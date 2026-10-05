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

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36"
    private const val REFERER = "https://www.furaffinity.net/"
    private val KNOWN_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

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
            if (workUrl.isEmpty()) {
                val sub = FurRepository.getInstance(ctx).getSubmission(task.submissionId)
                    ?: throw Exception("Detail unavailable")
                workUrl = sub.fullImageUrl
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

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(workUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .build()

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

                val folderPref = FurRepository.getInstance(ctx).downloadFolderFlow.first()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(ext))
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePathQ(folderPref))
                    }
                    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                        ?: throw Exception("MediaStore insert failed")
                    insertedUri = uri
                    ctx.contentResolver.openOutputStream(uri)?.use { os ->
                        body.byteStream().use { input ->
                            input.copyTo(os)
                        }
                    } ?: throw Exception("Output stream unavailable")
                } else {
                    @Suppress("DEPRECATION")
                    val baseDir = when {
                        folderPref.startsWith("Downloads") -> Environment.DIRECTORY_DOWNLOADS
                        folderPref.startsWith("DCIM") -> Environment.DIRECTORY_DCIM
                        else -> Environment.DIRECTORY_PICTURES
                    }
                    val subName = if (folderPref.contains("/")) folderPref.substringAfter("/") else "FurFlix"
                    val dir = File(Environment.getExternalStoragePublicDirectory(baseDir), subName)
                    dir.mkdirs()
                    val file = File(dir, finalName)
                    FileOutputStream(file).use { fos ->
                        body.byteStream().use { input ->
                            input.copyTo(fos)
                        }
                    }
                    android.media.MediaScannerConnection.scanFile(ctx, arrayOf(file.absolutePath), arrayOf(mimeFor(ext)), null)
                }

                writeMeta(ctx, task.submissionId, author, title)
                update(task.id) { it.copy(status = DownloadStatus.COMPLETED, speedBps = 0) }
                toast(ctx, ctx.getString(R.string.toast_download_saved, finalName))
                _refreshSignal.emit(Unit)
            }
        } catch (e: Exception) {
            // 失败清理：避免留下 MediaStore 空壳条目
            insertedUri?.let { uri ->
                runCatching { ctx.contentResolver.delete(uri, null, null) }
            }
            update(task.id) { it.copy(status = DownloadStatus.FAILED, speedBps = 0, error = e.message ?: "Unknown") }
            toast(ctx, ctx.getString(R.string.toast_download_failed, e.message ?: ""))
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
            ?.takeIf { it.substringBefore('/') == "image" }
            ?.substringAfter('/')?.substringBefore(';')?.lowercase()
        return if (fromType in KNOWN_EXTS) fromType!! else "jpg"
    }

    private fun mimeFor(ext: String): String = when (ext) {
        "gif" -> "image/gif"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        else -> "image/jpeg"
    }

    private fun relativePathQ(folderPref: String): String =
        if (folderPref.isNotEmpty()) {
            val parts = folderPref.split("/")
            if (parts.size > 1) parts[0] + "/" + parts.drop(1).joinToString("/")
            else Environment.DIRECTORY_PICTURES + "/" + folderPref
        } else {
            Environment.DIRECTORY_PICTURES + "/FurFlix"
        }

    private fun alreadyDownloaded(ctx: Context, fileName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ctx.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(fileName),
                null
            )?.use { it.count > 0 } ?: false
        } else {
            @Suppress("DEPRECATION")
            val pictures = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "FurFlix")
            File(pictures, fileName).exists()
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
