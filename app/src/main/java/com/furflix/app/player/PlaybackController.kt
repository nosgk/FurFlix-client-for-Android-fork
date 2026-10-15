package com.furflix.app.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 进程级音频播放器（ExoPlayer 单例）与对外状态。
 *
 * 设计要点：
 *  - **单例、进程级生命周期**：详情页播放器、全局迷你条、后台 [PlaybackService]
 *    共享同一个 ExoPlayer。谁都不 release（服务销毁也不 release），避免"某处释放
 *    后别处还在引用"的悬空播放器；进程结束由系统回收。
 *  - **必须在主线程创建**：ExoPlayer 绑定创建线程的 Looper。详情页/服务都在主线程
 *    调用 [player]，Compose 侧用 [play] 间接创建。
 *  - **音质**：走系统 MediaCodec 硬解；未开启任何重采样 AudioProcessor，AudioTrack
 *    按源采样率配置（默认即"直出"）。变速用 [PlaybackParameters]（Sonic），只改时长
 *    不改音高，即"变速不变调"。
 *  - **网络**：ExoPlayer 自带 DefaultHttpDataSource，不经 App 的 OkHttp 抓取链路，
 *    因此不受 [com.furflix.app.data.lifecycle.AppLifecycleTracker] 的前后台闸门影响，
 *    后台续播不会被拦。
 */
object PlaybackController {

    /** 倍速档位（UI 与外部共用一份）。 */
    val SPEED_PRESETS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var exo: ExoPlayer? = null
    private var ticker: Job? = null

    /** 应用上下文：用于拉起后台服务，避免持有 Activity 引用。 */
    private var appContext: Context? = null

