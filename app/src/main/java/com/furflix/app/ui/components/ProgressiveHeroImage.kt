package com.furflix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.furflix.app.R
import com.furflix.app.data.remote.DynamicThumbnail
import com.furflix.app.data.remote.ImageLoadProgress
import com.furflix.app.ui.theme.DarkSurfaceVariant
import com.furflix.app.ui.theme.FA_Accent
import android.text.format.Formatter

/**
 * 详情页大图加载：底层缓存命中的缩略图占位（ratio 已知时容器预设比例，无跳变），
 * 顶层原图走重试加载；加载中按 URL 展示字节进度环 + 网速芯片。
 *
 * fixedSized：容器高度由调用方约束（单栏 ratio 预设 / 双栏 fillMaxHeight）时为 true，
 * 占位与真图 matchParentSize；为 false（高度未定）时两者按自身内在尺寸撑开，
 * loading 槽退回 4:3 暗盒兜底。
 */
@Composable
fun ProgressiveImageContent(
    fullUrl: String,
    listThumbnailUrl: String?,
    modifier: Modifier = Modifier,
    contentDescription: String?,
    contentScale: ContentScale,
    ratio: Float = 0f,
    fixedSized: Boolean = false,
    fallbackAspectRatio: Float = 4f / 3f,
    onImageClick: (() -> Unit)? = null,
    onImageSuccess: (() -> Unit)? = null
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val quality = LocalThumbnailQuality.current
        val detailQuality = LocalDetailImageQuality.current

        // 占位 URL：按本盒物理像素与清晰度偏好协商（与卡片/预取同源机制），
        // 必须是真缩略档（@NNN-）且不等于原图，否则放弃占位。
        val placeholderUrl = remember(listThumbnailUrl, maxWidth, density.density, quality, ratio) {
            if (listThumbnailUrl.isNullOrEmpty() || listThumbnailUrl == fullUrl) {
                null
            } else {
                val wpx = with(density) { maxWidth.toPx() }
                val hpx = with(density) { maxHeight.toPx() }
                    .takeIf { it.isFinite() }
                    ?: wpx / ratio.coerceIn(0.5f, 2f)
                val negotiated = DynamicThumbnail(listThumbnailUrl)
                    .bestThumbnailUrl(wpx, hpx.coerceAtLeast(1f), quality)
                negotiated.takeIf { it != fullUrl && it.contains(Regex("@\\d{1,4}-")) }
            }
        }

        val imageModifier = if (fixedSized) Modifier.matchParentSize() else Modifier.fillMaxWidth()

        // 底层占位（缓存命中即零流量出图）
        if (placeholderUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(placeholderUrl).build(),
                imageLoader = ImageLoaderFactory.getInstance(LocalContext.current),
                contentDescription = null,
                contentScale = contentScale,
                modifier = imageModifier
            )
        }

        val retryState = rememberRetryImageState(fullUrl)
        RetrySubcomposeAsyncImage(
            state = retryState,
            requestSize = remember(detailQuality, maxWidth, maxHeight, density.density, ratio) {
                when {
                    detailQuality == com.furflix.app.data.repository.FurRepository.DETAIL_QUALITY_AUTO -> null
                    detailQuality == com.furflix.app.data.repository.FurRepository.DETAIL_QUALITY_ORIGINAL ->
                        coil3.size.Size.ORIGINAL
                    else -> coil3.size.Size(detailQuality, detailQuality)
                }
            },
            state = retryState,
            modifier = imageModifier.then(
                if (onImageClick != null) Modifier.clickable(onClick = onImageClick) else Modifier
            ),
            contentDescription = contentDescription,
            contentScale = contentScale,
            onSuccess = { onImageSuccess?.invoke() },
            loading = {
                if (placeholderUrl != null) {
                    // 占位已可见：右下角小进度芯片，不打断视觉
                    LoadProgressChip(
                        url = fullUrl,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (fixedSized) Modifier.fillMaxSize() else Modifier.aspectRatio(fallbackAspectRatio))
                            .background(DarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadProgressChip(url = fullUrl, modifier = Modifier)
                    }
                }
            },
            errorContent = {
                DefaultImageErrorContent(
                    errorText = describeError(retryState.lastError),
                    onRetry = { retryState.retry() },
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        )
    }
}

/** 环形进度 + 百分比/网速；总量未知时以“已加载字节”替代百分比，网速在测速窗口就绪后显示——芯片始终有文字。 */
@Composable
fun LoadProgressChip(url: String, modifier: Modifier = Modifier) {
    val state by ImageLoadProgress.stateFor(url).collectAsState()
    val context = LocalContext.current

    val st = state
    val fraction = if (st != null && st.totalBytes > 0) {
        (st.bytes.toFloat() / st.totalBytes).coerceIn(0f, 1f)
    } else {
        null
    }
    val speedText = if (st != null && st.speedBps > 0) {
        stringResource(R.string.load_speed_format, Formatter.formatFileSize(context, st.speedBps))
    } else {
        null
    }
    val percentText = fraction?.let {
        stringResource(R.string.load_percent_format, (it * 100).toInt())
    }
    // Content-Length 缺失（分块传输）时没有百分比——用已加载字节数兜底，避免只剩一个环
    val bytesText = if (fraction == null && st != null && st.bytes > 0) {
        stringResource(R.string.load_downloaded_format, Formatter.formatFileSize(context, st.bytes))
    } else {
        null
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (fraction != null) {
                CircularProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.size(22.dp),
                    color = FA_Accent,
                    strokeWidth = 2.dp,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = FA_Accent,
                    strokeWidth = 2.dp
                )
            }
            if (percentText != null || bytesText != null || speedText != null) {
                Spacer(Modifier.width(8.dp))
                Column {
                    if (percentText != null) {
                        Text(
                            percentText,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (bytesText != null) {
                        Text(
                            bytesText,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (speedText != null) {
                        Text(
                            speedText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    }
}
