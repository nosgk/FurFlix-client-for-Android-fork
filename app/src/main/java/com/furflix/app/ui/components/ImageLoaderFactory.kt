package com.furflix.app.ui.components

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.furflix.app.data.remote.FurAffinityScraper
import okhttp3.OkHttpClient
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
                coil3.memory.MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache(
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.25)
                    .build()
            )
            // Coil 3 removed ImageLoader-level crossfade; fades are set per-request
            // via ImageRequest.Builder.crossfade(...) at the call sites.
            .build()
    }
}
