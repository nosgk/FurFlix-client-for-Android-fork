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
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.furflix.app.data.remote.FaMediaClient
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
 *
 * `Tracks` / `C` 等 media3 声明标了 `@UnstableApi`：本类整体接受该 opt-in。
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object PlaybackController {

    /** 倍速档位（UI 与外部共用一份）。 */
    val SPEED_PRESETS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var exo: ExoPlayer? = null
    private var ticker: Job? = null

    /** 应用上下文：用于拉起后台服务，避免持有 Activity 引用。 */
    private var appContext: Context? = null

    /** 与播放器内 MediaItem 顺序一一对应的队列（ExoPlayer 不保存我们的业务模型）。 */
    private val queue = mutableListOf<PlaylistItem>()

    /** 当前播放列表：供迷你条显示来源名，也用于"下一首"预取。 */
    @Volatile
    private var currentPlaylist: Playlist? = null

    /** 队列懒解析任务：切列表时取消上一个，避免旧列表继续往播放器里塞。 */
    private var resolveJob: Job? = null

    /** 一次最多往播放器里补多少条：画师可能有几百条音频，没必要一次全解析。 */
    private const val MAX_RESOLVED_QUEUE = 60

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    /** 取（必要时创建）共享播放器。**主线程**调用。 */
    fun player(context: Context): ExoPlayer {
        exo?.let { return it }
        val app = context.applicationContext
        appContext = app
        // 音频走磁盘缓存数据源：首次边下边播并落盘，重播零网络，中断可续传。
        // upstream 必须用带 FA 会话头的 OkHttp 数据源：d.furaffinity.net 直链对
        // 无会话请求返回 403，默认的 DefaultHttpDataSource 不带 Cookie/UA/Referer，
        // 音频必然播放失败。
        val cacheFactory = CacheDataSource.Factory()
            .setCache(AudioCache.get(app))
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(FaMediaClient.client()))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val p = ExoPlayer.Builder(app)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheFactory))
            .build()
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
        // 常驻进度轮询：不随暂停而停，否则"暂停时后台仍在缓冲"的进度会僵在暂停
        // 那一刻（缓冲进度也喂给进度条）。空闲时值不变，StateFlow 自动去重。
        startTicker()
        return p
    }

    /**
     * 播放 [track]：以 [tracks] 为播放列表，从 [track] 所在位置开始。
     * 若该曲已是当前曲，则只切到播放态（不重启）。
     */
    fun play(context: Context, track: AudioTrack, tracks: List<AudioTrack> = emptyList()) {
        val p = player(context)
        val alreadyCurrent = queue.getOrNull(p.currentMediaItemIndex)
            ?.let { it.postId == track.postId && it.url == track.url } == true
        if (alreadyCurrent) {
            p.play()
            ensureService()
            return
        }
        val list = tracks.ifEmpty { listOf(track) }
        val items = list.map {
            PlaylistItem(it.postId, it.title, it.artist, it.coverUrl, it.url, it.artistUsername)
        }
        val start = items.indexOfFirst { it.postId == track.postId && it.url == track.url }
            .takeIf { it >= 0 } ?: 0
        playPlaylist(context, Playlist(track.title, PlaylistSource.Single, items), start)
    }

    /**
     * 播放整个列表（画师 / 文件夹 / 自定义）。
     *
     * 起始项解析出来后立刻起播，其余条目在后台**按序解析并 append 进播放器队列**：
     * 这样系统通知栏的"下一首"、自动续播都沿用 ExoPlayer 原生队列语义，不必自己造
     * 一套。列表项没有直链时按 postId 抓详情解析（见 [AudioUrlResolver]）。
     */
    fun playPlaylist(
        context: Context,
        playlist: Playlist,
        startIndex: Int = 0,
        playWhenReady: Boolean = true
    ) {
        if (playlist.items.isEmpty()) return
        val p = player(context)
        val app = context.applicationContext
        val from = startIndex.coerceIn(0, playlist.items.lastIndex)
        currentPlaylist = playlist
        resolveJob?.cancel()
        queue.clear()
        p.stop()
        p.clearMediaItems()
        _state.update { it.copy(track = null) }
        resolveJob = scope.launch {
            val start = playlist.items[from]
            val startUrl = resolveItemUrl(app, start) ?: run {
                android.util.Log.w("PlaybackController", "playlist start item has no audio url: ${start.postId}")
                _state.update { it.copy(failed = true) }
                return@launch
            }
            queue.add(start.copy(url = startUrl))
            p.setMediaItem(start.toMediaItem(startUrl))
            p.playWhenReady = playWhenReady
            p.prepare()
            ensureService()
            syncTrack()
            // 其余按"起始项之后 → 起始项之前"补齐（从当前曲往后听更符合直觉）
            val order = (from + 1 until playlist.size) + (0 until from).toList()
            for (i in order) {
                if (queue.size >= MAX_RESOLVED_QUEUE) break
                val item = playlist.items[i]
                val url = resolveItemUrl(app, item) ?: continue
                queue.add(item.copy(url = url))
                p.addMediaItem(item.toMediaItem(url))
            }
        }
    }

    /** 已知直链直接用，否则按 postId 抓详情解析。 */
    private suspend fun resolveItemUrl(context: Context, item: PlaylistItem): String? =
        item.url?.takeIf { it.isNotEmpty() } ?: AudioUrlResolver.resolve(context, item.postId)

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
        resolveJob?.cancel()
        resolveJob = null
        currentPlaylist = null
        p.stop()
        p.clearMediaItems()
        queue.clear()
        _state.value = PlaybackUiState()
    }

    /** 队列快照（已解析出直链的那些）。 */
    fun queueSnapshot(): List<PlaylistItem> = queue.toList()

    // ── 内部 ────────────────────────────────────────────────────────────────

    private fun ensureService() {
        val ctx = appContext ?: return
        // 从可见界面（用户点播放）发起，startService 合法；服务自行在播放中升前台。
        runCatching { ctx.startService(Intent(ctx, PlaybackService::class.java)) }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            syncProgress()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update {
                it.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    isReady = playbackState == Player.STATE_READY,
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
        val item = queue.getOrNull(p.currentMediaItemIndex)
        _state.update {
            it.copy(
                track = item?.toAudioTrack(),
                playlistName = currentPlaylist?.name,
                durationMs = p.duration.coerceAtLeast(0L),
                hasNext = p.hasNextMediaItem(),
                hasPrevious = p.hasPreviousMediaItem(),
                isReady = p.playbackState == Player.STATE_READY,
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
                delay(200)
            }
        }
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

    private fun PlaylistItem.toAudioTrack(): AudioTrack =
        AudioTrack(postId, title, artist, coverUrl, url.orEmpty(), artistUsername)

    private fun PlaylistItem.toMediaItem(url: String): MediaItem =
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
