package com.furflix.app.ui.components

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import android.graphics.Paint
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.furflix.app.R
import com.furflix.app.data.remote.FurAffinityScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * GIF 全屏逐帧播放器：android.graphics.Movie 驱动（各 API 版本行为一致），
 * 支持暂停/播放、上一帧/下一帧、进度条拖帧、按当前帧原始分辨率无损截屏入图库。
 * 字节经 FA 抓取层获取（含会话头），帧延时从 GIF 块结构解析（帧序 = GCE 顺序）。
 */
@Composable
fun GifFrameViewer(
    imageUrl: String,
    submissionId: String,
    onClose: () -> Unit
) {
    BackHandler { onClose() }
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(imageUrl) {
        withContext(Dispatchers.IO) {
            val raw = FurAffinityScraper.fetchBinary(imageUrl)
            if (raw == null) failed = true else bytes = raw
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val data = bytes
        when {
            data != null -> {
                val movie = remember(data) {
                    runCatching { Movie.decodeByteArray(data, 0, data.size) }.getOrNull()
                }
                if (movie != null && movie.width() > 0) {
                    GifPlayer(movie = movie, bytes = data, submissionId = submissionId)
                } else {
                    LoadFailedHint(modifier = Modifier.align(Alignment.Center))
                }
            }
            failed -> LoadFailedHint(modifier = Modifier.align(Alignment.Center))
            else -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(40.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = 12.dp, vertical = 24.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.cd_close),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun LoadFailedHint(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.gif_load_failed),
        color = Color.White.copy(alpha = 0.7f),
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
    )
}

@Composable
private fun GifPlayer(
    movie: Movie,
    bytes: ByteArray,
    submissionId: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val delays = remember(bytes) { parseFrameDelaysMs(bytes) }
    val frameCount = delays.size.coerceAtLeast(1)
    // 帧边界时间（ms）：setTime 落在 [start, nextStart) 区间即显示该帧
    val frameStartsMs = remember(delays) {
        val starts = IntArray(delays.size)
        var acc = 0
        for (i in delays.indices) {
            starts[i] = acc
            acc += delays[i]
        }
        starts
    }

    var frameIndex by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    // version 只作重绘驱动：Canvas 块读它，帧一变即重画 Movie 当前帧
    var drawVersion by remember { mutableIntStateOf(0) }
    val paint = remember { Paint() }

    fun showFrame(index: Int) {
        if (frameCount <= 0) return
        val clamped = index.coerceIn(0, frameCount - 1)
        frameIndex = clamped
        movie.setTime(frameStartsMs[clamped])
        drawVersion++
    }

    // 首帧定位（Movie 默认停在 setTime(0) 的帧上）
    LaunchedEffect(movie) { showFrame(0) }

    // 播放循环：按当前帧延时推进，尾帧回环
    LaunchedEffect(playing, frameCount) {
        if (!playing || frameCount <= 1) return@LaunchedEffect
        while (true) {
            delay(delays[frameIndex].coerceIn(20, 10_000).toLong())
            showFrame((frameIndex + 1) % frameCount)
        }
    }

    // 缩放/平移/双击 —— 与 ZoomableImageViewer 同一套手感
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scope.launch {
                            if (scale > 1.5f) {
                                val startScale = scale
                                val startOffset = offset
                                animate(0f, 1f, animationSpec = tween(250, easing = FastOutSlowInEasing)) { v, _ ->
                                    scale = startScale + (1f - startScale) * v
                                    offset = startOffset * (1f - v)
                                }
                                offset = Offset.Zero
                            } else {
                                val startScale = scale
                                animate(0f, 1f, animationSpec = tween(250, easing = FastOutSlowInEasing)) { v, _ ->
                                    scale = startScale + (2.5f - startScale) * v
                                }
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 6f)
                    scale = newScale
                    offset = if (newScale > 1f) offset + pan else Offset.Zero
                }
            }
    ) {
        // 原始分辨率逐帧渲染（等比 fit 全屏，graphicsLayer 承担缩放）
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        ) {
            if (drawVersion >= 0) {
                val nativeCanvas = drawContext.canvas.nativeCanvas
                val dx = (size.width - movie.width()) / 2f
                val dy = (size.height - movie.height()) / 2f
                nativeCanvas.save()
                nativeCanvas.translate(dx, dy)
                movie.draw(nativeCanvas, 0f, 0f, paint)
                nativeCanvas.restore()
            }
        }

        // 底部控制条：进度条拖帧 + 上一帧/播放暂停/下一帧/截屏
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Slider(
                value = frameIndex.toFloat(),
                onValueChange = { showFrame(it.toInt()) },
                valueRange = 0f..(frameCount - 1).coerceAtLeast(1).toFloat(),
                steps = (frameCount - 2).coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.gif_frame_position, frameIndex + 1, frameCount),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showFrame(frameIndex - 1) }) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.gif_prev_frame),
                        tint = Color.White
                    )
                }
                IconButton(onClick = { playing = !playing }) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (playing) R.string.gif_pause else R.string.gif_play),
                        tint = Color.White
                    )
                }
                IconButton(onClick = { showFrame(frameIndex + 1) }) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.gif_next_frame),
                        tint = Color.White
                    )
                }
                IconButton(onClick = {
                    // Movie 非线程安全：帧捕获必须留在主线程，只有落盘走 IO
                    scope.launch {
                        val bitmap = captureFrameBitmap(movie, paint)
                        saveFrameToGallery(context, bitmap, submissionId, frameIndex)
                    }
                }) {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = stringResource(R.string.gif_screenshot),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

/** 把当前帧按 Movie 原始分辨率画进新位图（主线程调用；PNG 落盘在 saveFrameToGallery）。 */
private fun captureFrameBitmap(movie: Movie, paint: Paint): Bitmap {
    val width = movie.width().coerceAtLeast(1)
    val height = movie.height().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.BLACK)
    movie.draw(canvas, 0f, 0f, paint)
    return bitmap
}

