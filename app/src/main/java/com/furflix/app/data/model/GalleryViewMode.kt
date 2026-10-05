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
