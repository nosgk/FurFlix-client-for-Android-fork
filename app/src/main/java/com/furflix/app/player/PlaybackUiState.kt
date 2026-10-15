package com.furflix.app.player

/**
 * 播放器对外快照。详情页播放器、全局迷你条、通知栏都只读它，播放器本身是唯一
 * 事实源（[PlaybackController]）。
 */
data class PlaybackUiState(
    val track: AudioTrack? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val repeatOne: Boolean = false,
    val speed: Float = 1f,
    /** 始终为 1.0 表示"变速不变调"；保留字段以便将来单独变调。 */
    val pitch: Float = 1f,
    val stream: AudioStreamInfo = AudioStreamInfo(),
    val failed: Boolean = false
) {
    /** 进度条用的 0..1 比例（时长未知时为 0）。 */
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
