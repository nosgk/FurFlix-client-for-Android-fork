package com.furflix.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.furflix.app.R
import com.furflix.app.player.AudioTrack
import com.furflix.app.player.PlaybackController
import com.furflix.app.ui.theme.DarkSurfaceVariant
import com.furflix.app.ui.theme.SubtleGray
import kotlin.math.roundToInt

/** 迷你条距屏幕底部的基准偏移（悬浮于底栏之上）。 */
private val MINI_PLAYER_BOTTOM_INSET = 96.dp

/**
 * 应用内全局可拖动迷你播放器。
 *
 * 只要 [PlaybackController] 有当前曲目就悬浮显示，跨所有路由存活（挂在 NavGraph 的
 * NavHost 之上）——这正是"退出详情页音频继续播、随时可切歌"的可见入口。
 * 卡片整体可拖动（拖拽与点击互不干扰：未超过 touch slop 的抬手仍算点击），
 * 点信息区跳回该作品详情页，点 X 停止并清空队列。
 */
@Composable
fun MiniPlayerOverlay(
    onOpenTrack: (AudioTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by PlaybackController.state.collectAsState()
    val track = state.track ?: return
    // 文案先取好：不放进 ifEmpty 这类非组合 lambda，免踩组合上下文编译错误
    val untitledLabel = stringResource(R.string.player_untitled)
    val unknownArtistLabel = stringResource(R.string.player_unknown_artist)

    // Android 13+ 媒体通知需运行时授权：迷你条首次出现（=首次播放）时请求一次
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }
        LaunchedEffect(Unit) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val containerW = with(density) { maxWidth.toPx() }
        val containerH = with(density) { maxHeight.toPx() }
        val bottomInsetPx = with(density) { MINI_PLAYER_BOTTOM_INSET.toPx() }

        // 拖动位移（相对底部居中的基准位）
        var offset by remember { mutableStateOf(Offset.Zero) }
        var cardW by remember { mutableFloatStateOf(0f) }
        var cardH by remember { mutableFloatStateOf(0f) }

        // 拖动闭包只建一次，尺寸经 rememberUpdatedState 保持实时，避免旋转后钳制失真
        val containerWState = rememberUpdatedState(containerW)
        val containerHState = rememberUpdatedState(containerH)
        val bottomInsetState = rememberUpdatedState(bottomInsetPx)
        val cardWState = rememberUpdatedState(cardW)
        val cardHState = rememberUpdatedState(cardH)

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = MINI_PLAYER_BOTTOM_INSET)
                .padding(horizontal = 8.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth(0.96f)
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .onGloballyPositioned {
                    cardW = it.size.width.toFloat()
                    cardH = it.size.height.toFloat()
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val maxX = ((containerWState.value - cardWState.value) / 2f).coerceAtLeast(0f)
                        val minY = -(containerHState.value - cardHState.value - bottomInsetState.value)
                            .coerceAtLeast(0f)
                        offset = Offset(
                            (offset.x + drag.x).coerceIn(-maxX, maxX),
                            (offset.y + drag.y).coerceIn(minY, bottomInsetState.value)
                        )
                    }
                }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 6.dp)
                ) {
                    // 信息区：点击跳回作品详情
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenTrack(track) }
                            .padding(2.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E1E1E))
                        ) {
                            val cover = track.coverUrl
                            if (!cover.isNullOrEmpty()) {
                                AsyncImage(
                                    model = cover,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = SubtleGray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title.ifEmpty { untitledLabel },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist.ifEmpty { unknownArtistLabel },
                                fontSize = 11.sp,
                                color = SubtleGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = { PlaybackController.previous() },
                        enabled = state.hasPrevious
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = stringResource(R.string.player_previous),
                            tint = if (state.hasPrevious) Color.White else SubtleGray.copy(alpha = 0.4f)
                        )
                    }

                    IconButton(onClick = { PlaybackController.togglePlayPause() }) {
                        when {
                            state.isBuffering -> CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                            state.isPlaying -> Icon(
                                Icons.Default.Pause,
                                contentDescription = stringResource(R.string.player_pause),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            else -> Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.player_play),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(
                        onClick = { PlaybackController.next() },
                        enabled = state.hasNext
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = stringResource(R.string.player_next),
                            tint = if (state.hasNext) Color.White else SubtleGray.copy(alpha = 0.4f)
                        )
                    }

                    IconButton(onClick = { PlaybackController.stop() }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.player_close),
                            tint = SubtleGray
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color(0xFF1E1E1E)
                )
            }
        }
    }
}
