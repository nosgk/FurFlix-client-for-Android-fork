package com.furflix.app.data.remote

import android.content.Context
import android.webkit.WebSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全链路 UA 单一来源（2026-10-08 CF 循环验证事故定案 + 实时更新版）。
 *
 * 全部出站链路——OkHttp 抓取 / Coil 图片 / 下载 / 登录与验证 WebView——统一使用
 * 设备**真实 WebView UA**：cf_clearance 被 CF 按"签发它的 UA"绑定校验，验证
 * WebView（原生 UA）拿到的 clearance 只有在同 UA 的抓取链路上才有效。
 *
 * 实时更新设计：
 *  - UA 以 [MutableStateFlow] 持有，[update] 可随时注入新值（如登录/验证
 *    WebView 每次创建时回写其运行时 UA），后续请求立即生效；
 *  - 请求头（User-Agent 与 FurHeaders 的 Client Hints）在每次构建请求时
 *    实时读取 [current]，不存在缓存过期问题；
 *  - Cookie 侧由 getCookiesString 每次调用实时读系统 CookieManager 补
 *    cf_clearance 等 CF 系 cookie（92cdde0 起的行为，保持不变）。
 *
 * 回落 UA 锁定 Android WebView 形态（JVM 单测/极端时序），桌面形态是
 * 2026-10-08 事故根因，UserAgentTest 防其回流。
 */
object UserAgent {

    /** init 前的回落值：Android WebView 形态的移动 UA */
    private const val FALLBACK =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Version/4.0 Chrome/131.0.0.0 Mobile Safari/537.36"

    private val _ua = MutableStateFlow(FALLBACK)

    /** 当前生效 UA（可观察；网络层取 current()） */
    val current: StateFlow<String> = _ua.asStateFlow()

    /** Application 创建时调用：取设备 WebView 的真实 UA 作为初始值 */
    fun init(context: Context) {
        runCatching { WebSettings.getDefaultUserAgent(context) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { update(it) }
    }

    /** 实时更新 UA：登录/验证 WebView 回写运行时 UA 等场景，后续请求立即生效 */
    fun update(ua: String) {
        if (ua.isNotBlank()) _ua.value = ua
    }

    /** 网络请求使用的 UA：设备真实 WebView UA */
    fun current(): String = current.value

    /** 从当前 UA 解析 Chromium 主版本（Client Hints 与其保持自洽） */
    fun chromiumMajorVersion(): Int =
        Regex("""Chrome/(\d+)""").find(current())?.groupValues?.get(1)?.toIntOrNull() ?: 131

    /** 低熵 Client Hint 品牌三元组（与 [current] 版本/平台自洽，Android WebView 形态） */
    fun secChUa(): String {
        val v = chromiumMajorVersion()
        return "\"Not_A Brand\";v=\"24\", \"Chromium\";v=\"$v\", \"Android WebView\";v=\"$v\""
    }
}
