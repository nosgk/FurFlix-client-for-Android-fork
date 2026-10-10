package com.furflix.app.data.download

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.widget.Toast
import com.furflix.app.R
import com.furflix.app.data.repository.FurRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

enum class DownloadStatus { QUEUED, RUNNING, COMPLETED, FAILED }

/** DIRECT = 已知 URL 直下；PARSE_THEN_DOWNLOAD = 先解析详情页取原图（批量下载）。 */
enum class DownloadKind { DIRECT, PARSE_THEN_DOWNLOAD }

data class DownloadTask(
    val id: Long,
    val submissionId: String,
    val kind: DownloadKind = DownloadKind.DIRECT,
    val url: String,
    val fileName: String,
    val author: String,
    val title: String,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val bytes: Long = 0,
    val totalBytes: Long = -1L,
    val speedBps: Long = 0,
    val error: String? = null
)

/**
 * 进程内下载队列：串行消费，逐任务上报进度/速度/状态。
 * 收敛原 MainViewModel.downloadImage 与 DownloadHelper.downloadImage 两套实现；
 * 完成事件经 [refreshSignal] 通知下载页重查 MediaStore。
 */
object DownloadManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channel = Channel<DownloadTask>(Channel.UNLIMITED)
    private val idCounter = AtomicLong(1)

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val _refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val refreshSignal: SharedFlow<Unit> = _refreshSignal.asSharedFlow()

    private var appContext: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private const val REFERER = "https://www.furaffinity.net/"
    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    // FA 音频作品（Music/Podcast 类）与文档类：MediaStore 需按类型分流，不能全按图片落库
    private val AUDIO_EXTS = setOf("mp3", "m4a", "ogg", "wav", "flac", "aac", "mid", "midi")
    private val DOC_EXTS = setOf("txt", "pdf", "doc", "docx", "rtf", "swf")
    private val KNOWN_EXTS = IMAGE_EXTS + AUDIO_EXTS + DOC_EXTS

    /** 扩展名 → MediaStore 集合：Q+ 按类型插入 Images/Audio/Downloads。 */
    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
    private fun mediaCollectionFor(ext: String): android.net.Uri = when {
        ext in AUDIO_EXTS -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        ext in IMAGE_EXTS -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
    }

    /** 扩展名 → 落盘子目录（pre-Q 与 RELATIVE_PATH 首段共用）。 */
    private fun envDirFor(ext: String): String = when {
        ext in AUDIO_EXTS -> Environment.DIRECTORY_MUSIC
        ext in IMAGE_EXTS -> Environment.DIRECTORY_PICTURES
        else -> Environment.DIRECTORY_DOWNLOADS
    }

    init {
        scope.launch {
            for (task in channel) {
                run(task)
            }
        }
    }

    /** 入队一次下载；同名任务在队列中或已存在磁盘时按幂等处理。 */
    fun enqueue(context: Context, submissionId: String, url: String, author: String, title: String) {
        if (url.isEmpty()) return
        appContext = context.applicationContext
        val fileName = buildFileName(title, submissionId, url)
        val pending = _tasks.value.any {
            it.fileName == fileName &&
                (it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.RUNNING)
        }
        if (pending) return

        val task = DownloadTask(
            id = idCounter.getAndIncrement(),
            submissionId = submissionId,
            url = url,
            fileName = fileName,
            author = author,
            title = title
        )
        _tasks.update { it + task }
        scope.launch {
            val ctx = appContext ?: return@launch
            if (alreadyDownloaded(ctx, fileName)) {
                update(task.id) { it.copy(status = DownloadStatus.COMPLETED) }
                toast(ctx, ctx.getString(R.string.downloads_already_exists))
                _refreshSignal.emit(Unit)
            } else {
                channel.send(task)
            }
        }
    }

    /**
     * 批量下载入口：只带 submissionId，执行时先解析详情页拿原图
     * （命中 MainViewModel 的详情缓存则零解析成本——缓存由预取/浏览自然回填）。
     */
    fun enqueueDownload(context: Context, submissionId: String) {
        if (submissionId.isBlank()) return
        appContext = context.applicationContext
        val pending = _tasks.value.any {
            it.submissionId == submissionId &&
                (it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.RUNNING)
        }
        if (pending) return
        val task = DownloadTask(
            id = idCounter.getAndIncrement(),
            submissionId = submissionId,
            kind = DownloadKind.PARSE_THEN_DOWNLOAD,
            url = "",
            fileName = "submission_$submissionId",
            author = "",
            title = ""
        )
        _tasks.update { it + task }
        scope.launch { channel.send(task) }
    }

    /** 重试一个失败任务（沿用原 id；详情页按 submissionId 追踪仍能命中）。 */
    fun retry(taskId: Long) {
        val task = _tasks.value.firstOrNull { it.id == taskId && it.status == DownloadStatus.FAILED } ?: return
        update(taskId) {
            it.copy(status = DownloadStatus.QUEUED, bytes = 0, totalBytes = -1L, speedBps = 0, error = null)
        }
        scope.launch { channel.send(task.copy(status = DownloadStatus.QUEUED)) }
    }

    private suspend fun run(task: DownloadTask) {
        val ctx = appContext ?: return
        update(task.id) { it.copy(status = DownloadStatus.RUNNING) }
        var insertedUri: android.net.Uri? = null
        try {
            // PARSE_THEN_DOWNLOAD：先解析详情页取原图与元数据
            var workUrl = task.url
            var author = task.author
            var title = task.title
            val method = FurRepository.getInstance(ctx).downloadMethodFlow.first()
            if (workUrl.isEmpty()) {
                val sub = FurRepository.getInstance(ctx).getSubmission(task.submissionId)
                    ?: throw Exception("Detail unavailable")
                // 下载方式分流：original = d.furaffinity.net 原件（无下载锚点回落展示文件）；
                // copy = 详情页正式载入的展示文件
                workUrl = if (method == FurRepository.DOWNLOAD_METHOD_ORIGINAL) {
                    sub.fileUrl.ifEmpty { sub.fullImageUrl }
                } else {
                    sub.fullImageUrl
                }
                author = sub.author
                title = sub.title
                if (workUrl.isEmpty()) throw Exception("No full image URL")
                update(task.id) {
                    it.copy(
                        url = workUrl,
                        author = author,
                        title = title,
                        fileName = buildFileName(title, task.submissionId, workUrl)
                    )
                }
            }
            val folderPref = FurRepository.getInstance(ctx).downloadFolderFlow.first()

            // 复制模式零网络快速路径：详情页已把同 URL 文件载入 Coil 磁盘缓存，
            // 命中即直接复制字节落盘（与屏幕所见逐字节一致）；未命中（批量下载/
            // 缓存被清）回落下方网络流。原图模式不查缓存。
            if (method == FurRepository.DOWNLOAD_METHOD_COPY) {
                val cachedBytes = readImageCacheBytes(ctx, workUrl)
                if (cachedBytes != null && cachedBytes.isNotEmpty()) {
                    val ext = resolveExtension(workUrl, null)
                    val baseName = if (task.kind == DownloadKind.PARSE_THEN_DOWNLOAD) {
                        buildFileName(title, task.submissionId, workUrl)
                    } else {
                        task.fileName
                    }
                    val finalName = renameWithExtension(baseName, ext)
                    update(task.id) {
                        it.copy(
                            fileName = finalName,
                            bytes = cachedBytes.size.toLong(),
                            totalBytes = cachedBytes.size.toLong()
                        )
                    }
                    persistStream(ctx, ext, finalName, folderPref) { os -> os.write(cachedBytes) }
                    writeMeta(ctx, task.submissionId, author, title)
                    update(task.id) { it.copy(status = DownloadStatus.COMPLETED, speedBps = 0) }
                    toast(ctx, ctx.getString(R.string.toast_download_saved, finalName))
                    _refreshSignal.emit(Unit)
                    return
                }
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val requestBuilder = Request.Builder().url(workUrl)
                .header("User-Agent", com.furflix.app.data.remote.UserAgent.current())
                .header("Referer", REFERER)
                .header(
                    "Accept",
                    "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
                )
            // K-002:d.furaffinity.net 的原件直链对无会话请求返回 403。
            // 下载必须带走与抓取链路同源的会话/CF cookie,否则"下载原图"必失败。
            // 与 UserAgent 同源保证 cf_clearance 的 UA 绑定校验成立(ADR-0002)。
            val cookieHeader = com.furflix.app.data.remote.FurAffinityScraper.getCookiesString()
            if (cookieHeader.isNotEmpty()) {
                requestBuilder.header("Cookie", cookieHeader)
            }
            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                val rawBody = response.body ?: throw Exception("Empty response")

                val ext = resolveExtension(workUrl, rawBody.contentType()?.toString())
                val baseName = if (task.kind == DownloadKind.PARSE_THEN_DOWNLOAD) {
                    buildFileName(title, task.submissionId, workUrl)
                } else {
                    task.fileName
                }
                val finalName = renameWithExtension(baseName, ext)
                val contentLength = rawBody.contentLength()
                update(task.id) {
                    it.copy(
                        fileName = finalName,
                        totalBytes = if (contentLength > 0) contentLength else -1L
                    )
                }

                // 回调按 120ms 节流 + 600ms 滑窗测速，避免高频 read 触发过多重组
                val speedometer = Speedometer()
                var lastFlush = 0L
                val body = ProgressResponseBody(rawBody) { totalRead, length ->
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastFlush >= 120) {
                        lastFlush = now
                        val speed = speedometer.sample(totalRead, now)
                        update(task.id) { t ->
                            t.copy(
                                bytes = totalRead,
                                totalBytes = if (length > 0) length else -1L,
                                speedBps = speed ?: t.speedBps
                            )
                        }
                    }
                }

                insertedUri = persistStream(ctx, ext, finalName, folderPref) { os ->
                    body.byteStream().use { input ->
                        input.copyTo(os)
                    }
                }

                writeMeta(ctx, task.submissionId, author, title)
                update(task.id) { it.copy(status = DownloadStatus.COMPLETED, speedBps = 0) }
                toast(ctx, ctx.getString(R.string.toast_download_saved, finalName))
                _refreshSignal.emit(Unit)
            }
        } catch (e: Exception) {
            // 失败清理：避免留下 MediaStore 空壳条目（persistStream 自身已兜一层）
            insertedUri?.let { uri ->
                runCatching { ctx.contentResolver.delete(uri, null, null) }
            }
            update(task.id) { it.copy(status = DownloadStatus.FAILED, speedBps = 0, error = e.message ?: "Unknown") }
            toast(ctx, ctx.getString(R.string.toast_download_failed, e.message ?: ""))
        }
    }

    /**
     * 复制模式专用：读 Coil 磁盘缓存里同 URL 的字节（详情页正式载入过即命中）。
     * 缓存实现细节任何一步异常都视为未命中，回落网络流。
     */
    private fun readImageCacheBytes(ctx: Context, url: String): ByteArray? = runCatching {
        com.furflix.app.ui.components.ImageLoaderFactory.getInstance(ctx)
            .diskCache?.openSnapshot(url)?.let { snapshot ->
                try {
                    okio.FileSystem.SYSTEM.source(snapshot.data).buffer().use { it.readByteArray() }
                } finally {
                    runCatching { snapshot.close() }
                }
            }
    }.getOrNull()

    /**
     * 落盘：Q+ 按扩展名插入 MediaStore 对应集合后写入；pre-Q 写公共目录的
     * FurFlix 子目录并触发媒体扫描。写入失败时撤回已插入的 MediaStore 条目，
     * 返回插入的 Uri（pre-Q 与文件路径无 Uri 返回 null）。
     */
    private inline fun persistStream(
        ctx: Context,
        ext: String,
        finalName: String,
        folderPref: String,
        writer: (java.io.OutputStream) -> Unit
    ): android.net.Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(ext))
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePathQ(folderPref, ext))
            }
            val uri = ctx.contentResolver.insert(mediaCollectionFor(ext), contentValues)
                ?: throw Exception("MediaStore insert failed")
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { os -> writer(os) }
                    ?: throw Exception("Output stream unavailable")
            } catch (e: Exception) {
                runCatching { ctx.contentResolver.delete(uri, null, null) }
                throw e
            }
            return uri
        } else {
            @Suppress("DEPRECATION")
            val baseDir = when {
                folderPref.startsWith("Downloads") -> Environment.DIRECTORY_DOWNLOADS
                folderPref.startsWith("DCIM") -> Environment.DIRECTORY_DCIM
                else -> envDirFor(ext)
            }
            val subName = if (folderPref.contains("/")) folderPref.substringAfter("/") else "FurFlix"
            val dir = File(Environment.getExternalStoragePublicDirectory(baseDir), subName)
            dir.mkdirs()
            val file = File(dir, finalName)
            FileOutputStream(file).use { fos -> writer(fos) }
            android.media.MediaScannerConnection.scanFile(ctx, arrayOf(file.absolutePath), arrayOf(mimeFor(ext)), null)
            return null
        }
    }

    private fun update(taskId: Long, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it.id == taskId) transform(it) else it } }
    }

    private fun toast(context: Context, message: String) {
        mainHandler.post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }

    private fun buildFileName(title: String, submissionId: String, url: String): String {
        val safeTitle = title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(60).ifEmpty { "submission" }
        val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase()
            .takeIf { it in KNOWN_EXTS } ?: "jpg"
        return "${safeTitle}_$submissionId.$ext"
    }

    /** URL 无可辨识后缀时按响应类型回填扩展名；否则信任入队时的推断。 */
    private fun renameWithExtension(fileName: String, ext: String): String {
        val currentExt = fileName.substringAfterLast('.', "").lowercase()
        return if (currentExt == ext || (currentExt in KNOWN_EXTS && currentExt != "jpg")) {
            fileName
        } else {
            "${fileName.substringBeforeLast('.')}.$ext"
        }
    }

    private fun resolveExtension(url: String, contentType: String?): String {
        val fromUrl = url.substringBefore('?').substringAfterLast('.', "").lowercase()
        if (fromUrl in KNOWN_EXTS) return fromUrl
        val fromType = contentType
            ?.takeIf { it.substringBefore('/') == "image" || it.substringBefore('/') == "audio" || it.substringBefore('/') == "text" || it.substringBefore('/') == "application" }
            ?.substringAfter('/')?.substringBefore(';')?.lowercase()
        return if (fromType in KNOWN_EXTS) fromType!! else "jpg"
    }

    private fun mimeFor(ext: String): String = when (ext) {
        "gif" -> "image/gif"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "aac" -> "audio/aac"
        "mid", "midi" -> "audio/midi"
        "txt" -> "text/plain"
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "rtf" -> "application/rtf"
        "swf" -> "application/x-shockwave-flash"
        else -> "image/jpeg"
    }

    private fun relativePathQ(folderPref: String, ext: String): String {
        val root = envDirFor(ext)
        return if (folderPref.isNotEmpty()) {
            val parts = folderPref.split("/")
            if (parts.size > 1) parts[0] + "/" + parts.drop(1).joinToString("/")
            else root + "/" + folderPref
        } else {
            root + "/FurFlix"
        }
    }

    private fun alreadyDownloaded(ctx: Context, fileName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 下载按扩展名分流到 Images/Audio/Downloads 三个集合，查重须覆盖全部
            val collections = listOf(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            )
            collections.any { uri ->
                ctx.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                    arrayOf(fileName),
                    null
                )?.use { it.count > 0 } ?: false
            }
        } else {
            // pre-Q：按扩展名落在对应公共目录的 FurFlix 子目录
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val dir = File(Environment.getExternalStoragePublicDirectory(envDirFor(ext)), "FurFlix")
            File(dir, fileName).exists()
        }
    } catch (_: Exception) {
        false
    }

    private fun writeMeta(ctx: Context, submissionId: String, author: String, title: String) {
        val metadata = JSONObject().apply {
            put("author", author)
            put("title", title)
            put("postId", submissionId)
        }.toString()
        ctx.getSharedPreferences("downloads_meta", Context.MODE_PRIVATE)
            .edit().putString(submissionId, metadata).apply()
    }
}
