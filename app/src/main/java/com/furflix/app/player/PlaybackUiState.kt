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
    val failed: Boolean = false,
    /**
     * 音频是否已就绪可播（解码器就绪、时长已知）。
     *
     * 用于"字幕生成"这类要再次读取音频的操作门禁。刻意**不要求整条缓冲完毕**：
     * ExoPlayer 默认只向前预读有限缓冲（数十秒），长音频的 bufferedPosition 在
     * 播到接近结尾前永远追不上 duration —— 那样门禁会把按钮长期压在灰态，用户侧
     * 表现就是"音频都能播了，转录按钮还是灰的"。
     */
    val isReady: Boolean = false
) {
    /** 进度条用的 0..1 比例（时长未知时为 0）。 */
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
