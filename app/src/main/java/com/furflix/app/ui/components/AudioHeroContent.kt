package com.furflix.app.ui.components

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.furflix.app.ui.theme.DarkBackground
import com.furflix.app.ui.theme.DarkSurfaceVariant
import com.furflix.app.ui.theme.SubtleGray
import kotlinx.coroutines.delay

private fun formatAudioTime(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

/**
 * Music/音频作品的主媒体区（FA 详情页为 <audio> + Download 直链，无图可渲）。
 * 站点缩略图作封面，内嵌 MediaPlayer 提供播放/暂停/拖动进度；错误态可点重试。
 */
@Composable
fun AudioHeroContent(
    audioUrl: String,
    coverUrl: String?,
    modifier: Modifier = Modifier
) {
    var retryKey by remember { mutableIntStateOf(0) }
    var prepared by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var durationMs by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableIntStateOf(0) }
    // 拖动中的本地回显值，避免被轮询进度顶回
    var draggingValue by remember { mutableStateOf<Float?>(null) }

    val player = remember(audioUrl, retryKey) {
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setOnPreparedListener {
                prepared = true
                durationMs = it.duration
            }
            setOnCompletionListener {
                playing = false
                positionMs = 0
            }
            setOnErrorListener { _, what, extra ->
                failed = true
                playing = false
                android.util.Log.w("AudioHero", "player error what=$what extra=$extra")
                true
            }
            runCatching {
                setDataSource(audioUrl)
                setScreenOnWhilePlaying(true)
                prepareAsync()
            }.onFailure {
                failed = true
            }
        }
    }

    DisposableEffect(audioUrl, retryKey) {
        onDispose {
            runCatching { player.stop() }
            player.release()
        }
    }

    // 播放中轮询进度（MediaPlayer 无进度回调）
    LaunchedEffect(playing, prepared) {
        while (playing && prepared) {
            positionMs = runCatching { player.currentPosition }.getOrDefault(positionMs)
            delay(400)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .background(DarkBackground)
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(DarkSurfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
        ) {
            if (!coverUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = coverUrl,
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

        Spacer(modifier = Modifier.height(24.dp))

        FilledIconButton(
            onClick = {
                if (failed) {
                    failed = false
                    prepared = false
                    retryKey++
                    return@FilledIconButton
                }
                if (!prepared) return@FilledIconButton
                if (playing) {
                    player.pause()
                    playing = false
                } else {
                    player.start()
                    playing = true
                }
            },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color(0xFF0A0A0A)
            ),
            modifier = Modifier.size(72.dp)
        ) {
            when {
                failed -> Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(36.dp))
                !prepared -> CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    color = Color(0xFF0A0A0A),
                    strokeWidth = 3.dp
                )
                playing -> Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(36.dp))
                else -> Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(36.dp))
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        val sliderValue = draggingValue ?: (positionMs.toFloat() / durationMs.coerceAtLeast(1))
        Slider(
            value = sliderValue.coerceIn(0f, 1f),
            onValueChange = { draggingValue = it },
            onValueChangeFinished = {
                if (prepared) {
                    val target = ((draggingValue ?: 0f) * durationMs).toInt()
                    runCatching { player.seekTo(target) }
                    positionMs = target
                }
                draggingValue = null
            },
            enabled = prepared && !failed,
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
                text = formatAudioTime(if (draggingValue != null) (draggingValue!! * durationMs).toInt() else positionMs),
                fontSize = 12.sp,
                color = SubtleGray
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatAudioTime(durationMs),
                fontSize = 12.sp,
                color = SubtleGray
            )
        }
    }
}
