package com.furflix.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.furflix.app.R
import com.furflix.app.data.remote.DynamicThumbnail
import com.furflix.app.data.repository.FurRepository
import com.furflix.app.ui.components.HazeTopAppBar
import com.furflix.app.ui.theme.AppIcon
import com.furflix.app.ui.theme.ThemePreference
import com.furflix.app.utils.IconManager
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { FurRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    val themePref by repository.themeFlow.collectAsState(initial = ThemePreference.DEFAULT)
    val appIcon by repository.appIconFlow.collectAsState(initial = AppIcon.DARK)
    val persistedViewMode by repository.viewModeFlow.collectAsState(initial = com.furflix.app.data.model.GalleryViewMode.GRID)
    val gridColumnsPref by repository.gridColumnsFlow.collectAsState(initial = 2)
    val thumbnailQuality by repository.thumbnailQualityFlow.collectAsState(initial = 0)
    val detailImageQuality by repository.detailImageQualityFlow.collectAsState(initial = FurRepository.DETAIL_QUALITY_AUTO)
    val detailLayoutPref by repository.detailLayoutFlow.collectAsState(initial = FurRepository.DETAIL_LAYOUT_AUTO)
    val detailSplit by repository.detailSplitFlow.collectAsState(initial = FurRepository.DETAIL_SPLIT_DEFAULT)
    val textScale by repository.textScaleFlow.collectAsState(initial = 1f)
    val autoHideBars by repository.hideBarsOnScrollFlow.collectAsState(initial = true)
    // 乐观覆盖：先本地生效，持久化值回流后对齐（与 HomeScreen 顶栏切换同款）
    var pendingViewMode by remember { mutableStateOf<com.furflix.app.data.model.GalleryViewMode?>(null) }
    val viewMode = pendingViewMode ?: persistedViewMode
    LaunchedEffect(persistedViewMode) {
        if (pendingViewMode == persistedViewMode) pendingViewMode = null
    }

    val hazeState = remember { HazeState() }

    Scaffold(
        topBar = {
            HazeTopAppBar(
                title = { Text(stringResource(R.string.settings_appearance)) },
                hazeState = hazeState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .hazeSource(state = hazeState)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // --- Theme Selection ---
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            
            val listState = rememberLazyListState()

            LaunchedEffect(themePref) {
                val index = ThemePreference.entries.indexOf(themePref)
                if (index != -1) {
                    listState.animateScrollToItem(maxOf(0, index - 1))
                }
            }

            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(ThemePreference.entries.toTypedArray()) { theme ->
                    val isSelected = theme == themePref
                    val backgroundColor by animateColorAsState(
                        targetValue = if (isSelected) theme.color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        label = "bgColor"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (isSelected) theme.color else Color.Transparent,
                        label = "borderColor"
                    )
                    val dotSize by animateDpAsState(
                        targetValue = if (isSelected) 24.dp else 32.dp,
                        label = "dotSize"
                    )

                    Surface(
                        modifier = Modifier
                            .height(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .clickable { scope.launch { repository.saveTheme(theme.id) } },
                        color = backgroundColor,
                        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) borderColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = if (isSelected) 16.dp else 12.dp)
                                .fillMaxHeight()
                                .animateContentSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(dotSize)
                                    .clip(CircleShape)
                                    .background(theme.color)
                            )
                            
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(theme.titleRes),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GridView, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                when (viewMode) {
                                    com.furflix.app.data.model.GalleryViewMode.GRID -> stringResource(R.string.settings_grid_view)
                                    com.furflix.app.data.model.GalleryViewMode.MASONRY -> stringResource(R.string.settings_view_masonry)
                                    com.furflix.app.data.model.GalleryViewMode.LIST -> stringResource(R.string.settings_list_view)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                stringResource(R.string.settings_view_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val modes = listOf(
                        stringResource(R.string.settings_grid_view) to com.furflix.app.data.model.GalleryViewMode.GRID,
                        stringResource(R.string.settings_view_masonry) to com.furflix.app.data.model.GalleryViewMode.MASONRY,
                        stringResource(R.string.settings_list_view) to com.furflix.app.data.model.GalleryViewMode.LIST
                    )
                    modes.forEach { (name, mode) ->
                        val isSelected = viewMode == mode
                        val bgColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        val textColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(24.dp))
                                .background(bgColor)
                                .clickable {
                                    pendingViewMode = mode
                                    scope.launch { repository.saveViewMode(mode) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(name, color = textColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mini preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(8.dp)
                ) {
                    if (viewMode == com.furflix.app.data.model.GalleryViewMode.MASONRY) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    Modifier
                                        .weight(1.2f)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                )
                                Box(
                                    Modifier
                                        .weight(0.7f)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    Modifier
                                        .weight(0.7f)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                )
                                Box(
                                    Modifier
                                        .weight(1.2f)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                )
                            }
                        }
                    } else if (viewMode == com.furflix.app.data.model.GalleryViewMode.GRID) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                )
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                )
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            )
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            )
                        }
                    }
                }
            }

            // --- Grid columns (0 = adaptive, 2..5 fixed) ---
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GridView, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.settings_grid_columns),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.settings_grid_columns_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val columnOptions = listOf(
                    stringResource(R.string.settings_grid_columns_adaptive) to 0,
                    "2" to 2,
                    "3" to 3,
                    "4" to 4,
                    "5" to 5
                )
                columnOptions.forEach { (name, count) ->
                    val isSelected = gridColumnsPref == count
                    val bgColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    val textColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .background(bgColor)
                            .clickable { scope.launch { repository.saveGridColumns(count) } },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(name, color = textColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // --- Thumbnail tier (0 = auto fit, else forced pixel tier) ---
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.settings_thumbnail_quality),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.settings_thumbnail_quality_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 全部可用档位直选；标签即像素值（数值+单位，不参与翻译）
                val qualityOptions = listOf(
                    stringResource(R.string.settings_thumb_quality_auto) to DynamicThumbnail.TIER_AUTO
                ) + DynamicThumbnail.TIERS.map { tier -> "${tier}px" to tier }
                qualityOptions.forEach { (name, tier) ->
                    val isSelected = thumbnailQuality == tier
                    val bgColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    val textColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .background(bgColor)
                            .clickable { scope.launch { repository.saveThumbnailQuality(tier) } },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(name, color = textColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // --- Detail page image quality (0 = follow display, -1 = original) ---
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.HighQuality, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.settings_detail_quality),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.settings_detail_quality_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Auto → 各像素档 → 原图（不限解码尺寸）；像素档即最长边上限
                val detailQualityOptions = listOf(
                    stringResource(R.string.settings_thumb_quality_auto) to FurRepository.DETAIL_QUALITY_AUTO
                ) + FurRepository.DETAIL_QUALITY_TIERS.map { tier -> "${tier}px" to tier } +
                    listOf(stringResource(R.string.settings_detail_quality_original) to FurRepository.DETAIL_QUALITY_ORIGINAL)
                detailQualityOptions.forEach { (name, tier) ->
                    val isSelected = detailImageQuality == tier
                    val bgColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    val textColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .background(bgColor)
                            .clickable { scope.launch { repository.saveDetailImageQuality(tier) } },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(name, color = textColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // --- Text size (multiplies the system font scale) ---            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.settings_text_size),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.settings_text_size_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val scaleOptions = listOf(
                    stringResource(R.string.settings_text_scale_small) to 0.85f,
                    stringResource(R.string.settings_text_scale_standard) to 1.0f,
                    stringResource(R.string.settings_text_scale_large) to 1.15f,
                    stringResource(R.string.settings_text_scale_xlarge) to 1.3f,
                    stringResource(R.string.settings_text_scale_max) to 1.5f
                )
                scaleOptions.forEach { (name, scale) ->
                    val isSelected = textScale == scale
                    val bgColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    val textColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .background(bgColor)
                            .clickable { scope.launch { repository.saveTextScale(scale) } },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(name, color = textColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // --- Auto-hide bars while scrolling ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_auto_hide_bars),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.settings_auto_hide_bars_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoHideBars,
                    onCheckedChange = { scope.launch { repository.saveHideBarsOnScroll(it) } }
                )
            }

            // --- Watchlist card background: source + gaussian blur ---
            val bannerSource by repository.watchlistBannerSourceFlow.collectAsState(
                initial = FurRepository.WATCHLIST_BANNER_SOURCE_BANNER
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wallpaper, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.settings_watchlist_card_bg),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.settings_watchlist_card_bg_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val sourceOptions = listOf(
                    stringResource(R.string.settings_watchlist_bg_profile) to FurRepository.WATCHLIST_BANNER_SOURCE_BANNER,
                    stringResource(R.string.settings_watchlist_bg_latest) to FurRepository.WATCHLIST_BANNER_SOURCE_LATEST
                )
                sourceOptions.forEach { (name, source) ->
                    val isSelected = bannerSource == source
                    val bgColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    val textColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .background(bgColor)
                            .clickable { scope.launch { repository.saveWatchlistBannerSource(source) } },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(name, color = textColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                stringResource(R.string.settings_watchlist_card_bg_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 模糊半径滑杆：0 = 清晰原图，拖动即时预览，松手持久化
            val savedBlur by repository.watchlistBannerBlurFlow.collectAsState(initial = 0f)
            var pendingBlur by remember(savedBlur) { mutableFloatStateOf(savedBlur) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_watchlist_bg_blur),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.settings_watchlist_bg_blur_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    stringResource(R.string.settings_watchlist_bg_blur_value, pendingBlur.roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = pendingBlur,
                onValueChange = { pendingBlur = it },
                onValueChangeFinished = {
                    scope.launch { repository.saveWatchlistBannerBlur(pendingBlur) }
                },
                valueRange = 0f..FurRepository.WATCHLIST_BANNER_BLUR_MAX
            )

            // --- Detail layout (auto by width / single / two-pane) ---
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Dashboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.settings_detail_layout),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.settings_detail_layout_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val layoutOptions = listOf(
                    stringResource(R.string.settings_detail_layout_auto) to FurRepository.DETAIL_LAYOUT_AUTO,
                    stringResource(R.string.settings_detail_layout_single) to FurRepository.DETAIL_LAYOUT_SINGLE,
                    stringResource(R.string.settings_detail_layout_dual) to FurRepository.DETAIL_LAYOUT_DUAL
                )
                layoutOptions.forEach { (name, layout) ->
                    val isSelected = detailLayoutPref == layout
                    val bgColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    val textColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .background(bgColor)
                            .clickable { scope.launch { repository.saveDetailLayout(layout) } },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(name, color = textColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // --- Two-pane split: artwork pane share, drag to preview, persist on release ---
            Spacer(modifier = Modifier.height(12.dp))
            var pendingSplit by remember(detailSplit) { mutableFloatStateOf(detailSplit.toFloat()) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_detail_split),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.settings_detail_split_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    stringResource(R.string.settings_detail_split_value, pendingSplit.toInt(), 100 - pendingSplit.toInt()),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = pendingSplit,
                onValueChange = { pendingSplit = it },
                onValueChangeFinished = {
                    scope.launch { repository.saveDetailSplit(pendingSplit.toInt()) }
                },
                valueRange = FurRepository.DETAIL_SPLIT_MIN.toFloat()..FurRepository.DETAIL_SPLIT_MAX.toFloat(),
                steps = 4
            )


                Spacer(modifier = Modifier.height(16.dp))
                
                val defaultTab by repository.defaultTabFlow.collectAsState(initial = 0)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                stringResource(R.string.settings_startup_tab),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                stringResource(R.string.settings_startup_tab_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabs = listOf(stringResource(R.string.nav_tab_browse) to 0, stringResource(R.string.nav_tab_latest) to 1, stringResource(R.string.nav_tab_search) to 2, stringResource(R.string.nav_tab_profile) to 3)
                    tabs.forEach { (name, index) ->
                        val isSelected = defaultTab == index
                        val bgColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        val textColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(24.dp))
                                .background(bgColor)
                                .clickable { scope.launch { repository.saveDefaultTab(index) } },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(name, color = textColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

            // --- App Icon Selection ---
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_app_icon),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            val iconListState = rememberLazyListState()
            LaunchedEffect(appIcon) {
                val index = AppIcon.entries.indexOf(appIcon)
                if (index != -1) {
                    iconListState.animateScrollToItem(maxOf(0, index - 1))
                }
            }

            LazyRow(
                state = iconListState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(AppIcon.entries.toTypedArray()) { icon ->
                    val isSelected = icon == appIcon
                    val backgroundColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        label = "bgColor"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        label = "borderColor"
                    )
                    val imgSize by animateDpAsState(
                        targetValue = if (isSelected) 36.dp else 48.dp,
                        label = "imgSize"
                    )

                    Surface(
                        modifier = Modifier
                            .height(64.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .clickable {
                                scope.launch {
                                    repository.saveAppIcon(icon)
                                }
                            },
                        color = backgroundColor,
                        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) borderColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(32.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = if (isSelected) 16.dp else 8.dp)
                                .fillMaxHeight()
                                .animateContentSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(id = icon.previewRes),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(imgSize)
                                    .clip(CircleShape)
                            )
                            
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(icon.titleRes),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

        }
    }
}
