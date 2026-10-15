package com.furflix.app.subtitle

/** 一条字幕：序号 + 起止毫秒 + 文本（文本可含行内 Markdown 强调标记）。 */
data class SubtitleCue(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String
)

/**
 * 一整条字幕轨。cues 恒按开始时间升序（[SrtParser] 负责排序），故 [cueAt] 可二分。
 */
data class SubtitleTrack(
    val cues: List<SubtitleCue>
) {
    val isEmpty: Boolean get() = cues.isEmpty()

    /** 命中 [positionMs] 所在的那条字幕；落在间隙里返回 null。 */
    fun cueAt(positionMs: Long): SubtitleCue? {
        var lo = 0
        var hi = cues.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val cue = cues[mid]
            when {
                positionMs < cue.startMs -> hi = mid - 1
                positionMs > cue.endMs -> lo = mid + 1
                else -> return cue
            }
        }
        return null
    }

    companion object {
        val EMPTY = SubtitleTrack(emptyList())
    }
}
