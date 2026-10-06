package com.furflix.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 画廊列表首屏骨架屏（三视图变体）：加载占位与真实网格同构（同列数策略、
 * 同间距、同比例区间），避免加载完成后整页跳动；脉动为单一整块缓入缓出，
 * 不做逐块 shimmer 扫光（长列表里成片扫光既噪又费帧）。
 * 布局参数与 GalleryCells 同源，由调用方按当前视图传入。
 */
private object SkeletonDefaults {
    /** 占位图块宽高比区间（模拟真实作品版式的多样性，瀑布流尤其需要） */
    val ratios = listOf(0.72f, 1.25f, 0.95f, 0.6f, 1.1f, 0.8f, 1.35f, 0.9f, 0.75f, 1.05f)
}

@Composable
private fun PulsingBlock(modifier: Modifier, shape: Shape) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.11f,
        animationSpec = infiniteRepeatable(
            tween(780, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = alpha))
    )
}

/** 单张骨架卡：图块（按给定比例占位）+ 可选标题/署名行 */
@Composable
fun SkeletonCard(
    ratio: Float,
    modifier: Modifier = Modifier,
    showText: Boolean = false
) {
    Column(modifier = modifier) {
        PulsingBlock(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio),
            shape = RoundedCornerShape(8.dp)
        )
        if (showText) {
            Spacer(modifier = Modifier.height(6.dp))
            PulsingBlock(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .height(10.dp),
                shape = RoundedCornerShape(3.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            PulsingBlock(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(9.dp),
                shape = RoundedCornerShape(3.dp)
            )
        }
    }
}

/** 紧凑/大图网格骨架：与 LazyVerticalGrid 同列数、同间距 */
@Composable
fun SkeletonGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    itemCount: Int = 12,
    showTitle: Boolean = false
) {
    LazyVerticalGrid(
        columns = columns,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        userScrollEnabled = false
    ) {
        items((0 until itemCount).toList()) { index ->
            SkeletonCard(
                ratio = SkeletonDefaults.ratios[index % SkeletonDefaults.ratios.size],
                showText = showTitle
            )
        }
    }
}

/** 瀑布流骨架：与 LazyVerticalStaggeredGrid 同列数、同间距，逐块变比例 */
@Composable
fun SkeletonMasonry(
    columns: androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells,
    modifier: Modifier = Modifier,
    itemCount: Int = 10
) {
    LazyVerticalStaggeredGrid(
        columns = columns,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalItemSpacing = 4.dp,
        userScrollEnabled = false
    ) {
        staggeredItems((0 until itemCount).toList()) { index ->
            SkeletonCard(
                ratio = SkeletonDefaults.ratios[index % SkeletonDefaults.ratios.size]
            )
        }
        item(span = StaggeredGridItemSpan.FullLine) { Spacer(modifier = Modifier.height(1.dp)) }
    }
}

/** 单列列表骨架：4:3 图块 + 标题/署名行，与列表卡同构 */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    itemCount: Int = 4
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(itemCount) {
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                SkeletonCard(
                    ratio = 4f / 3f,
                    modifier = Modifier.weight(1f),
                    showText = true
                )
            }
        }
    }
}

/** 画廊三视图的骨架分派（浏览/最新等跟随全局 viewMode 的列表用） */
@Composable
fun GallerySkeletonPlaceholder(
    viewMode: com.furflix.app.data.model.GalleryViewMode,
    gridColumnsPref: Int,
    modifier: Modifier = Modifier
) {
    when (viewMode) {
        com.furflix.app.data.model.GalleryViewMode.MASONRY ->
            SkeletonMasonry(masonryGridCells(gridColumnsPref), modifier)
        com.furflix.app.data.model.GalleryViewMode.LIST -> SkeletonList(modifier)
        else -> SkeletonGrid(galleryGridCells(gridColumnsPref, isList = false), modifier)
    }
}

/** 搜索结果四视图的骨架分派（首页搜索 tab 与标签搜索页共用） */
@Composable
fun SearchSkeletonPlaceholder(
    view: com.furflix.app.data.model.SearchResultView,
    gridColumnsPref: Int,
    modifier: Modifier = Modifier
) {
    when (view) {
        com.furflix.app.data.model.SearchResultView.MASONRY ->
            SkeletonMasonry(masonryGridCells(gridColumnsPref), modifier)
        com.furflix.app.data.model.SearchResultView.LIST -> SkeletonList(modifier)
        com.furflix.app.data.model.SearchResultView.LARGE ->
            SkeletonGrid(GridCells.Fixed(2), modifier, showTitle = true)
        else -> SkeletonGrid(galleryGridCells(0), modifier)
    }
}
