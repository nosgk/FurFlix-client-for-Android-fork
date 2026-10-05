package com.furflix.app.data.remote

import android.os.SystemClock
import com.furflix.app.data.download.ProgressResponseBody
import com.furflix.app.data.download.Speedometer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap

data class ImageLoadState(
    val url: String,
    val bytes: Long,
    val totalBytes: Long,
    val speedBps: Long
)

/**
 * 图片加载进度注册表：Coil 的 OkHttp fetcher 走 [ImageLoadProgressInterceptor]，
 * 每个 URL 一条热状态流；UI 只读当前渲染 URL 的条目，预取流量天然隔离。
 * 请求结束由图片组件的 onSuccess/onError 回调 [clear]。
 */
object ImageLoadProgress {

    private val flows = ConcurrentHashMap<String, MutableStateFlow<ImageLoadState?>>()
    private const val MAX_TRACKED_URLS = 96

    fun stateFor(url: String): StateFlow<ImageLoadState?> =
        flows.getOrPut(url) { MutableStateFlow(null) }.asStateFlow()

    fun begin(url: String) {
        if (flows.size > MAX_TRACKED_URLS) {
            flows.entries.removeAll { it.value.value == null }
        }
        flows.getOrPut(url) { MutableStateFlow(null) }.value = ImageLoadState(url, 0, -1, 0)
    }

    fun update(url: String, bytes: Long, totalBytes: Long, speedBps: Long) {
        val flow = flows[url] ?: return
        val prev = flow.value
        flow.value = ImageLoadState(
            url,
            bytes,
            if (totalBytes > 0) totalBytes else -1,
            if (speedBps >= 0) speedBps else (prev?.speedBps ?: 0)
        )
    }

    fun clear(url: String) {
        flows[url]?.value = null
    }
}

/**
 * 只包装图片响应（本 client 仅供 Coil 使用，GET 且 Content-Type 为 image 开头即图片请求）；
 * 回调按 120ms 节流、600ms 滑窗测速，与下载任务共用 [ProgressResponseBody]。
 */
class ImageLoadProgressInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val url = request.url.toString()
        val contentType = response.header("Content-Type") ?: ""
        if (request.method != "GET" || !contentType.startsWith("image/")) return response
        val body = response.body ?: return response

        ImageLoadProgress.begin(url)
        val speedometer = Speedometer()
        var lastFlush = 0L
        val wrapped = ProgressResponseBody(body) { totalRead, length ->
            val now = SystemClock.elapsedRealtime()
            if (now - lastFlush >= 120) {
                lastFlush = now
                val speed = speedometer.sample(totalRead, now)
                ImageLoadProgress.update(url, totalRead, length, speed ?: -1)
            }
        }
        return response.newBuilder().body(wrapped).build()
    }
}