/** 位图写入系统图库（PNG 无损）：Q+ 走 MediaStore，老版本直写 Pictures/FurFlix + 扫描。 */
private suspend fun saveFrameToGallery(
    context: Context,
    bitmap: Bitmap,
    submissionId: String,
    frameIndex: Int
) {
    try {
        val name = "FurFlix_${submissionId}_frame${frameIndex + 1}.png"
        val savedName = withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/FurFlix")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: throw Exception("MediaStore insert failed")
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                } ?: throw Exception("Output stream unavailable")
                name
            } else {
                @Suppress("DEPRECATION")
                val dir = java.io.File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "FurFlix"
                )
                dir.mkdirs()
                val file = java.io.File(dir, name)
                java.io.FileOutputStream(file).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
                name
            }
        }
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.toast_frame_saved, savedName),
            android.widget.Toast.LENGTH_SHORT
        ).show()
    } catch (e: Exception) {
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.toast_frame_save_failed, e.message ?: ""),
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}

/**
 * 从 GIF 字节流解析每帧延时（Graphic Control Extension 的 delay，单位 10ms）。
 * 帧序 = GCE 出现顺序；异常帧按 100ms 处理（与浏览器空延时默认一致）。
 */
private fun parseFrameDelaysMs(bytes: ByteArray): List<Int> {
    val delays = mutableListOf<Int>()
    fun u16(o: Int): Int = if (o + 1 < bytes.size) {
        (bytes[o].toInt() and 0xFF) or ((bytes[o + 1].toInt() and 0xFF) shl 8)
    } else 0

    try {
        var i = 6 + 7 // header(6) + logical screen descriptor(7)
        if (i >= bytes.size) return delays
        if ((bytes[10].toInt() and 0x80) != 0) {
            // 全局色表：3 字节/项，项数 = 2^(n+1)
            i += 3 * (1 shl ((bytes[10].toInt() and 0x07) + 1))
        }
        while (i < bytes.size) {
            when (bytes[i].toInt() and 0xFF) {
                0x21 -> { // 扩展块
                    val label = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else -1
                    if (label == 0xF9 && i + 6 < bytes.size) {
                        val delay = u16(i + 4) * 10
                        delays.add(if (delay <= 0) 100 else delay)
                    }
                    i += 2
                    while (i < bytes.size && bytes[i].toInt() != 0) {
                        i += 1 + (bytes[i].toInt() and 0xFF)
                    }
                    i += 1
                }
                0x2C -> { // 图像描述符：9 字节定长 + 可选局部色表 + LZW 子块链
                    i += 1 + 8
                    if (i >= bytes.size) break
                    if ((bytes[i].toInt() and 0x80) != 0) {
                        i += 3 * (1 shl ((bytes[i].toInt() and 0x07) + 1))
                    }
                    i += 1 // LZW 最小编码长度
                    while (i < bytes.size && bytes[i].toInt() != 0) {
                        i += 1 + (bytes[i].toInt() and 0xFF)
                    }
                    i += 1
                }
                0x3B -> break // trailer
                else -> break // 结构异常：以已解析的为准
            }
        }
    } catch (_: Exception) {
        // 越界等异常：返回已解析部分
    }
    return delays
}
