package com.furflix.app.ui.components

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.ui.unit.dp

/**
 * 网格列数偏好（FurRepository.gridColumnsFlow：0 = 自适应，2..5 = 固定列数）
 * 到 LazyGrid 单元格策略的统一换算；列表视图恒为单列。
 */
const val GALLERY_ADAPTIVE_COLUMNS = 0
val GALLERY_ADAPTIVE_MIN_WIDTH = 160.dp

fun galleryGridCells(columnsPref: Int, isList: Boolean): GridCells =
    if (isList) {
        GridCells.Fixed(1)
    } else {
        galleryGridCells(columnsPref)
    }

fun galleryGridCells(columnsPref: Int): GridCells =
    if (columnsPref <= GALLERY_ADAPTIVE_COLUMNS) {
        GridCells.Adaptive(GALLERY_ADAPTIVE_MIN_WIDTH)
    } else {
        GridCells.Fixed(columnsPref.coerceIn(2, 5))
    }

fun masonryGridCells(columnsPref: Int): StaggeredGridCells =
    if (columnsPref <= GALLERY_ADAPTIVE_COLUMNS) {
        StaggeredGridCells.Adaptive(GALLERY_ADAPTIVE_MIN_WIDTH)
    } else {
        StaggeredGridCells.Fixed(columnsPref.coerceIn(2, 5))
    }