    /** 与播放器内 MediaItem 顺序一一对应的队列（ExoPlayer 不保存我们的业务模型）。 */
    private val queue = mutableListOf<AudioTrack>()

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    /** 取（必要时创建）共享播放器。**主线程**调用。 */
    fun player(context: Context): ExoPlayer {
        exo?.let { return it }
        val app = context.applicationContext
        appContext = app
        val p = ExoPlayer.Builder(app).build()
        p.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true
        )
        // 拔耳机/蓝牙断开自动暂停，避免外放扰人
        p.setHandleAudioBecomingNoisy(true)
        // 后台流式播放持 WifiLock，避免息屏断流
        p.setWakeMode(C.WAKE_MODE_NETWORK)
        p.addListener(listener)
        exo = p
        return p
    }

    /**
     * 播放 [track]：以 [tracks] 为播放列表，从 [track] 所在位置开始。
     * 若该曲已是当前曲，则只切到播放态（不重启）。
     */
    fun play(context: Context, track: AudioTrack, tracks: List<AudioTrack> = emptyList()) {
        val p = player(context)
        val list = tracks.ifEmpty { listOf(track) }
        val alreadyCurrent = queue.getOrNull(p.currentMediaItemIndex)
            ?.let { it.postId == track.postId && it.url == track.url } == true
        if (alreadyCurrent) {
            p.play()
            ensureService()
            return
        }
        val index = list.indexOfFirst { it.postId == track.postId && it.url == track.url }
            .takeIf { it >= 0 } ?: 0
        queue.clear()
        queue.addAll(list)
        p.setMediaItems(list.map { it.toMediaItem() }, index, 0L)
        p.playWhenReady = true
        p.prepare()
        ensureService()
        syncTrack()
    }

    fun togglePlayPause() {
        val p = exo ?: return
        if (p.isPlaying) {
            p.pause()
        } else if (p.playbackState != Player.STATE_IDLE) {
            ensureService()
            p.play()
        }
    }

    fun seekTo(positionMs: Long) {
        exo?.seekTo(positionMs.coerceAtLeast(0L))
        syncProgress()
    }

    fun next() {
        val p = exo ?: return
        if (p.hasNextMediaItem()) {
            p.seekToNextMediaItem()
            p.play()
        }
    }

    fun previous() {
        val p = exo ?: return
        if (p.hasPreviousMediaItem()) {
            p.seekToPreviousMediaItem()
            p.play()
        }
    }

    /** 倍速播放（音高不变）。 */
    fun setSpeed(speed: Float) {
        exo?.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 4f))
    }

    /** 单曲循环开关。 */
    fun setRepeatOne(enabled: Boolean) {
        exo?.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    /** 失败后重试当前曲目。 */
    fun retry(context: Context) {
        val p = player(context)
        _state.update { it.copy(failed = false) }
        p.prepare()
    }

    /** 停止并清空队列（迷你条关闭按钮）。 */
    fun stop() {
        val p = exo ?: return
        stopTicker()
        p.stop()
        p.clearMediaItems()
        queue.clear()
        _state.value = PlaybackUiState()
    }

    /** 队列是否包含下一首/上一首（UI 显隐切歌键）。 */
    fun queueSnapshot(): List<AudioTrack> = queue.toList()

    // ── 内部 ────────────────────────────────────────────────────────────────

    private fun ensureService() {
        val ctx = appContext ?: return
        // 从可见界面（用户点播放）发起，startService 合法；服务自行在播放中升前台。
        runCatching { ctx.startService(Intent(ctx, PlaybackService::class.java)) }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) startTicker() else { stopTicker(); syncProgress() }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update {
                it.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    failed = if (playbackState == Player.STATE_IDLE) it.failed else false,
                    durationMs = exo?.duration?.coerceAtLeast(0L) ?: it.durationMs
                )
            }
            if (playbackState == Player.STATE_READY) syncProgress()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncTrack()
        }

        override fun onTracksChanged(tracks: Tracks) {
            _state.update { it.copy(stream = readStreamInfo(tracks)) }
        }

        override fun onPlayerError(error: PlaybackException) {
            android.util.Log.w("PlaybackController", "playback error: ${error.errorCodeName}", error)
            _state.update { it.copy(failed = true, isPlaying = false, isBuffering = false) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _state.update { it.copy(repeatOne = repeatMode == Player.REPEAT_MODE_ONE) }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _state.update {
                it.copy(speed = playbackParameters.speed, pitch = playbackParameters.pitch)
            }
        }
    }

    private fun syncTrack() {
        val p = exo ?: return
        val track = queue.getOrNull(p.currentMediaItemIndex)
        _state.update {
            it.copy(
                track = track,
                durationMs = p.duration.coerceAtLeast(0L),
                hasNext = p.hasNextMediaItem(),
                hasPrevious = p.hasPreviousMediaItem(),
                failed = false
            )
        }
    }

    private fun syncProgress() {
        val p = exo ?: return
        _state.update {
            it.copy(
                positionMs = p.currentPosition.coerceAtLeast(0L),
                durationMs = p.duration.coerceAtLeast(0L),
                bufferedMs = p.bufferedPosition.coerceAtLeast(0L),
                hasNext = p.hasNextMediaItem(),
                hasPrevious = p.hasPreviousMediaItem()
            )
        }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                syncProgress()
                delay(500)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    /** 从已选中的音轨 Format 读真实流参数。 */
    private fun readStreamInfo(tracks: Tracks): AudioStreamInfo {
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (i in 0 until group.length) {
                if (!group.isTrackSelected(i)) continue
                val format = group.getTrackFormat(i)
                return AudioStreamInfo(
                    codec = AudioStreamInfo.codecLabel(format.sampleMimeType),
                    sampleRateHz = format.sampleRate.takeIf { it > 0 },
                    bitrateBps = format.bitrate.takeIf { it > 0 },
                    channelCount = format.channelCount.takeIf { it > 0 }
                )
            }
        }
        return AudioStreamInfo()
    }

    private fun AudioTrack.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(postId)
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(coverUrl?.takeIf { it.isNotEmpty() }?.let(Uri::parse))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
}
