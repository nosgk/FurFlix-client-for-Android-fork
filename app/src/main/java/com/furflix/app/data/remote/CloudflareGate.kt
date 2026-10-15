package com.furflix.app.data.remote

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/** CF 拦截判定结果。 */
enum class CloudflareVerdict {
    /** 与 CF 无关的正常页面。 */
    NONE,

    /** 可人工通过的交互式挑战：弹验证框让用户走真实 WebView。 */
    CHALLENGE,

    /**
     * CF 封禁/限流错误页（1015 限流、1020 拒绝等）。人工验证**通不过**，
     * 若按挑战处理会把用户困在一个永远无法完成的验证界面里——必须如实报错。
     */
    BLOCK
}

/**
 * CF 拦截特征判定（纯函数，供单测）。
 *
 * 先判「可人工通过的挑战」：标题 "Just a moment"/"Checking your browser"、
 * 内联挑战变量 _cf_chl_opt、/cdn-cgi/challenge-platform/ 路径脚本——三者任一即成立
 * （FA 首页/浏览页等正常页面实测均不含这些特征，2026-10-15 浏览器复核）。
 *
 * 再判「封禁/错误页」：cf-error-details 且状态码为 4xx/5xx。这类页面弹验证框无用，
 * 只应作为限流/拒绝错误退避重试；要求非 2xx 是为了避免把正文里偶然提到该词条的
 * 正常页面误判成封禁。
 *
 * 注意不能拿 challenges.cloudflare.com/turnstile/api.js 当特征——
 * FA 登录页正常状态就带 Turnstile 组件（2026-10-07 实测），会误判。
 */
fun classifyCloudflare(status: Int, html: String): CloudflareVerdict {
    if (html.length < 200) return CloudflareVerdict.NONE
    val lower = html.lowercase()
    val isChallenge = lower.contains("just a moment") ||
        lower.contains("checking your browser") ||
        lower.contains("_cf_chl_opt") ||
        lower.contains("/cdn-cgi/challenge-platform/")
    if (isChallenge) return CloudflareVerdict.CHALLENGE
    if (status >= 400 && lower.contains("cf-error-details")) return CloudflareVerdict.BLOCK
    return CloudflareVerdict.NONE
}

/** 是否为可人工通过的 CF 挑战（验证对话框只对这种页面弹出）。 */
fun isCloudflareChallenge(status: Int, html: String): Boolean =
    classifyCloudflare(status, html) == CloudflareVerdict.CHALLENGE

/** 是否为不可通过的 CF 封禁/错误页（限流等，应退避重试而非弹验证框）。 */
fun isCloudflareBlock(status: Int, html: String): Boolean =
    classifyCloudflare(status, html) == CloudflareVerdict.BLOCK

/** 抓取层命中 CF 拦截时抛出；携带被拦页 URL 供验证对话框加载 */
class CloudflareChallengeException(val pageUrl: String) :
    Exception("Cloudflare challenge: $pageUrl")

/**
 * 抓取层命中 CF 封禁/限流错误页时抛出。与挑战不同，这类响应人工验证也通不过，
 * 不应触发验证对话框，按可重试的服务端错误处理。
 */
class CloudflareBlockedException(val pageUrl: String, val status: Int) :
    Exception("Cloudflare block (HTTP $status): $pageUrl")

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
