package com.furflix.app.data.remote

/**
 * Rewrites a FA thumbnail URL to the CDN tier that best serves the requested
 * display box, honoring the user's quality preference. FA embeds the tier in
 * the thumbnail path as {sid}@NNN-{timestamp}.{ext} with 600 being the largest
 * tier it serves, so a two-column layout fed with the listing's default tier
 * (@300 on browse) would upscale a ~200px-tall source on a high-density screen.
 *
 * Qualities:
 *  - QUALITY_AUTO: smallest tier covering the display box in physical pixels.
 *  - QUALITY_DATA_SAVER: auto, with the tier capped at 300.
 *  - QUALITY_HIGH: always the largest tier (600).
 *
 * URLs whose path doesn't match the tier pattern (avatars, unusual formats)
 * come back untouched, so callers can use the negotiator unconditionally.
 */
class DynamicThumbnail(private val thumbnailUrl: String) {

    fun bestThumbnailUrl(displayWidthPx: Float, displayHeightPx: Float, quality: Int = QUALITY_AUTO): String {
        val longestSide = maxOf(displayWidthPx, displayHeightPx)
        val target = when (quality) {
            QUALITY_HIGH -> MAX_TIER
            QUALITY_DATA_SAVER -> smallestTierCovering(minOf(longestSide, DATA_SAVER_CAP))
            else -> smallestTierCovering(longestSide)
        }
        return thumbnailUrl.replace(SIZE_REGEX) { "${it.groupValues[1]}${target}${it.groupValues[3]}" }
    }

    private fun smallestTierCovering(side: Float): Int =
        SIZES.firstOrNull { it >= side } ?: SIZES.last()

    companion object {
        const val QUALITY_AUTO = 0
        const val QUALITY_DATA_SAVER = 1
        const val QUALITY_HIGH = 2

        /** FA serves no tier above 600. */
        const val MAX_TIER = 600

        /** Tier ceiling for the data-saver preference. */
        const val DATA_SAVER_CAP = 300

        private val SIZES = intArrayOf(200, 300, 320, 400, 600)
        private val SIZE_REGEX = Regex("(.+@)(\\d+)(-.+)")
    }
}
