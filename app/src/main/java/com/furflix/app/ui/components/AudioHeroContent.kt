package com.furflix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.furflix.app.R
import com.furflix.app.data.remote.DynamicThumbnail
import com.furflix.app.player.AudioTrack
import com.furflix.app.player.PlaybackController
import com.furflix.app.subtitle.SubtitleController
import com.furflix.app.subtitle.SubtitleRequest
import com.furflix.app.subtitle.SubtitleTrack
import com.furflix.app.subtitle.TranscriptionConfig
import com.furflix.app.subtitle.TranscriptionSettings
import com.furflix.app.subtitle.ui.SubtitlePanel
import com.furflix.app.subtitle.ui.transcriptionErrorText
import com.furflix.app.ui.theme.DarkBackground
import com.furflix.app.ui.theme.DarkSurfaceVariant
import com.furflix.app.ui.theme.SubtleGray
import java.util.Locale

private fun formatAudioTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

private fun formatKHz(hz: Int): String {
    val k = hz / 1000f
    return if (k % 1f == 0f) k.toInt().toString() else String.format(Locale.US, "%.1f", k)
}

/**
 * Music/音频作品的主媒体区（FA 详情页为 <audio> + Download 直链，无图可渲）。
 *
 * 本站播放器只是全局 [PlaybackController] 的一个遥控面板：播放态、进度、倍速、
 * 单曲循环都读它、改它。因此离开详情页音频继续播（后台服务接管），回到本页
 * 也自动接上当前进度——这是"后台无缝续播 + 全局迷你条"的前提。
 */
