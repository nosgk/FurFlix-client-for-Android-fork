package com.furflix.app.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furflix.app.R
import com.furflix.app.data.local.DocFileCache
import com.furflix.app.data.repository.FurRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 全屏 PDF 查看器（自研文档查看器体系的 PDF 完整支持）：
 * DocFileCache 缓存优先（重开同一作品零下载）→ PdfRenderer 按视口分辨率渲染 →
 * 横向翻页 + 双击放大/缩小 + 放大后拖动/捏合（1x 时手势交给翻页）+
 * 底部页码滑杆跳页。页位图按字节数计费进 LruCache；离开时释放 renderer，
 * 磁盘缓存保留（碎片由 CacheJanitor 按 TTL/配额修剪）。
 */
@Composable
fun PdfViewerScreen(
    pdfUrl: String,
    title: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var pageCount by remember { mutableIntStateOf(0) }
    var attempt by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { pageCount })

    // 渲染目标宽 = 屏宽物理像素（钳制 720~1600），兼顾清晰度与内存
    val targetWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
        .toInt().coerceIn(720, 1600)

    // 页位图缓存：按字节计费（MB），~48MB ≈ 3-4 张屏宽页
    val pageCache = remember {
        object : LruCache<Int, ImageBitmap>(48) {
            override fun sizeOf(key: Int, value: ImageBitmap): Int =
                value.asAndroidBitmap().byteCount / (1024 * 1024)
        }
    }

    LaunchedEffect(pdfUrl, attempt) {
        loading = true
        failed = false
        val file = DocFileCache.obtain(context, pdfUrl, "pdf") {
            FurRepository.getInstance(context).fetchSubmissionFile(pdfUrl)
        }
        if (file == null) {
            loading = false
            failed = true
            return@LaunchedEffect
        }
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val r = PdfRenderer(pfd)
            renderer = r
            pageCount = r.pageCount
        } catch (_: Exception) {
            // 加密/损坏等：系统 PdfRenderer 打不开
            failed = true
        }
        loading = false
    }

    DisposableEffect(Unit) {
        onDispose {
            renderer?.let { runCatching { it.close() } }
            pageCache.evictAll()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when {
            loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            failed -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = stringResource(R.string.pdf_load_failed),
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.size(12.dp))
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.downloads_retry),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { attempt++ }
                    )
                }
            }
            else -> HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                PdfViewerPage(
                    renderer = renderer!!,
                    page = page,
                    targetWidthPx = targetWidthPx,
                    cache = pageCache
                )
            }
        }

        // 顶栏：返回 + 标题 + 页码
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = Color.White,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose)
                    .padding(8.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (pageCount > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${pagerState.currentPage + 1} / $pageCount",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }

        // 底栏：页码滑杆跳页
        if (pageCount > 1) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Slider(
                    value = pagerState.currentPage.toFloat(),
                    onValueChange = { value ->
                        val target = value.toInt().coerceIn(0, pageCount - 1)
                        if (target != pagerState.currentPage) {
                            scope.launch { pagerState.scrollToPage(target) }
                        }
                    },
                    valueRange = 0f..(pageCount - 1).toFloat(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 单页渲染：进入组合才渲染（PdfRenderer 非线程安全，串行加锁），位图进
 * [cache]；双击 1x↔2.5x，放大后拖动/捏合由手势接管（1x 时横向滑动仍归翻页）。
 */
@Composable
private fun PdfViewerPage(
    renderer: PdfRenderer,
    page: Int,
    targetWidthPx: Int,
    cache: LruCache<Int, ImageBitmap>
) {
    var bitmap by remember(page) { mutableStateOf(cache.get(page)) }
    var renderFailed by remember(page) { mutableStateOf(false) }
    // 缩放/位移随页重置
    var scale by remember(page) { mutableFloatStateOf(1f) }
    var offset by remember(page) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(page, targetWidthPx) {
        if (bitmap != null) return@LaunchedEffect
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                synchronized(renderer) {
                    renderer.openPage(page).use { p ->
                        val w = targetWidthPx.coerceAtLeast(1)
                        val h = (p.height * w.toFloat() / p.width).toInt().coerceAtLeast(1)
                        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
                            b.eraseColor(android.graphics.Color.WHITE)
                            p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }.asImageBitmap()
                    }
                }
            }.getOrNull()
        }
        if (bmp != null) {
            cache.put(page, bmp)
            bitmap = bmp
        } else {
            renderFailed = true
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) {
        val bmp = bitmap
        when {
            bmp != null -> Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        })
                    }
                    .pointerInput(scale > 1f) {
                        // 仅放大态接管拖动/捏合：1x 时手势留给 HorizontalPager 翻页
                        if (scale > 1f) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                offset += pan
                                if (scale <= 1f) offset = Offset.Zero
                            }
                        }
                    }
            )
            renderFailed -> Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            else -> CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
