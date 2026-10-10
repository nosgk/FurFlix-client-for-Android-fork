package com.furflix.app.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.CookieManager

/**
 * FA 会话与 cookie 管理(从 FurAffinityScraper 抽出,REFACTOR-004)。
 *
 * 职责边界:
 *  - 持有登录态(`loggedIn` / `username`)与登录时的会话 cookie 快照;
 *  - 在系统 CookieManager 与抓取链路之间搬运 cookie;
 *  - 提供合并后的 Cookie 请求头(合并策略见 [CookieMerger])。
 *
 * 不负责:页面抓取、HTML 解析、分页游标 —— 那些留在 [FurAffinityScraper]。
 *
 * 为什么是 object:与 [UserAgent] / [CloudflareGate] 同为进程级单例,
 * 与系统 CookieManager 的生命周期对齐。抽出的收益是 FurAffinityScraper
 * 不再混杂会话状态,便于单独演进与替换(例如未来引入 DI)。
 */
object FurSession {

    /** 会话 cookie(按名索引)。FA 真实登录 cookie 名为 "a" 与 "b"。 */
    private var sessionCookies: MutableMap<String, String> = mutableMapOf()
    private var loggedIn = false
    private var username = ""

    private val cookieManager: CookieManager get() = CookieManager.getInstance()

    @SuppressLint("SetJavaScriptEnabled")
    fun init(context: Context) {
        CookieManager.getInstance().setAcceptCookie(true)
        if (sessionCookies.isNotEmpty()) {
            applySessionCookiesToCookieManager()
        }
    }

    fun isLoggedIn(): Boolean = loggedIn

    fun getUsername(): String = username

    fun setUsername(name: String) {
        username = name
    }

    /** 抓取链路解析出登录态后回写(供 Scraper 的登录校验路径调用)。 */
    fun markLoggedIn(name: String) {
        username = name
        loggedIn = true
    }

    /**
     * Cookie 请求头。合并策略见 [CookieMerger]:
     * CF 系 cookie 以运行时 CookieManager 为权威,会话快照仅作回落。
     *
     * @param forceNsfw true 去掉 sfw;false 注入 sfw=1;null 不动
     */
    fun getCookiesString(forceNsfw: Boolean? = null): String =
        CookieMerger.merge(
            session = sessionCookies,
            runtime = parseCm(),
            forceNsfw = forceNsfw,
        )

    /** 解析系统 CookieManager 的当前值(两个 host 合并,后者同名覆盖)。 */
    private fun parseCm(): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        listOf(
            cookieManager.getCookie("https://furaffinity.net"),
            cookieManager.getCookie("https://www.furaffinity.net")
        ).forEach { raw ->
            raw?.split(";")?.forEach { part ->
                val kv = part.trim().split("=", limit = 2)
                if (kv.size == 2 && kv[0].trim().isNotEmpty()) {
                    map[kv[0].trim()] = kv[1].trim()
                }
            }
        }
        return map
    }

    /**
     * 登录 WebView 回传 cookie 后调用:解析并存入会话快照。
     * 只有名为 "a" 的 cookie 才代表真实登录(与 iOS 端一致);
     * FCCDCF / FCNEC 等只是 Cloudflare/consent cookie,不算登录。
     */
    fun setCookiesFromWebView(cookiesString: String) {
        sessionCookies.clear()
        cookiesString.split(";").forEach { cookie ->
            val parts = cookie.trim().split("=", limit = 2)
            if (parts.size == 2) {
                val name = parts[0].trim()
                val value = parts[1].trim()
                if (name.isNotEmpty()) {
                    sessionCookies[name] = value
                }
            }
        }
        applySessionCookiesToCookieManager()

        val hasAuthCookie = sessionCookies.containsKey("a")
        loggedIn = hasAuthCookie

        Log.d(
            "FurSession",
            "Cookies set: ${sessionCookies.keys}, hasAuthCookie(a)=$hasAuthCookie, loggedIn=$loggedIn"
        )
    }

    private fun applySessionCookiesToCookieManager() {
        sessionCookies.forEach { (name, value) ->
            cookieManager.setCookie("https://furaffinity.net", "$name=$value")
            cookieManager.setCookie("https://www.furaffinity.net", "$name=$value")
        }
        cookieManager.flush()
    }

    fun clearSession() {
        sessionCookies.clear()
        loggedIn = false
        username = ""
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
    }

    /**
     * CF 验证通过后调用:清理会话快照里已被运行时新值取代的 CF 系脏键,
     * 避免重登前的旧 clearance 在任何回落路径上复活(见 K-001)。
     */
    fun dropStaleCfSessionCookies() {
        val stale = CookieMerger.staleSessionKeys(sessionCookies, parseCm())
        if (stale.isEmpty()) return
        stale.forEach { sessionCookies.remove(it) }
        Log.d("FurSession", "Dropped stale CF session cookies: $stale")
    }
}
