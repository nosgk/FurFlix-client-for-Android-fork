package com.furflix.app.data.remote

/**
 * Rewrites a FA thumbnail URL to the requested CDN tier. FA embeds the tier
 * in the thumbnail path as {sid}@NNN-{timestamp}.{ext}; tiers are pixel sizes
 * and [TIERS] lists everything the CDN serves.
 *
 * Selection:
 *  - TIER_AUTO (0): smallest tier covering the display box in physical pixels.
 *  - any tier from [TIERS]: forced, regardless of the display box.
 *  - anything else: falls back to auto.
 *
 * URLs whose path doesn't match the tier pattern (avatars, unusual formats)
 * come back untouched, so callers can use the negotiator unconditionally.
 */
class DynamicThumbnail(private val thumbnailUrl: String) {

    fun bestThumbnailUrl(displayWidthPx: Float, displayHeightPx: Float, tier: Int = TIER_AUTO): String {
        val longestSide = maxOf(displayWidthPx, displayHeightPx)
        val target = if (TIERS.contains(tier)) tier else smallestTierCovering(longestSide)
        return thumbnailUrl.replace(SIZE_REGEX) { "${it.groupValues[1]}${target}${it.groupValues[3]}" }
    }

    private fun smallestTierCovering(side: Float): Int =
        SIZES.firstOrNull { it >= side } ?: SIZES.last()

    companion object {
        /** 0 = 自动：按显示尺寸选最小够用档 */
        const val TIER_AUTO = 0

        /** FA CDN 实际提供的全部档位（像素），设置里全部开放直选 */
        val TIERS = listOf(200, 300, 320, 400, 600)

        private val SIZES = intArrayOf(200, 300, 320, 400, 600)
        private val SIZE_REGEX = Regex("(.+@)(\\d+)(-.+)")
    }
}
