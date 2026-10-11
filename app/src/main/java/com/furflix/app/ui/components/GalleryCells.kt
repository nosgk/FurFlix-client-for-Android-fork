package com.furflix.app.ui.components

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 网格列数偏好（FurRepository.gridColumnsFlow：0 = 自适应，2..5 = 固定列数）
 * 到 LazyGrid 单元格策略的统一换算；列表视图恒为单列。
 *
 * 三个换算函数都是 @Composable：自适应档要读屏宽，而它们只在
 * LazyVerticalGrid/StaggeredGrid 的 columns 参数处被调用（必在组合期）。
 */
const val GALLERY_ADAPTIVE_COLUMNS = 0
val GALLERY_ADAPTIVE_MIN_WIDTH = 160.dp

/**
 * 自适应档的"最小卡片宽"按屏宽分档：大屏上单纯按 160dp 自适应会切出七八列
 * 邮票大小的卡片，排版层级随之塌掉。
 *
 * 上限刻意压在 200dp：FA CDN 的缩略图最高只有 600px 档，卡片按自身物理像素
 * 宽度协商档位——200dp 在 3x 密度屏上正好 600px，再宽就只能把小图放大，
 * "响应式"反而换来更糊的卡片。改这个常量前先确认 CDN 档位表
 * （[com.furflix.app.data.remote.DynamicThumbnail.TIERS]）是否跟着放宽了。
 */
@Composable
fun adaptiveGalleryMinWidth(): Dp {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp >= 840 -> 200.dp
        widthDp >= 600 -> 180.dp
        else -> GALLERY_ADAPTIVE_MIN_WIDTH
    }
}

@Composable
fun galleryGridCells(columnsPref: Int, isList: Boolean): GridCells =
    if (isList) {
        GridCells.Fixed(1)
    } else {
        galleryGridCells(columnsPref)
    }

@Composable
fun galleryGridCells(columnsPref: Int): GridCells =
    if (columnsPref <= GALLERY_ADAPTIVE_COLUMNS) {
        GridCells.Adaptive(adaptiveGalleryMinWidth())
    } else {
        GridCells.Fixed(columnsPref.coerceIn(2, 5))
    }

@Composable
fun masonryGridCells(columnsPref: Int): StaggeredGridCells =
    if (columnsPref <= GALLERY_ADAPTIVE_COLUMNS) {
        StaggeredGridCells.Adaptive(adaptiveGalleryMinWidth())
    } else {
        StaggeredGridCells.Fixed(columnsPref.coerceIn(2, 5))
    }
