package com.furflix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.furflix.app.R
import com.furflix.app.data.model.CardStats
import com.furflix.app.data.model.Submission
import com.furflix.app.data.remote.DynamicThumbnail
import com.furflix.app.translate.data.TranslationCategory
import com.furflix.app.translate.data.TranslationUiPrefs
import com.furflix.app.translate.ui.TranslatableText
import com.furflix.app.translate.ui.TranslationEntry
import com.furflix.app.translate.ui.TranslationUiState
import com.furflix.app.translate.ui.rememberTranslationEntry
import com.furflix.app.translate.ui.rememberTranslationPrefs
import com.furflix.app.ui.theme.*

/**
 * 卡片标题自动翻译：延迟 500ms 再送翻，快速滑动时卡片离开组合即取消
 * （未发出的不补发），避免长列表刷屏式打翻译服务；缓存与中文跳过由
 * TranslationEntry.translateAuto 统一处理。
 */
@Composable
private fun CardTitleAutoEffect(entry: TranslationEntry, prefs: TranslationUiPrefs) {
    LaunchedEffect(entry, prefs.enabled, prefs.auto) {
        if (prefs.enabled && prefs.auto) {
            kotlinx.coroutines.delay(500)
            val state = entry.state.value
            if (state is TranslationUiState.Idle && !state.autoAttempted) {
                entry.translateAuto()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubmissionCard(
    submission: Submission,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    compact: Boolean = false,
    ratio: Float = 0f,
    stats: CardStats? = null,
    forceFavorited: Boolean = false,
    selectionActive: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onAuthorClick: ((String) -> Unit)? = null
) {
    // 长按进入多选时给触觉确认(§17 触觉反馈)
    val haptic = LocalHapticFeedback.current
    val hapticLongClick = onLongClick?.let { callback ->
        {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            callback()
        }
    }
    // 多选勾选触觉：仅在本卡选中态真正翻转时触发（滚动入屏的既有选中态不响）
    var wasSelected by remember { mutableStateOf(isSelected) }
    LaunchedEffect(isSelected, selectionActive) {
        if (selectionActive && isSelected != wasSelected) {
            haptic.performHapticFeedback(
                if (isSelected) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
            )
        }
        wasSelected = isSelected
    }
    // 按压态反馈：轻微缩小（水波纹由 Card 自带 indication 提供）
    val cardInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val cardPressed by cardInteractionSource.collectIsPressedAsState()
    val cardScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (cardPressed) 0.97f else 1f,
        animationSpec = androidx.compose.animation.core.tween(120),
        label = "cardPressScale"
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .combinedClickable(
                interactionSource = cardInteractionSource,
                // Compose 1.8+ 新重载：interactionSource 与 indication 必须成对给出
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick,
                onLongClick = hapticLongClick
            ),
        shape = RoundedCornerShape(if (compact) 8.dp else 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardDark
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            val cardShape = if (!showTitle || compact)
                RoundedCornerShape(if (compact) 8.dp else 12.dp)
            else
                RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            var imageLoaded by remember { mutableStateOf(false) }

            // Masonry passes the parsed thumbnail ratio so the card pre-sizes to
            // the real image shape (zero jump on load); clamped so extreme
            // artwork can't unbalance the lanes. Grid/list keep fixed ratios.
            val displayRatio = when {
                ratio > 0f -> ratio.coerceIn(0.45f, 2.2f)
                compact -> 1f
                else -> 4f / 3f
            }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(displayRatio)
            ) {
                // 卡片按自身物理像素宽度协商 CDN 档位（清晰度偏好经 LocalThumbnailQuality
                // 注入），与 MasonryPrefetchEffect 同源，预取命中同一缓存键。
                val boxWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
                val quality = LocalThumbnailQuality.current
                val displayUrl = remember(submission.thumbnailUrl, boxWidthPx, quality) {
                    DynamicThumbnail(submission.thumbnailUrl)
                        .bestThumbnailUrl(boxWidthPx, boxWidthPx / displayRatio, quality)
                }
                // 统计浮层只对本地已知的帖子显示（详情页回填）；收藏 tab 整页实心心形
                val showStats = stats != null || forceFavorited
                // 失败自动重试 3 次；耗尽后小浮层显示错误码与重试
                val retryState = rememberRetryImageState(displayUrl)
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(retryState.effectiveUrl)
                        .crossfade(300)
                        .build(),
                    imageLoader = ImageLoaderFactory.getInstance(LocalContext.current),
                    contentDescription = submission.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(cardShape),
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(DarkSurfaceVariant),
                    error = ColorPainter(DarkSurfaceVariant),
                    onLoading = { imageLoaded = false },
                    onSuccess = {
                        retryState.onSuccess()
                        imageLoaded = true
                    },
                    onError = { errorState ->
                        retryState.onError(errorState.result.throwable)
                        if (retryState.exhausted) imageLoaded = true
                    }
                )
                // 多选角标：激活时右上角勾选圈
                if (selectionActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSelected) FA_Accent else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                RetryEffect(retryState)
                if (retryState.exhausted) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(cardShape)
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                describeError(retryState.lastError),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                maxLines = 2,
                                textAlign = TextAlign.Center
                            )
                            TextButton(
                                onClick = { retryState.retry() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(stringResource(R.string.downloads_retry), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                if (!imageLoaded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(cardShape)
                            .background(DarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = FA_Accent.copy(alpha = 0.5f),
                            strokeWidth = 2.dp
                        )
                    }
                }

                // Soft bottom scrim — fades in only over the lower third so
                // the artwork stays clean; just enough to anchor the author text.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.55f to Color.Transparent,
                                    0.80f to Color.Black.copy(alpha = if (compact) 0.16f else 0.20f),
                                    1.0f to Color.Black.copy(alpha = if (compact) 0.40f else 0.48f)
                                )
                            )
                        )
                )

                // Rating badge
                if (submission.rating.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .padding(if (compact) 4.dp else 8.dp)
                            .align(Alignment.TopStart),
                        shape = RoundedCornerShape(if (compact) 4.dp else 6.dp),
                        color = when {
                            submission.rating.contains("General", true) -> Rating_General
                            submission.rating.contains("Mature", true)  -> Rating_Mature
                            submission.rating.contains("Adult", true)   -> Rating_Adult
                            else -> MaterialTheme.colorScheme.primary
                        }
                    ) {
                        Text(
                            text = submission.rating.take(1),
                            modifier = Modifier.padding(horizontal = if (compact) 4.dp else 8.dp, vertical = 2.dp),
                            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }

                // Stats overlay — bottom-end on the scrim; heart goes pink when
                // the submission is locally known to be favorited. A file-type
                // badge (ext, falling back to FA's type label) leads the row.
                if (showStats) {
                    val statStyle = MaterialTheme.typography.labelSmall.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.8f),
                            offset = Offset(0f, 1f),
                            blurRadius = 4f
                        )
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = if (compact) 6.dp else 10.dp, bottom = if (compact) 6.dp else 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val favorited = forceFavorited || (stats?.favorited ?: false)
                        // 文件类型徽标：扩展名优先（JPG/GIF/SWF/TXT/MP3…），无后缀回落 FA Type 文本
                        stats?.let { s ->
                            val typeLabel = s.fileExt.ifEmpty { s.fileType }.uppercase().take(6)
                            if (typeLabel.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Black.copy(alpha = 0.45f)
                                ) {
                                    Text(
                                        text = typeLabel,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (favorited) Pink else Color.White.copy(alpha = 0.9f)
                        )
                        if (stats != null) {
                            Text(formatCount(stats.favorites), style = statStyle, color = if (favorited) Pink else Color.White)
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = Color.White.copy(alpha = 0.9f)
                            )
                            Text(formatCount(stats.views), style = statStyle, color = Color.White)
                            Icon(
                                imageVector = Icons.Default.ChatBubble,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = Color.White.copy(alpha = 0.9f)
                            )
                            Text(formatCount(stats.comments), style = statStyle, color = Color.White)
                        }
                    }
                }

                // Author name (always shown inside image) — tappable, opens the artist profile
                if (submission.author.isNotEmpty()) {
                    val baseStyle = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
                    Text(
                        text = if (compact) submission.author else "by ${submission.author}",
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .then(
                                if (onAuthorClick != null) Modifier.clickable {
                                    onAuthorClick(submission.authorUsername.ifEmpty { submission.author })
                                } else Modifier
                            )
                            .padding(
                                start = if (compact) 6.dp else 10.dp,
                                end = (if (compact) 6.dp else 10.dp) + if (showStats) 96.dp else 0.dp,
                                top = if (compact) 6.dp else 10.dp,
                                bottom = if (compact) 6.dp else 10.dp
                            ),
                        style = baseStyle.copy(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.8f),
                                offset = Offset(0f, 1f),
                                blurRadius = 4f
                            )
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Title inside image when showTitle=true and compact
                if (showTitle && compact) {
                    // Title is shown inside the card below, not overlaid when compact
                }
            }

            // Bottom section — only shown in full (1-column) mode or when showTitle=true and not compact
            if (!compact) {
                if (showTitle) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // 标题双语翻译：全局翻译开启时自动送翻（防抖），双语/仅译文随类别设置
                        val translationPrefs = rememberTranslationPrefs()
                        val titleEntry = rememberTranslationEntry(
                            key = submission.id,
                            text = submission.title,
                            category = TranslationCategory.TITLE
                        )
                        CardTitleAutoEffect(titleEntry, translationPrefs)
                        TranslatableText(
                            entry = titleEntry,
                            prefs = translationPrefs,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (submission.favorites > 0) {
                                    StatChip(icon = Icons.Default.Favorite, text = formatCount(submission.favorites), color = FA_Accent)
                                }
                                if (submission.comments > 0) {
                                    StatChip(icon = Icons.Default.ChatBubble, text = formatCount(submission.comments), color = FA_LightBlue)
                                }
                                if (submission.views > 0) {
                                    StatChip(icon = Icons.Default.Visibility, text = formatCount(submission.views), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                } else {
                    // No title, no padding — image fills card edge-to-edge
                }
            } else {
                // Compact mode: show title below image as small text if showTitle=true
                if (showTitle) {
                    val translationPrefs = rememberTranslationPrefs()
                    val titleEntry = rememberTranslationEntry(
                        key = submission.id,
                        text = submission.title,
                        category = TranslationCategory.TITLE
                    )
                    CardTitleAutoEffect(titleEntry, translationPrefs)
                    TranslatableText(
                        entry = titleEntry,
                        prefs = translationPrefs,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(width = 14.dp, height = 14.dp),
            tint = color
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
