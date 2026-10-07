package com.furflix.app.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.furflix.app.R
import com.furflix.app.data.local.DocFileCache
import com.furflix.app.data.repository.FurRepository
import com.furflix.app.ui.theme.DarkBackground
import com.furflix.app.ui.theme.DarkSurfaceVariant
import com.furflix.app.ui.theme.SubtleGray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PDF 作品主媒体区（FA 详情页无图可渲，仅 Download 直链）。
 * 原件经 [DocFileCache] 缓存优先落盘（重开零下载，持久缓存由 CacheJanitor
 * 修剪），系统 PdfRenderer 按视口分辨率逐页渲染 + 横向翻页；
 * [onOpenViewer] 提供全屏查看器入口（缩放/跳页）。
 */
@Composable
fun PdfHeroContent(
    pdfUrl: String,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    onOpenViewer: (() -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var pageCount by remember { mutableIntStateOf(0) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }

    LaunchedEffect(pdfUrl, attempt) {
        loading = true
        failed = false
        renderer?.let { runCatching { it.close() } }
        renderer = null
        // 缓存优先：DocFileCache 命中零下载，未命中抓取后持久落盘
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
            loading = false
        } catch (_: Exception) {
            // 加密/损坏的 PDF：系统渲染器打不开
            loading = false
            failed = true
        }
    }

    // 只关渲染器；缓存文件保留（持久缓存，重开零下载）
    DisposableEffect(pdfUrl) {
        onDispose {
            renderer?.let { runCatching { it.close() } }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .background(DarkBackground)
            .padding(vertical = 16.dp)
    ) {
        when {
            loading -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().height(420.dp)
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            failed -> PdfFailedState(coverUrl = coverUrl, onRetry = { attempt++ })
            renderer != null -> PdfPager(
                renderer = renderer!!,
                pageCount = pageCount,
                onOpenViewer = onOpenViewer
            )
        }
    }
}

@Composable
private fun PdfPager(
    renderer: PdfRenderer,
    pageCount: Int,
    onOpenViewer: (() -> Unit)?
) {
    val pagerState = rememberPagerState(pageCount = { pageCount })
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // 渲染目标宽 = 盒子物理像素（钳制 720~1600）：视口清晰度与内存的平衡点
        val targetWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
            .toInt().coerceIn(720, 1600)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DarkSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 页码指示
                Text(
                    text = "${pagerState.currentPage + 1} / $pageCount",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    PdfPageImage(renderer = renderer, page = page, targetWidthPx = targetWidthPx)
                }
            }
        }
    }
    if (onOpenViewer != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            onClick = onOpenViewer,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = stringResource(R.string.pdf_open_viewer),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** 单页位图：进入组合才渲染（PdfRenderer 非线程安全，串行加锁），离开即回收。 */
@Composable
private fun PdfPageImage(renderer: PdfRenderer, page: Int, targetWidthPx: Int) {
    var bitmap by remember(page) { mutableStateOf<ImageBitmap?>(null) }
    var renderFailed by remember(page) { mutableStateOf(false) }
    LaunchedEffect(page, targetWidthPx) {
        bitmap = null
        renderFailed = false
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                synchronized(renderer) {
                    renderer.openPage(page).use { p ->
                        // 按目标宽等比缩放（旧版固定 2x 不看视口，大页有 OOM 风险）
                        val w = targetWidthPx.coerceAtLeast(1)
                        val h = (p.height * w.toFloat() / p.width).toInt().coerceAtLeast(1)
                        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                            bmp.eraseColor(Color.White.toArgb())
                            p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }.asImageBitmap()
                    }
                }
            }.getOrNull()
        }
        if (bitmap == null) renderFailed = true
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .background(Color.White)
    ) {
        val bmp = bitmap
        when {
            bmp != null -> Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxSize()
            )
            renderFailed -> Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = SubtleGray,
                modifier = Modifier.size(48.dp)
            )
            else -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun PdfFailedState(coverUrl: String?, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(DarkSurfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
        ) {
            if (!coverUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = SubtleGray,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.pdf_load_failed),
            fontSize = 13.sp,
            color = SubtleGray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Icon(
            Icons.Default.Refresh,
            contentDescription = stringResource(R.string.downloads_retry),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onRetry)
                .padding(4.dp)
        )
    }
}

/**
 * 文档类作品（doc/docx/rtf/odt/txt 等类型）主媒体区：元信息卡 +
 * [onOpenViewer] 非空时提供全屏文档查看器入口（自研查看器承担正文/翻译/
 * 查找/复制/媒体图）；为空时仅下载提示，文件交给系统应用打开。
 */
@Composable
fun DocumentHeroContent(
    fileName: String,
    fileSize: String,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    onOpenViewer: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .background(DarkBackground)
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(DarkSurfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
        ) {
            if (!coverUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // 覆盖一层文档徽标，避免被误认为图片作品
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(text = "DOC", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            } else {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = SubtleGray,
                    modifier = Modifier.size(64.dp)
                )
            }
        }
        if (fileName.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = fileName,
                fontSize = 13.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (fileSize.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = fileSize, fontSize = 12.sp, color = SubtleGray)
        }
        if (onOpenViewer != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                onClick = onOpenViewer,
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = stringResource(R.string.doc_open_viewer),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.doc_download_hint),
            fontSize = 12.sp,
            color = SubtleGray,
            textAlign = TextAlign.Center
        )
    }
}
