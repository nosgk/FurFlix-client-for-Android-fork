package com.furflix.app.data.remote

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * CF 拦截特征判定（纯函数，供单测）。特征以挑战页独有内容为准：
 * 标题 "Just a moment"/"Checking your browser"、内联挑战变量 _cf_chl_opt、
 * /cdn-cgi/challenge-platform/ 路径脚本、cf-error-details 错误页。
 * 注意不能拿 challenges.cloudflare.com/turnstile/api.js 当特征——
 * FA 登录页正常状态就带 Turnstile 组件（2026-10-07 实测），会误判。
 */
fun isCloudflareChallenge(status: Int, html: String): Boolean {
    if (html.length < 200) return false
    val lower = html.lowercase()
    return lower.contains("just a moment") ||
        lower.contains("checking your browser") ||
        lower.contains("_cf_chl_opt") ||
        lower.contains("/cdn-cgi/challenge-platform/") ||
        lower.contains("cf-error-details")
}

/** 抓取层命中 CF 拦截时抛出；携带被拦页 URL 供验证对话框加载 */
class CloudflareChallengeException(val pageUrl: String) :
    Exception("Cloudflare challenge: $pageUrl")

/**
 * CF 验证处理模块：抓取链路命中拦截 → [request] 弹出全局验证请求 →
 * UI 层弹出验证对话框（真实 WebView 人工通过）→ [resolve] 回执 →
 * [awaitResolution] 恢复、抓取协程自动重试一次。
 * 验证 cookie 由 WebView 落入系统 CookieManager，getCookiesString 合并读取，
 * OkHttp 链路无需显式搬运；并发命中的多个请求共享同一次验证结果。
 */
object CloudflareGate {

    /** 用户手动完成验证的等待上限；超时按未通过处理，请求如实报错 */
    private const val AWAIT_TIMEOUT_MS = 180_000L

    private val _challengeUrl = MutableStateFlow<String?>(null)

    /** 非空 = 存在待完成的验证（UI 订阅弹全屏验证对话框） */
    val challengeUrl: StateFlow<String?> = _challengeUrl.asStateFlow()

    /** 验证回执代次：每次 resolve/cancel 自增；等待方以代次推进为信号 */
    @Volatile
    private var resolutionSeq = 0L

    /** 最近一次回执是否为用户真实通过（resolve=true / cancel=false） */
    @Volatile
    private var lastResolutionPassed = false

    /** 抓取协程报告命中拦截；已有待处理验证时保持首个（幂等） */
    fun request(url: String) {
        synchronized(this) {
            if (_challengeUrl.value == null) {
                _challengeUrl.value = url
            }
        }
    }

    /** UI 在验证通过（页面离开挑战态、cookie 已落 CookieManager）后回执 */
    fun resolve() {
        synchronized(this) {
            lastResolutionPassed = true
            resolutionSeq++
            _challengeUrl.value = null
        }
    }

    /** 用户放弃验证（关闭对话框）：等待方恢复但按未通过处理，不自动重试 */
    fun cancel() {
        synchronized(this) {
            lastResolutionPassed = false
            resolutionSeq++
            _challengeUrl.value = null
        }
    }

    /**
     * 挂起等待验证回执。进入时记下当前代次，回执代次推进后返回：
     * true = 用户通过（可重试一次）；false = 放弃；null（超时）= 未通过。
     */
    suspend fun awaitResolution(): Boolean {
        val enteredSeq = synchronized(this) { resolutionSeq }
        return withTimeoutOrNull(AWAIT_TIMEOUT_MS) {
            while (synchronized(this@CloudflareGate) { resolutionSeq } == enteredSeq) {
                delay(200)
            }
            synchronized(this@CloudflareGate) { lastResolutionPassed }
        } ?: false
    }
}
