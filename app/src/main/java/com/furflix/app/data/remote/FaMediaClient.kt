package com.furflix.app.data.remote

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * FA 媒体文件（音频/视频直链）取流专用客户端。
 *
 * 存在的理由：`d.furaffinity.net` 的原件直链对**无会话**请求返回 403——这与
 * [com.furflix.app.data.download.DownloadManager] 里 K-002 记的是同一条站点行为。
 * 因此任何"直接按 URL 拉媒体字节"的实现（ExoPlayer 默认数据源、裸 OkHttp GET）
 * 都会失败；必须带上与抓取/图片链路同源的会话头。
 *
 * 头的取法刻意放在拦截器里而非静态默认头：Cookie 会随登录与 CF 校验变化，
 * 静态快照会过期。且只对 FA 域生效，不把会话 cookie 泄漏给第三方主机
 * （转录链路也会用它取音频，而大模型请求走的是另一套不带站点头的客户端）。
 *
 * 此客户端**不挂** AppLifecycleTracker 前后台闸门：否则退到后台的音频续播会被
 * 阻塞（媒体播放本来就要求后台可继续取流）。
 */
object FaMediaClient {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                if (!isFaHost(original.url.host)) return@addInterceptor chain.proceed(original)
                val builder = original.newBuilder()
                    .header("User-Agent", UserAgent.current())
                    .header("Referer", "https://www.furaffinity.net/")
                FurHeaders.applyMediaHints(builder)
                val cookies = runCatching { FurAffinityScraper.getCookiesString() }.getOrDefault("")
                if (cookies.isNotEmpty()) builder.header("Cookie", cookies)
                chain.proceed(builder.build())
            }
            .build()
    }

    fun client(): OkHttpClient = httpClient

    /** 取媒体字节（转录链路复用同一套头）。 */
    fun newCall(url: String): Call = httpClient.newCall(Request.Builder().url(url).get().build())

    internal fun isFaHost(host: String): Boolean {
        val h = host.lowercase()
        return h.endsWith("furaffinity.net") || h.endsWith("facdn.net")
    }
}
