package com.furflix.app.ui.components

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.furflix.app.data.remote.FurAffinityScraper
import okhttp3.OkHttpClient
import okio.Path.Companion.toPath
import java.util.concurrent.TimeUnit

object ImageLoaderFactory {

    private var imageLoader: ImageLoader? = null

    fun getInstance(context: Context): ImageLoader {
        return imageLoader ?: createLoader(context).also { imageLoader = it }
    }

    fun reset() {
        val loader = imageLoader
        imageLoader = null
        loader?.memoryCache?.clear()
        loader?.diskCache?.clear()
    }

    private fun createLoader(context: Context): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // 连接池保活对齐并发上限：快速滑动/回滚时 t.furaffinity.net 的空闲
            // 连接仍在池内，省去反复 TCP+TLS 握手（首字节可提前数百毫秒）
            .connectionPool(okhttp3.ConnectionPool(16, 5, TimeUnit.MINUTES))
            // 画廊缩略图几乎全在 t.furaffinity.net 单一 host：OkHttp 默认每 host
            // 5 并发会让首屏互相排队，提到 16 保障当前浏览画面优先吃满带宽
            .dispatcher(okhttp3.Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 16
            })
            .addInterceptor { chain ->
                // 前后台联动闸门（必须最先挂）：退到后台时一切图片网络请求在
                // 此阻塞等待回前台，杜绝后台预加载的隐性流量；内存/磁盘命中
                // 不经过网络层，离线秒开与回滚复用完全不受影响。
                com.furflix.app.data.lifecycle.AppLifecycleTracker.blockUntilForeground()
                chain.proceed(chain.request())
            }
            .addInterceptor { chain ->
                val cookiesStr = FurAffinityScraper.getCookiesString()
                val request = chain.request().newBuilder()
                    .header("User-Agent", com.furflix.app.data.remote.UserAgent.desktop())
                    .header("Referer", "https://www.furaffinity.net/")
                // 图片端 Fetch Metadata 拟真：跨站 CDN 请求的 sec-fetch-*/client hints
                // 与 UA 同源自洽，压低 CF 对图片链路的 bot 评分
                com.furflix.app.data.remote.FurHeaders.applyImageHints(request)
                if (cookiesStr.isNotEmpty()) {
                    request.header("Cookie", cookiesStr)
                }
                chain.proceed(request.build())
            }
            // 图片请求失败自动重试 3 次；同时把字节进度按 URL 汇报给 ImageLoadProgress
            .addInterceptor(com.furflix.app.data.remote.RetryInterceptor())
            .addInterceptor(com.furflix.app.data.remote.ImageLoadProgressInterceptor())
            .build()

        return ImageLoader.Builder(context)
            .components {
                // Coil 3 has no built-in network stack: register the OkHttp
                // fetcher explicitly, wired to our FA headers/cookies client.
                add(OkHttpNetworkFetcherFactory(callFactory = okHttpClient))
                // Animated avatars: GIFs need an explicit decoder
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(coil3.gif.AnimatedImageDecoder.Factory())
                } else {
                    add(coil3.gif.GifDecoder.Factory())
                }
            }
            .memoryCache {
                coil3.memory.MemoryCache.Builder()
                    // 25% → 30%：回滚已看过画面更多直接命中内存（零解码零 IO），
                    // 网格/瀑布流反复扫动的场景收益明显
                    .maxSizePercent(context, 0.30)
                    .build()
            }
            .diskCache(
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").absolutePath.toPath())
                    .maxSizePercent(0.25)
                    // Coil 3 网络层默认即不尊重 Cache-Control 头（新鲜度由内存缓存承担），
                    // 重复浏览直接命中磁盘缓存：减少反复拉取与快速滑动的反复解码
                    .build()
            )
            // Coil 3 removed ImageLoader-level crossfade; fades are set per-request
            // via ImageRequest.Builder.crossfade(...) at the call sites.
            .build()
    }
}
