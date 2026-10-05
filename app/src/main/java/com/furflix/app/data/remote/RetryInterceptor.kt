package com.furflix.app.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * GET 请求自动重试：网络异常 / 5xx / 429 时按 300/600/1200ms 退避重试，
 * 最多额外重试 [maxRetries] 次。图片（Coil fetcher）与页面抓取共用。
 */
class RetryInterceptor(private val maxRetries: Int = 3) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val attempts = if (request.method == "GET") maxRetries + 1 else 1

        var response: Response? = null
        var lastError: IOException? = null
        repeat(attempts) { attempt ->
            try {
                response?.close()
                response = null
                val candidate = chain.proceed(request)
                if (attempt < attempts - 1 && (candidate.code >= 500 || candidate.code == 429)) {
                    // 可重试状态：丢掉本次响应，退避后再来
                    candidate.close()
                    Thread.sleep(BACKOFF_MS shl attempt.coerceIn(0, 2))
                    return@repeat
                }
                response = candidate
                return candidate
            } catch (e: IOException) {
                lastError = e
                if (attempt < attempts - 1) {
                    Thread.sleep(BACKOFF_MS shl attempt.coerceIn(0, 2))
                }
            }
        }
        throw lastError ?: IOException("Retry exhausted")
    }

    private companion object {
        const val BACKOFF_MS = 300L
    }
}
