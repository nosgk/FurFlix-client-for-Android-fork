package com.furflix.app.player

/**
 * 当前音轨的流元数据：编解码器、采样率、比特率、声道数。
 *
 * 数据来自 ExoPlayer 解出的 [androidx.media3.common.Format]——即容器/流里真实
 * 声明的值，不是估算。字段未知（容器没写或尚未解析）时为 null，UI 只展示已知项。
 */
data class AudioStreamInfo(
    val codec: String? = null,
    val sampleRateHz: Int? = null,
    val bitrateBps: Int? = null,
    val channelCount: Int? = null
) {
    val isEmpty: Boolean
        get() = codec == null && sampleRateHz == null && bitrateBps == null && channelCount == null

    companion object {
        /** MIME → 用户可读的编解码器名；未知 mime 退化为其子类型大写。 */
        fun codecLabel(mime: String?): String? {
            val m = mime?.lowercase() ?: return null
            return when {
                m.startsWith("audio/mpeg") -> "MP3"
                m.contains("mp4a") || m == "audio/aac" || m.contains("aac") -> "AAC"
                m.contains("flac") -> "FLAC"
                m.contains("opus") -> "Opus"
                m.contains("vorbis") -> "Vorbis"
                m.contains("alac") -> "ALAC"
                m == "audio/raw" -> "PCM"
                m.contains("wav") -> "WAV"
                m.contains("amr") -> "AMR"
                m.contains("eac3") -> "E-AC-3"
                m.contains("ac3") -> "AC-3"
                m.contains("dts") -> "DTS"
                m.contains("midi") || m.contains("sp-midi") -> "MIDI"
                else -> m.substringAfter('/').uppercase().take(12)
            }
        }
    }
}
