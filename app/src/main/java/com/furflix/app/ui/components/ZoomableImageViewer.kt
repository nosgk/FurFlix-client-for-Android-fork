package com.furflix.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.furflix.app.R
import com.furflix.app.data.repository.FurRepository
import com.furflix.app.ui.theme.DarkSurfaceVariant
import com.furflix.app.utils.BitmapResampler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 全屏图像查看器：捏合缩放 / 拖动 / 双击，**缩放不设上限**（[MAX_ZOOM]），
 * 并按设置里的插值算法对**当前可见区域**做真实重采样。
 *
 * 为什么是"可见区域 LOD"而不是整图重采样：
 * - 整图按 32× 重采样会产生 GB 级位图，必然 OOM；
 * - 可见区域永远只有一屏大小，重采样代价恒定（与缩放倍率无关），
 *   于是"无限放大"在内存与耗时上都是可控的。
 *
 * 绘制分两层：底层始终用 GPU 把原图按当前变换画一遍（永不出现空洞），
 * 上层在重采样完成后覆盖一块按 [interpolation] 卷积出来的清晰位图。
 * 手势期间上层是旧帧（被拉伸，略糊），停手约 140ms 后重算——这是
 * "跟手优先、静止后变清晰"的取舍，也是各家看图应用的通例。
 */
