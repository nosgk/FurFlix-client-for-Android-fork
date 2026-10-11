// android.graphics.Movie 自 API 28 起标记弃用,但仍是框架内唯一支持按帧 setTime 的
// GIF 解码器;自研逐帧播放器(逐帧步进/暂停)有意依赖,整文件压制弃用告警。
@file:Suppress("DEPRECATION")

package com.furflix.app.ui.components

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Movie
import android.graphics.PorterDuff
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.furflix.app.R
import com.furflix.app.data.remote.FurAffinityScraper
import com.furflix.app.data.remote.GifFrameTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.buffer
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * GIF 全屏逐帧播放器：帧延时由 [GifFrameTable] 从 GIF 块结构解析，帧画面由
 * android.graphics.Movie 按帧时间画进一张**软件位图**（ARGB_8888），Compose 侧
 * drawImage 等比 fit 显示——不在硬件画布上直绘 Movie，规避设备兼容性差异。
 * 支持暂停/播放、上一帧/下一帧、进度条拖帧、按原始分辨率无损截屏入图库。
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
    var attempt by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    LaunchedEffect(imageUrl, attempt) {
        failed = false
        withContext(Dispatchers.IO) {
            // 详情页主图（同 URL）已由 Coil 拉过：优先复用其磁盘缓存字节，
            // 命中则零网络请求直接进逐帧播放器；未命中（被清缓存/直开）再下载
            val cached = runCatching {
                ImageLoaderFactory.getInstance(context).diskCache?.openSnapshot(imageUrl)
            }.getOrNull()?.let { snapshot ->
                try {
                    okio.FileSystem.SYSTEM.source(snapshot.data).buffer()
                        .use { src -> src.readByteArray() }
                } catch (_: Exception) {
                    null
                } finally {
                    runCatching { snapshot.close() }
                }
            }
            if (cached != null && cached.isNotEmpty()) {
                bytes = cached
            } else {
                val raw = FurAffinityScraper.fetchBinary(imageUrl)
                if (raw == null) failed = true else bytes = raw
            }
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
                    LoadFailedHint(onRetry = { attempt++ })
                }
            }
            failed -> LoadFailedHint(onRetry = { attempt++ })
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
private fun LoadFailedHint(modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.gif_load_failed),
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.downloads_retry), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun GifPlayer(
    movie: Movie,
    bytes: ByteArray,
    submissionId: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 插值档位必须在组合期读取（Canvas 的绘制 lambda 不是 @Composable，
    // 在里面读 CompositionLocal 是编译错误）
    val gifFilterQuality = imageFilterQuality(LocalImageInterpolation.current)
    val delays = remember(bytes) {
        GifFrameTable.parseFrameDelaysMs(bytes).ifEmpty { listOf(100) }
    }
    val frameCount = delays.size
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

    // 软件位图：Movie 逐帧画进 ARGB_8888，drawImage 只负责把当前位图贴上屏幕
    val bitmap = remember(movie) {
        runCatching {
            Bitmap.createBitmap(
                movie.width().coerceAtLeast(1),
                movie.height().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
        }.getOrNull()
    }

    var frameIndex by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    // drawVersion 只作重绘驱动：Canvas 块读它，帧一变即重贴位图当前内容
    var drawVersion by remember { mutableIntStateOf(0) }

    if (bitmap == null) {
        // 超大 GIF 位图分配失败（OOM 防线）
        Box(modifier = Modifier.fillMaxSize()) {
            LoadFailedHint(modifier = Modifier.align(Alignment.Center), onRetry = {})
        }
        return
    }
    val softCanvas = remember(bitmap) { android.graphics.Canvas(bitmap) }

    fun showFrame(index: Int) {
        val clamped = index.coerceIn(0, frameCount - 1)
        frameIndex = clamped
        movie.setTime(frameStartsMs[clamped])
        softCanvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        movie.draw(softCanvas, 0f, 0f)
        drawVersion++
    }

    // 首帧定位
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
    // 控制栏显隐：点画面空白处（未被控制栏消费的 tap）切换，沉浸式看帧
    var controlsVisible by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        // 单击空白处切换控制栏；控制栏自身的 clickable 会消费掉
                        // 落在它上面的 tap，不会冒泡到这里
                        controlsVisible = !controlsVisible
                    },
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
                    // 缩放上限与图像查看器对齐：可一路放大看清逐帧细节
                    val newScale = (scale * zoom).coerceIn(1f, 64f)
                    scale = newScale
                    offset = if (newScale > 1f) offset + pan else Offset.Zero
                }
            }
    ) {
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
            drawVersion // 读它触发重绘
            val iw = bitmap.width.toFloat()
            val ih = bitmap.height.toFloat()
            val fit = min(size.width / iw, size.height / ih)
            val dw = (iw * fit).roundToInt().coerceAtLeast(1)
            val dh = (ih * fit).roundToInt().coerceAtLeast(1)
            val dx = ((size.width - dw) / 2f).roundToInt()
            val dy = ((size.height - dh) / 2f).roundToInt()
            drawImage(
                bitmap.asImageBitmap(),
                dstOffset = IntOffset(dx, dy),
                dstSize = IntSize(dw, dh),
                // 放大采样跟随设置里的插值档位（GIF 逐帧重采样太贵，走 GPU 双线性）
                filterQuality = gifFilterQuality
            )
        }

        // 底部控制条：进度条拖帧 + 上一帧/播放暂停/下一帧/截屏
        // 点画面空白处可隐藏/唤回（controlsVisible），动画收起让位给画面
        androidx.compose.animation.AnimatedVisibility(
            visible = controlsVisible,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(160)) +
                androidx.compose.animation.slideInVertically(
                    androidx.compose.animation.core.tween(200)
                ) { it },
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140)) +
                androidx.compose.animation.slideOutVertically(
                    androidx.compose.animation.core.tween(180)
                ) { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
        Column(
            modifier = Modifier
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
                    // 位图就是当前帧的原始分辨率内容，直接拷贝落盘（主线程拷贝，IO 落盘）
                    scope.launch {
                        val copied = runCatching {
                            bitmap.copy(Bitmap.Config.ARGB_8888, false)
                        }.getOrNull()
                        if (copied != null) {
                            saveFrameToGallery(context, copied, submissionId, frameIndex)
                        }
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
