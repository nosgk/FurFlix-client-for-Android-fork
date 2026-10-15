package com.furflix.app.data.lifecycle

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * 前后台生命周期联动（ProcessLifecycleOwner 为单一事实源）：
 * - 前台：一切加载照常；
 * - 后台：图片网络层闸门关闭（内存/磁盘命中不经过网络层、不受影响），
 *   统计预热/横幅预解析等后台协程挂起等待回前台——杜绝"退到桌面还在
 *   偷偷拉流量"的隐性损耗。
 *
 * 冷启动默认按前台处理：ProcessLifecycleOwner 的 onStart 在主线程第一时间
 * 触发，此前短暂窗口内几乎没有加载发生，误伤可忽略。
 */
object AppLifecycleTracker {

    private val _isForeground = MutableStateFlow(true)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    private val foregroundLock = Object()

    fun install(app: Application) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    _isForeground.value = true
                    synchronized(foregroundLock) {
                        foregroundLock.notifyAll()
                    }
                    // 回前台即重查"已下载"登记：用户可能在文件管理器里删掉了文件，
                    // 下载状态必须随时准确（无时间窗口限制，每次回前台都实打实核对）
                    com.furflix.app.data.local.DownloadedMediaRegistry.refreshInBackground(app)
                }

                override fun onStop(owner: LifecycleOwner) {
                    _isForeground.value = false
                }
            }
        )
    }

    /**
     * OkHttp 线程用的阻塞等待：后台时在此挂起直至回前台（30s 周期醒检兜底，
     * 防 notify 丢失永久驻留）。仅网络层调用——缓存命中的请求不会走到这里。
     */
    fun blockUntilForeground() {
        if (_isForeground.value) return
        while (!_isForeground.value) {
            synchronized(foregroundLock) {
                if (!_isForeground.value) {
                    try {
                        foregroundLock.wait(30_000)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return
                    }
                }
            }
        }
    }

    /** 协程用的挂起等待：后台时挂起，回前台自动恢复（不占线程）。 */
    suspend fun suspendUntilForeground() {
        _isForeground.first { it }
    }
}