@Composable
fun ZoomableImageViewer(
    imageUrl: String,
    onClose: () -> Unit,
    /** 已下载作品的本地副本：非空时直接读它，不再联网（见 DownloadedMediaRegistry） */
    localSource: android.net.Uri? = null
) {
    BackHandler { onClose() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 全屏缩放查看器吃详情页画质档位：原图档下放大不糊
    val detailQuality = LocalDetailImageQuality.current
    val interpolation = LocalImageInterpolation.current
    val fidelity = LocalImageFidelity.current

    var zoom by remember(imageUrl) { mutableFloatStateOf(1f) }
    var offset by remember(imageUrl) { mutableStateOf(Offset.Zero) }
    var viewSize by remember(imageUrl) { mutableStateOf(IntSize.Zero) }
    // 解码后的原图（LOD 重采样的数据源）
    var source by remember(imageUrl) { mutableStateOf<ImageBitmap?>(null) }
    // 清晰层：按算法重采样出的可见区域位图 + 它在源图坐标里的矩形
    var tile by remember(imageUrl) { mutableStateOf<ImageBitmap?>(null) }
    var tileRect by remember(imageUrl) { mutableStateOf<Rect?>(null) }

    val retryState = rememberRetryImageState(imageUrl)
    RetryEffect(retryState)
    // GPU 采样档位：真实算法（双三次/兰索斯）在 CPU 层，GPU 一律走双线性
    val gpuFilter = imageFilterQuality(interpolation)

    // 可见区域重采样：手势停稳后重算（LaunchedEffect 的 key 变化即取消上一次）
    LaunchedEffect(source, zoom, offset, viewSize, interpolation) {
        val src = source ?: return@LaunchedEffect
        if (!needsCpuResample(interpolation)) {
            tile = null
            tileRect = null
            return@LaunchedEffect
        }
        if (viewSize == IntSize.Zero) return@LaunchedEffect
        // 防抖：捏合/拖动过程中不断变化，停手后才真正算
        delay(140)
        val result = withContext(Dispatchers.Default) {
            buildVisibleTile(src, viewSize, zoom, offset, interpolation)
        }
        if (result != null) {
            tile = result.first
            tileRect = result.second
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scope.launch {
                            if (zoom > 1.5f) {
                                val startZoom = zoom
                                val startOffset = offset
                                animate(0f, 1f, animationSpec = tween(250, easing = FastOutSlowInEasing)) { v, _ ->
                                    zoom = startZoom + (1f - startZoom) * v
                                    offset = startOffset * (1f - v)
                                }
                                offset = Offset.Zero
                            } else {
                                val startZoom = zoom
                                animate(0f, 1f, animationSpec = tween(250, easing = FastOutSlowInEasing)) { v, _ ->
                                    zoom = startZoom + (2.5f - startZoom) * v
                                }
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoomChange, _ ->
                    val nextZoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
                    zoom = nextZoom
                    offset = clampOffset(
                        if (nextZoom > 1f) offset + pan else Offset.Zero,
                        nextZoom, viewSize, source
                    )
                }
            }
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(localSource ?: retryState.effectiveUrl)
                .apply {
                    when {
                        detailQuality == FurRepository.DETAIL_QUALITY_ORIGINAL ->
                            size(coil3.size.Size.ORIGINAL)
                        detailQuality != FurRepository.DETAIL_QUALITY_AUTO ->
                            size(coil3.size.Size(detailQuality, detailQuality))
                        else -> Unit
                    }
                }
                .applyImageQuality(fidelity)
                // CPU 重采样要读回像素，硬件位图读不回（getPixels 直接抛异常），
                // 所以这个查看器的解码一律走可读的软件位图
                .allowHardware(false)
                .build(),
            imageLoader = ImageLoaderFactory.getInstance(context),
            contentDescription = stringResource(R.string.cd_artwork),
            contentScale = ContentScale.Fit,
            filterQuality = gpuFilter,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { retryState.onSuccess() },
            onError = { errorState -> retryState.onError(errorState.result.throwable) },
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize().background(DarkSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    LoadProgressChip(url = imageUrl, modifier = Modifier)
                }
            },
            error = {
                if (retryState.exhausted) {
                    DefaultImageErrorContent(
                        errorText = describeError(retryState.lastError),
                        onRetry = { retryState.retry() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(DarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadProgressChip(url = imageUrl, modifier = Modifier)
                    }
                }
            },
            success = success@{ state ->
                // 拿可读的位图给 LOD 重采样用：走 Coil 官方的 Image→Bitmap 转换
                // （BitmapPainter.image 在该 Compose 版本里是 private，取不到）
                val bitmap: ImageBitmap? = runCatching {
                    state.result.image.toBitmap().asImageBitmap()
                }.getOrNull()
                if (bitmap == null) {
                    // 转换失败（如解码结果不是位图）：退回 Coil 自己的绘制，功能不受影响
                    SubcomposeAsyncImageContent()
                    return@success
                }
                LaunchedEffect(bitmap) { source = bitmap }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val iw = bitmap.width.toFloat()
                    val ih = bitmap.height.toFloat()
                    if (iw <= 0f || ih <= 0f) return@Canvas
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val base = min(size.width / iw, size.height / ih)
                    val s = base * zoom
                    val imgLeft = (size.width - iw) / 2f
                    val imgTop = (size.height - ih) / 2f

                    fun screenX(px: Float) = cx + (imgLeft + px - cx) * s + offset.x
                    fun screenY(py: Float) = cy + (imgTop + py - cy) * s + offset.y

                    // 底层：GPU 直接缩放整图（保证任何时刻都有画面）
                    withTransform({
                        translate(offset.x, offset.y)
                        scale(s, s, pivot = Offset(cx, cy))
                    }) {
                        drawImage(
                            image = bitmap,
                            dstOffset = IntOffset(imgLeft.roundToInt(), imgTop.roundToInt()),
                            dstSize = IntSize(iw.roundToInt(), ih.roundToInt()),
                            filterQuality = gpuFilter
                        )
                    }

                    // 上层：按所选插值算法重采样出的清晰区域
                    val t = tile
                    val r = tileRect
                    if (t != null && r != null) {
                        val left = screenX(r.left)
                        val top = screenY(r.top)
                        drawImage(
                            image = t,
                            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                            dstSize = IntSize(
                                (r.width * s).roundToInt().coerceAtLeast(1),
                                (r.height * s).roundToInt().coerceAtLeast(1)
                            ),
                            filterQuality = gpuFilter
                        )
                    }
                }
            }
        )

        // 缩放倍率指示：仅在放大时显示，让"还能继续放大"这件事可见
        if (zoom > 1.02f) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = 14.dp, vertical = 28.dp)
            ) {
                Text(
                    text = "×" + String.format(java.util.Locale.ROOT, "%.1f", zoom),
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        // Close button
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

/** 缩放上限：远超源图分辨率仍可继续放大（细节由所选插值算法补足）。 */
private const val MAX_ZOOM = 64f

/**
 * 把位移钳到"图片边缘不进入视口内"的范围：图片在该缩放下比视口小的一轴
 * 保持居中（位移归零），比视口大的一轴允许在富余量内自由拖动。
 */
private fun clampOffset(
    offset: Offset,
    zoom: Float,
    viewSize: IntSize,
    source: ImageBitmap?
): Offset {
    val src = source ?: return offset
    if (viewSize == IntSize.Zero) return offset
    val iw = src.width.toFloat()
    val ih = src.height.toFloat()
    if (iw <= 0f || ih <= 0f) return offset
    val base = min(viewSize.width / iw, viewSize.height / ih)
    val s = base * zoom
    val maxX = ((iw * s - viewSize.width) / 2f).coerceAtLeast(0f)
    val maxY = ((ih * s - viewSize.height) / 2f).coerceAtLeast(0f)
    return Offset(
        offset.x.coerceIn(-maxX, maxX),
        offset.y.coerceIn(-maxY, maxY)
    )
}

/**
 * 重采样当前可见区域：算出视口在源图坐标里覆盖的矩形，按 [algo] 卷积到
 * 屏幕像素尺寸。返回（清晰位图，源图坐标矩形）；参数退化时返回 null。
 */
private fun buildVisibleTile(
    source: ImageBitmap,
    viewSize: IntSize,
    zoom: Float,
    offset: Offset,
    algo: Int
): Pair<ImageBitmap, Rect>? {
    val bmp = source.asAndroidBitmap()
    val iw = bmp.width
    val ih = bmp.height
    if (iw <= 0 || ih <= 0) return null
    val base = min(viewSize.width.toFloat() / iw, viewSize.height.toFloat() / ih)
    val s = base * zoom
    if (s <= 0f) return null
    val cx = viewSize.width / 2f
    val cy = viewSize.height / 2f
    val icx = iw / 2f
    val icy = ih / 2f

    // 视口中心对应的源图坐标；视口半宽高除以 s 得源图半宽高
    val scx = icx - offset.x / s
    val scy = icy - offset.y / s
    val halfW = cx / s
    val halfH = cy / s
    val left = (scx - halfW).coerceIn(0f, iw.toFloat())
    val top = (scy - halfH).coerceIn(0f, ih.toFloat())
    val right = (scx + halfW).coerceIn(0f, iw.toFloat())
    val bottom = (scy + halfH).coerceIn(0f, ih.toFloat())
    val w = (right - left)
    val h = (bottom - top)
    if (w < 1f || h < 1f) return null

    val outW = (w * s).roundToInt().coerceIn(1, 4096)
    val outH = (h * s).roundToInt().coerceIn(1, 4096)
    val resampled = BitmapResampler.resample(
        src = bmp,
        srcLeft = left.toInt(),
        srcTop = top.toInt(),
        srcWidth = w.toInt().coerceAtLeast(1),
        srcHeight = h.toInt().coerceAtLeast(1),
        outWidth = outW,
        outHeight = outH,
        algo = algo
    ) ?: return null
    return resampled.asImageBitmap() to Rect(left, top, right, bottom)
}
