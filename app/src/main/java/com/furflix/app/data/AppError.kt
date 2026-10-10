package com.furflix.app.data

/**
 * 应用级错误分类(ADR-0006 的接缝,REFACTOR-011)。
 *
 * ## 现状
 *  repositories 与抓取层用裸 `Exception` / `runCatching` 表达失败,
 *  UI 侧只能拿到一句 `e.message`,既无法分类也无法给出可操作的提示。
 *  更糟的是若干处直接 `catch (_: Exception) { emptyList() }` ——
 *  「解析失败」与「真的没有数据」在 UI 上长得一样。
 *
 * ## 本类
 *  只做**分类与归因**,不改动任何既有调用点(避免多米诺式回归)。
 *  新增代码应抛/返回 [AppError];既有裸异常可经 [from] 归类后使用。
 *  阶段 4 后续批次再逐步把 repositories 的错误出口迁到本类。
 */
sealed class AppError(
    /** 可直接呈现给用户的原因分类(由 UI 层决定具体文案与是否展示)。 */
    open val cause: Throwable? = null
) {

    /** 网络不可达 / 超时 / DNS。可重试。 */
    data class Network(override val cause: Throwable? = null) : AppError(cause)

    /** 服务端返回非 2xx(如 403/404/429)。[code] 用于区分鉴权与限流。 */
    data class Http(val code: Int, override val cause: Throwable? = null) : AppError(cause)

    /** Cloudflare 挑战未通过:需用户完成验证后重试。 */
    data class Cloudflare(val pageUrl: String, override val cause: Throwable? = null) :
        AppError(cause)

    /** 访客被门页拦截(仅注册用户可见)。 */
    data class GuestBlocked(override val cause: Throwable? = null) : AppError(cause)

    /** 会话失效:需重新登录。 */
    data class Unauthorized(override val cause: Throwable? = null) : AppError(cause)

    /** HTML 结构未按预期解析(站点改版)。不可重试,应降级而非崩溃。 */
    data class Parse(override val cause: Throwable? = null) : AppError(cause)

    /** 本地存储 / DataStore / MediaStore 失败。 */
    data class Storage(override val cause: Throwable? = null) : AppError(cause)

    /** 配额不足(缓存上限等)。 */
    data class Quota(override val cause: Throwable? = null) : AppError(cause)

    /** 未归类。保留原始异常以便排查。 */
    data class Unknown(override val cause: Throwable? = null) : AppError(cause)

    /** 是否值得自动重试:网络与 5xx 可重试,解析/鉴权不可。 */
    val retryable: Boolean
        get() = when (this) {
            is Network -> true
            is Http -> code >= 500 || code == 429
            is Cloudflare -> true      // 用户完成验证后重试一次
            is GuestBlocked -> false
            is Unauthorized -> false
            is Parse -> false
            is Storage -> false
            is Quota -> false
            is Unknown -> false
        }

    /** 是否需要用户交互才能继续(验证 / 重新登录)。 */
    val needsUserAction: Boolean
        get() = this is Cloudflare || this is Unauthorized || this is GuestBlocked

    companion object {
        /**
         * 把裸异常归类为 [AppError]:预先识别项目已有的异常类型,
         * 其余按名字/消息做保守猜测,猜不出即 [Unknown](不吞信息)。
         */
        fun from(t: Throwable): AppError = when {
            t is AppError -> t
            t is com.furflix.app.data.remote.CloudflareChallengeException ->
                Cloudflare(t.pageUrl, t)
            t is com.furflix.app.data.remote.GuestBlockedException -> GuestBlocked(t)
            t is java.net.SocketTimeoutException -> Network(t)
            t is java.net.UnknownHostException -> Network(t)
            t is java.io.IOException -> Network(t)
            else -> {
                val msg = t.message.orEmpty()
                when {
                    msg.startsWith("HTTP ", ignoreCase = true) -> {
                        val code = msg.removePrefix("HTTP ").trim().takeWhile { it.isDigit() }
                            .toIntOrNull()
                        if (code != null) {
                            if (code == 401 || code == 403) Unauthorized(t) else Http(code, t)
                        } else {
                            Unknown(t)
                        }
                    }
                    else -> Unknown(t)
                }
            }
        }
    }
}
