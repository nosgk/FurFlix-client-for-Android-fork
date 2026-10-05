package com.furflix.app.data.remote

/**
 * Rewrites a FA thumbnail URL to the smallest CDN tier that covers the
 * requested display box. FA embeds the tier in the thumbnail path as
 * {sid}@NNN-{timestamp}.{ext} with 600 being the largest tier it serves, so a
 * two-column layout fed with the listing's default tier (@300 on browse) would
 * upscale a ~200px-tall source to a ~500px-wide cell.
 *
 * URLs whose path doesn't match the tier pattern (avatars, unusual formats)
 * come back untouched, so callers can use the negotiator unconditionally.
 */
class DynamicThumbnail(private val thumbnailUrl: String) {

    fun bestThumbnailUrl(displayWidthPx: Float, displayHeightPx: Float): String {
        val longestSide = maxOf(displayWidthPx, displayHeightPx)
        val target = SIZES.firstOrNull { it >= longestSide } ?: SIZES.last()
        return thumbnailUrl.replace(SIZE_REGEX) { "${it.groupValues[1]}${target}${it.groupValues[3]}" }
    }

    companion object {
        private val SIZES = intArrayOf(200, 300, 320, 400, 600)
        private val SIZE_REGEX = Regex("(.+@)(\\d+)(-.+)")
    }
}
