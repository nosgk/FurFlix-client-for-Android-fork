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
            // 画廊缩略图几乎全在 t.furaffinity.net 单一 host：OkHttp 默认每 host
            // 5 并发会让首屏互相排队，提到 12 保障当前浏览画面优先吃满带宽
            .dispatcher(okhttp3.Dispatcher().apply {
                maxRequests = 48
                maxRequestsPerHost = 12
            })
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
                    .header("Referer", "https://www.furaffinity.net/")
                    .build()

                val cookiesStr = FurAffinityScraper.getCookiesString()
                val finalRequest = if (cookiesStr.isNotEmpty()) {
                    request.newBuilder()
                        .header("Cookie", cookiesStr)
                        .build()
                } else {
                    request
                }

                chain.proceed(finalRequest)
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
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache(
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").absolutePath.toPath())
                    .maxSizePercent(0.25)
                    // FA CDN 缓存头短且多变：忽略头提高重复浏览命中率（磁盘即二级缓存，
                    // 内存缓存承担新鲜度），减少反复拉取与快速滑动的反复解码
                    .respectCacheHeaders(false)
                    .build()
            )
            // Coil 3 removed ImageLoader-level crossfade; fades are set per-request
            // via ImageRequest.Builder.crossfade(...) at the call sites.
            .build()
    }
}
