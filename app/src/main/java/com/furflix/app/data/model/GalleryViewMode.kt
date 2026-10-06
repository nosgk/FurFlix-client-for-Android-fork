package com.furflix.app.data.model

/**
 * Gallery layout for the browse/latest/search feeds. Replaces the legacy
 * `is_grid` boolean with a third mode: GRID = 2-column square cells,
 * MASONRY = 2-column natural-ratio staggered grid, LIST = single column with
 * 4:3 cells.
 */
enum class GalleryViewMode {
    GRID,
    MASONRY,
    LIST;

    companion object {
        fun fromId(id: String): GalleryViewMode =
            entries.firstOrNull { it.name == id } ?: GRID
    }
}

/**
 * 搜索结果视图（独立于全局画廊偏好，search_view_mode 键持久化）：
 * 紧凑多列网格 → 瀑布流 → 大图网格 → 列表。
 */
enum class SearchResultView(val id: String) {
    COMPACT("compact"),
    MASONRY("masonry"),
    LARGE("large"),
    LIST("list");

    /** 循环切换到下一视图（切换按钮用）。 */
    fun next(): SearchResultView = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromId(id: String?): SearchResultView =
            entries.firstOrNull { it.id == id } ?: MASONRY
    }
}