@Composable
fun AudioHeroContent(
    track: AudioTrack,
    queue: List<AudioTrack>,
    coverUrl: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by PlaybackController.state.collectAsState()
    val untitledLabel = stringResource(R.string.player_untitled)

    // 字幕会话跟随当前作品切换；缓存命中即直接显示，未命中等用户点生成
    val subtitleState by SubtitleController.state.collectAsState()
    val transcriptionConfig by remember { TranscriptionSettings.getInstance(context).config }
        .collectAsState(initial = TranscriptionConfig())
    LaunchedEffect(track.postId) { SubtitleController.select(context, track.postId) }
    val subtitleTrack = if (subtitleState.postId == track.postId) subtitleState.track else SubtitleTrack.EMPTY

    // 本曲是否就是播放器当前曲：不是则本面板呈现"待播放"，按钮点击才真正装载队列
    val isCurrent = state.track?.postId == track.postId && state.track?.url == track.url
    val displayCover = coverUrl?.takeIf { it.isNotEmpty() } ?: track.coverUrl

    var speedMenuOpen by remember { mutableStateOf(false) }
    // 拖动中的本地回显值，避免被轮询进度顶回
    var draggingValue by remember { mutableStateOf<Float?>(null) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .background(DarkBackground)
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        // 音频类作品的站点缩略图原始分辨率普遍偏低（多为 200/300px），详情页按屏宽
        // 比例放大铺满会明显发虚。这里把封面固定为较小尺寸，并按显示像素向 CDN
        // 协商更高档位（DynamicThumbnail），从源头减少放大损耗。
        val coverSize = 180.dp
        val coverPx = with(LocalDensity.current) { coverSize.toPx() }
        val coverModel = displayCover
            ?.takeIf { it.isNotEmpty() }
            ?.let { DynamicThumbnail(it).bestThumbnailUrl(coverPx, coverPx) }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(coverSize)
                .clip(RoundedCornerShape(24.dp))
                .background(DarkSurfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
        ) {
            if (coverModel != null) {
                AsyncImage(
                    model = coverModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = SubtleGray,
                    modifier = Modifier.size(64.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = track.title.ifEmpty { untitledLabel },
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        if (track.artist.isNotEmpty()) {
            Text(
                text = track.artist,
                fontSize = 12.sp,
                color = SubtleGray,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 传输控制：上一首 / 播放暂停 / 下一首 ─────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = { PlaybackController.previous() },
                enabled = isCurrent && state.hasPrevious
            ) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = stringResource(R.string.player_previous),
                    tint = if (isCurrent && state.hasPrevious) Color.White else SubtleGray.copy(alpha = 0.4f)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            FilledIconButton(
                onClick = {
                    when {
                        !isCurrent -> PlaybackController.play(context, track, queue)
                        state.failed -> PlaybackController.retry(context)
                        else -> PlaybackController.togglePlayPause()
                    }
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color(0xFF0A0A0A)
                ),
                modifier = Modifier.size(72.dp)
            ) {
                when {
                    !isCurrent -> Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(36.dp))
                    state.failed -> Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(36.dp))
                    state.isBuffering -> CircularProgressIndicator(
                        modifier = Modifier.size(30.dp),
                        color = Color(0xFF0A0A0A),
                        strokeWidth = 3.dp
                    )
                    state.isPlaying -> Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(36.dp))
                    else -> Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(36.dp))
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            IconButton(
                onClick = { PlaybackController.next() },
                enabled = isCurrent && state.hasNext
            ) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = stringResource(R.string.player_next),
                    tint = if (isCurrent && state.hasNext) Color.White else SubtleGray.copy(alpha = 0.4f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── 进度 ────────────────────────────────────────────────────────
        val durationMs = if (isCurrent) state.durationMs else 0L
        val positionMs = if (isCurrent) state.positionMs else 0L
        val sliderValue = draggingValue ?: if (durationMs > 0L) (positionMs.toFloat() / durationMs) else 0f
        Slider(
            value = sliderValue.coerceIn(0f, 1f),
            onValueChange = { draggingValue = it },
            onValueChangeFinished = {
                if (isCurrent && durationMs > 0L) {
                    PlaybackController.seekTo(((draggingValue ?: 0f) * durationMs).toLong())
                }
                draggingValue = null
            },
            enabled = isCurrent && durationMs > 0L && !state.failed,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = DarkSurfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = formatAudioTime(
                    if (draggingValue != null) ((draggingValue ?: 0f) * durationMs).toLong() else positionMs
                ),
                fontSize = 12.sp,
                color = SubtleGray
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = formatAudioTime(durationMs), fontSize = 12.sp, color = SubtleGray)
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ── 单曲循环 / 倍速（变速不变调）────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerChip(
                icon = Icons.Default.RepeatOne,
                label = stringResource(R.string.player_repeat_one),
                active = isCurrent && state.repeatOne,
                enabled = isCurrent,
                onClick = { PlaybackController.setRepeatOne(!state.repeatOne) }
            )

            Spacer(modifier = Modifier.width(10.dp))

            Box {
                PlayerChip(
                    icon = null,
                    label = stringResource(R.string.player_speed_value, formatSpeed(state.speed)),
                    active = state.speed != 1f,
                    enabled = true,
                    onClick = { speedMenuOpen = true }
                )
                DropdownMenu(
                    expanded = speedMenuOpen,
                    onDismissRequest = { speedMenuOpen = false }
                ) {
                    PlaybackController.SPEED_PRESETS.forEach { preset ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.player_speed_value, formatSpeed(preset)),
                                    fontWeight = if (preset == state.speed) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                PlaybackController.setSpeed(preset)
                                speedMenuOpen = false
                            }
                        )
                    }
                }
            }
        }

        // ── 流元数据：编解码器 / 采样率 / 比特率 / 声道 ───────────────────
        if (isCurrent && !state.stream.isEmpty) {
            Spacer(modifier = Modifier.height(14.dp))
            // 文案先无条件取好：stringResource 不放进 ?.let / buildList 这类非组合 lambda，
            // 免踩"@Composable 调用不在组合上下文"的编译错误
            val sampleRate = state.stream.sampleRateHz
            val bitrate = state.stream.bitrateBps
            val channels = state.stream.channelCount
            val sampleRateLabel = if (sampleRate != null) {
                stringResource(R.string.player_stream_sample_rate_value, formatKHz(sampleRate))
            } else null
            val bitrateLabel = if (bitrate != null) {
                stringResource(R.string.player_stream_bitrate_value, bitrate / 1000)
            } else null
            val channelLabel = when {
                channels == null -> null
                channels <= 1 -> stringResource(R.string.player_stream_channel_mono)
                channels == 2 -> stringResource(R.string.player_stream_channel_stereo)
                else -> stringResource(R.string.player_stream_channel_count, channels)
            }
            val parts = listOfNotNull(
                state.stream.codec,
                sampleRateLabel,
                bitrateLabel,
                channelLabel
            )
            if (parts.isNotEmpty()) {
                Text(
                    text = parts.joinToString(" · "),
                    fontSize = 11.sp,
                    color = SubtleGray,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ── 字幕（AI 转录）─────────────────────────────────────────────
        Spacer(modifier = Modifier.height(14.dp))
        val subtitleRequest = SubtitleRequest(
            postId = track.postId,
            title = track.title,
            description = track.description,
            audioUrl = track.url
        )
        if (!subtitleTrack.isEmpty) {
            SubtitlePanel(
                track = subtitleTrack,
                positionMs = if (isCurrent) state.positionMs else 0L,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        val subtitleError = subtitleState.errorKind
        when {
            subtitleState.generating -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.subtitle_generating),
                    fontSize = 12.sp,
                    color = SubtleGray
                )
            }

            subtitleError != null -> Text(
                text = transcriptionErrorText(subtitleError),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth()
            )

            subtitleTrack.isEmpty -> if (transcriptionConfig.isConfigured) {
                TextButton(onClick = { SubtitleController.generate(context, subtitleRequest) }) {
                    Text(text = stringResource(R.string.subtitle_generate), fontSize = 12.sp)
                }
            } else {
                Text(
                    text = stringResource(R.string.subtitle_hint_not_configured),
                    fontSize = 12.sp,
                    color = SubtleGray,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            else -> TextButton(
                onClick = { SubtitleController.generate(context, subtitleRequest, forceRefresh = true) }
            ) {
                Text(text = stringResource(R.string.subtitle_regenerate), fontSize = 12.sp)
            }
        }

        if (isCurrent && state.failed) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.player_error),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 0.5 → "0.5"，1.0 → "1"，1.25 → "1.25"：去掉无意义的尾随零。 */
private fun formatSpeed(speed: Float): String {
    val rounded = (speed * 100f).toInt() / 100f
    return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
}

/** 播放器上的胶囊按钮（循环/倍速）：激活态用主色描边。 */
@Composable
private fun PlayerChip(
    icon: ImageVector?,
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> SubtleGray.copy(alpha = 0.4f)
        active -> MaterialTheme.colorScheme.primary
        else -> SubtleGray
    }
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else DarkSurfaceVariant,
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text = label, fontSize = 12.sp, color = tint)
    }
}
