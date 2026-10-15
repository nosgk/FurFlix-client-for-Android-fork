package com.furflix.app.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.furflix.app.MainActivity

/**
 * 后台播放服务：把进程级 [PlaybackController] 的共享播放器包进 MediaSession，
 * 由系统托管前台服务（mediaPlayback 类型）与媒体通知栏 / 锁屏控制。
 *
 * 由 [PlaybackController.play] 在用户点播放时拉起（前台发起，合法）。
 * 播放中服务自动升前台，暂停后自动降级——后台续播依赖的就是这一步。
 *
 * 播放器实例**刻意不在此 release**：它与 UI 共享，服务销毁（如暂停后被系统回收）
 * 不该连带废掉 UI 侧仍在引用的播放器。
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = PlaybackController.player(this)
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
