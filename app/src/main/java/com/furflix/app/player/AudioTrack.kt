package com.furflix.app.player

import com.furflix.app.data.model.Submission

/**
 * 播放队列里的一条音频作品。
 *
 * 刻意与 [Submission] 解耦：播放器只需要驱动 UI 与通知栏的最小字段集（标题/作者/
 * 封面/直链/作品号），详情页负责把 Submission 映射成它。这样迷你条与后台服务
 * 都不必依赖抓取层的完整模型。
 */
data class AudioTrack(
    val postId: String,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val url: String
)

/** 音频类作品的扩展名集合（与详情页判定同源，避免两处漂移）。 */
val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "ogg", "wav", "flac", "aac", "mid", "midi")

/** FA 把音频作品标成 Music/Podcast 类，或按扩展名判定。 */
fun Submission.isAudioWork(): Boolean =
    fileExt.lowercase() in AUDIO_EXTENSIONS ||
        fileType.equals("Music", ignoreCase = true) ||
        fileType.equals("Podcast", ignoreCase = true)

/**
 * Submission → 播放条目。音频直链在 [Submission.fileUrl]；部分列表条目只带了
 * [Submission.fullImageUrl]（详情页对音频作品的兜底直链也放在这里），故按序回退。
 */
fun Submission.toAudioTrack(): AudioTrack = AudioTrack(
    postId = id,
    title = title.ifEmpty { fileName.ifEmpty { id } },
    artist = author,
    coverUrl = thumbnailUrl.takeIf { it.isNotEmpty() },
    url = fileUrl.ifEmpty { fullImageUrl }
)
