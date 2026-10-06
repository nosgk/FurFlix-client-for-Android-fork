package com.furflix.app.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import com.furflix.app.data.model.Submission
import com.furflix.app.data.model.Comment
import com.furflix.app.data.model.GalleryFolder
import com.furflix.app.data.model.Journal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * FA 的“仅注册用户可见”门页：所有者把用户页/画廊/Scraps 设为登录可见，
 * 非浏览器指纹的请求未带有效会话时 FA 会返回 System Message 门页而非内容。
 * 抛出后由 ViewModel 转成“登录后可见”提示，且不得写入任何分页/文件夹缓存。
 */
class GuestBlockedException(message: String = "Guest gate page") : Exception(message)

object FurAffinityScraper {

    // Session cookies — keyed by name. Real FA auth cookies are named "a" and "b".
    private var sessionCookies: MutableMap<String, String> = mutableMapOf()
    private var loggedIn = false
    private var username = ""

    private val cookieManager = CookieManager.getInstance()

    // PC UA：FA 按 UA/指纹区分呈现，移动 UA 曾加剧“仅注册用户”门页与移动端形态分歧；
    // 改用桌面 Chrome UA 与浏览器实测样本保持一致（解析规则全部按桌面主题校准）。
    // 版本号由 UserAgent 按设备真实 WebView 内核动态解析（与登录 WebView、
    // Coil 图片端共用同一来源）：版本一致性是 Turnstile 静默签发与
    // cf_clearance 的 UA 绑定在两条链路间成立的前提。
    val USER_AGENT: String
        get() = UserAgent.desktop()

    const val FA_BASE = "https://www.furaffinity.net"

    var latestNextUrl: String? = null
    var watchlistNextUrl: String? = null
    var browseNextUrl: String? = null
    var searchNextUrl: String? = null
    
    private val galleryNextUrls = mutableMapOf<String, String>()
    private val scrapsNextUrls = mutableMapOf<String, String>()
    private val favoritesNextUrls = mutableMapOf<String, String>()

    /** 用户画廊文件夹缓存（/gallery/{user}/ 侧栏区块；键 = username） */
    private val userFoldersCache = mutableMapOf<String, List<GalleryFolder>>()
    private val journalsNextUrls = mutableMapOf<String, String>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        // 页面抓取失败自动重试 3 次（指数退避）
        .addInterceptor(com.furflix.app.data.remote.RetryInterceptor())
        .build()

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

    /**
     * Returns the Cookie header string from session cookies.
     * Only uses cookies that are actual auth/session cookies - not consent junk.
     * @param forceNsfw if true, injects sfw=0; if false, injects sfw=1; if null, leaves it alone.
     *
     * cookie 合并策略（CF 对抗关键）：有会话时以 sessionCookies 为权威，
     * 并从 CookieManager **精准补齐 Cloudflare 系 cookie**（cf_clearance 等——
     * 验证弹窗/登录 WebView 刚通过挑战拿到的通行证在这里实时落库）；
     * 不合并 FCCDCF/FCNEC 等 consent 垃圾（ Cookie 头膨胀反而恶化指纹）。
     * 无会话（访客态）时回落全量 CookieManager，维持原行为。
     */
    fun getCookiesString(forceNsfw: Boolean? = null): String {
        // CF 系 cookie 白名单：验证/挑战链路签发的通行凭证
        val cfCookieNames = setOf("cf_clearance", "__cf_bm", "__cfruid", "__cf_chl_seqn")

        fun parseCm(): Map<String, String> {
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

        val cmCookies = parseCm()
        return if (sessionCookies.isNotEmpty()) {
            val merged = LinkedHashMap<String, String>()
            merged.putAll(cmCookies.filterKeys { it in cfCookieNames })
            sessionCookies.forEach { (name, value) -> merged[name] = value }
            if (forceNsfw == true) merged.remove("sfw")
            else if (forceNsfw == false) merged["sfw"] = "1"
            merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
        } else {
            var combined = cmCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            if (forceNsfw == true) {
                combined = combined.replace(Regex("""sfw=\d+"""), "")
                    .replace(Regex(""";\s*;"""), ";").trim(';', ' ')
            } else if (forceNsfw == false) {
                combined = combined.replace(Regex("""sfw=\d+"""), "").trim(';', ' ')
                if (combined.isNotEmpty()) combined += "; "
                combined += "sfw=1"
            }
            combined
        }
    }

    /**
     * Called after WebView login. Parses and stores cookies.
     * Key insight from iOS: only cookie named "a" (and optionally "b") signals real login.
     * Cookies like FCCDCF, FCNEC are just CloudFlare/consent cookies — NOT auth.
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

        // Real FA session requires the "a" cookie — just like the iOS app checks:
        // guard cookies.map(\.name).contains("a") else { return nil }
        val hasAuthCookie = sessionCookies.containsKey("a")
        loggedIn = hasAuthCookie

        Log.d(
            "FurScraper",
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

        private suspend fun postDocument(url: String, formBody: okhttp3.FormBody, forceNsfw: Boolean? = null): org.jsoup.nodes.Document =
            withCfGuard(url) {
                withContext(Dispatchers.IO) {
                    Log.d("FurScraper", "POSTing to: $url")
                    val cookieHeader = getCookiesString(forceNsfw)
                    val reqBuilder = Request.Builder()
                        .url(url)
                        .post(formBody)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.5")
                        .header("Referer", "$FA_BASE/")
                        .header("Connection", "keep-alive")
                    FurHeaders.applyFormHints(reqBuilder)

                    if (cookieHeader.isNotEmpty()) {
                        reqBuilder.header("Cookie", cookieHeader)
                    }

                    val response = httpClient.newCall(reqBuilder.build()).execute()
                    val html = response.body.string()
                    val finalUrl = response.request.url.toString()

                    Log.d("FurScraper", "POST Status=${response.code}, finalUrl=$finalUrl, len=${html.length}")
                    if (isCloudflareChallenge(response.code, html)) {
                        throw CloudflareChallengeException(finalUrl)
                    }
                    Jsoup.parse(html, finalUrl)
                }
            }

    private suspend fun fetchDocument(url: String, forceNsfw: Boolean? = null): org.jsoup.nodes.Document =
        withCfGuard(url) {
            withContext(Dispatchers.IO) {
                Log.d("FurScraper", "Fetching: $url")

                val cookieHeader = getCookiesString(forceNsfw)
                val reqBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
                    )
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .header("Referer", "$FA_BASE/")
                    .header("Connection", "keep-alive")
                FurHeaders.applyDocumentHints(reqBuilder)

                if (cookieHeader.isNotEmpty()) {
                    reqBuilder.header("Cookie", cookieHeader)
                }

                val response = httpClient.newCall(reqBuilder.build()).execute()
                val html = response.body.string()
                val finalUrl = response.request.url.toString()

                Log.d("FurScraper", "Status=${response.code}, finalUrl=$finalUrl, len=${html.length}")
                if (html.length < 1000) Log.d("FurScraper", "Short HTML: ${html.take(500)}")

                if (finalUrl.contains("/login")) {
                    Log.w("FurScraper", "Redirected to login — session cookie 'a' missing or expired")
                }

                if (isCloudflareChallenge(response.code, html)) {
                    throw CloudflareChallengeException(finalUrl)
                }

                Jsoup.parse(html, FA_BASE)
            }
        }

    /**
     * CF 验证 guard：请求命中拦截时挂起等待用户在验证对话框完成人机验证
     * （cookie 由验证 WebView 落入 CookieManager，getCookiesString 合并生效），
     * 通过后自动重试一次；用户放弃/超时则如实上抛。
     */
    private suspend fun <T> withCfGuard(url: String, block: suspend () -> T): T {
        try {
            return block()
        } catch (e: CloudflareChallengeException) {
            Log.w("FurScraper", "CF challenge at $url — requesting user verification")
            CloudflareGate.request(e.pageUrl)
            val passed = CloudflareGate.awaitResolution()
            if (!passed) {
                Log.w("FurScraper", "CF verification not completed for $url")
                throw e
            }
            Log.d("FurScraper", "CF verification passed — retrying $url")
            return block()
        }
    }

    /**
     * After successful WebView login, verify the session by fetching the home page
     * and extracting the logged-in username — same as iOS does in OnlineFASession.init().
     */
    suspend fun verifyLoginAndGetUsername(): String? = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument("$FA_BASE/")

            // Most reliable source: the logged-in avatar link, whose href is always
            // "/user/{canonical-lowercase-username}/" — its <img> has no useful text,
            // so we must read the href, not .text(). This avoids accidentally picking up
            // a menu link whose visible text is something generic like "Logged in".
            val avatarLink = doc.selectFirst("a.loggedin_user_avatar, a:has(img.loggedin_user_avatar)")
            var name = avatarLink?.attr("href")?.trim('/')?.removePrefix("user/")?.substringBefore("/")

            if (name.isNullOrEmpty()) {
                // Fallback: nav-user-menu / navhideonmobile links, but again prefer href over text
                // since these can also wrap icons or generic labels.
                val usernameEl = doc.selectFirst(
                    ".nav-user-menu a[href^=\"/user/\"], ul.navhideonmobile a[href^=\"/user/\"]"
                )
                name = usernameEl?.attr("href")?.trim('/')?.removePrefix("user/")?.substringBefore("/")
                    ?.takeIf { it.isNotEmpty() }
                    ?: usernameEl?.text()?.trim()
            }

            if (!name.isNullOrEmpty() && name != "Logged in") {
                Log.d("FurScraper", "Verified login, username=$name")
                username = name
                loggedIn = true
            } else {
                // Try alternate selector
                val alt = doc.selectFirst("a[href^=\"/user/\"]")
                val altHref = alt?.attr("href")?.trim('/')?.removePrefix("user/")?.substringBefore("/")
                val altName = altHref?.takeIf { it.isNotEmpty() } ?: alt?.text()?.trim()
                if (!altName.isNullOrEmpty() && !altName.contains(" ") && altName.length < 40) {
                    username = altName
                    loggedIn = true
                    Log.d("FurScraper", "Verified login via alt selector, username=$altName")
                } else {
                    Log.w("FurScraper", "Could not find username on home page")
                }
            }
            username.ifEmpty { null }
        } catch (e: Exception) {
            Log.e("FurScraper", "verifyLogin error: ${e.message}", e)
            null
        }
    }

    suspend fun login(user: String, pass: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val loginClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()

            val getResp = loginClient.newCall(
                Request.Builder()
                    .url("$FA_BASE/login/")
                    .header("User-Agent", USER_AGENT)
                    .header(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
                    )
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .also { FurHeaders.applyDocumentHints(it) }
                    .build()
            ).execute()
            val html = getResp.body.string()
            if (html.isEmpty()) return@withContext Result.failure(Exception("Empty response"))

            val doc = Jsoup.parse(html)
            val csrfToken = doc.selectFirst("input[name=\"csrf_token\"]")?.attr("value") ?: ""
            if (csrfToken.isEmpty()) {
                return@withContext Result.failure(Exception("Could not find login form."))
            }

            val postResp = loginClient.newCall(
                Request.Builder()
                    .url("$FA_BASE/login/")
                    .post(
                        FormBody.Builder()
                            .add("action", "login")
                            .add("retard_language", "en")
                            .add("username", user)
                            .add("password", pass)
                            .add("csrf_token", csrfToken)
                            .build()
                    )
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .header("Referer", "$FA_BASE/login/")
                    .header("Connection", "keep-alive")
                    .also { FurHeaders.applyFormHints(it) }
                    .build()
            ).execute()

            val respHtml = postResp.body.string()
            val respUrl = postResp.request.url.toString()

            if (respUrl.contains("login") &&
                (respHtml.contains("Login to access") || respHtml.contains("Invalid username"))
            ) {
                val msg = when {
                    respHtml.contains("Incorrect Password") -> "Incorrect password"
                    respHtml.contains("Invalid username") -> "Invalid username"
                    else -> "Login failed. Check your credentials."
                }
                return@withContext Result.failure(Exception(msg))
            }

            loggedIn = true
            username = user
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(Exception("Login error: ${e.message}"))
        }
    }

    // ------------------------------------------------------------------------------------------
    // Content fetching — URL structure taken directly from iOS FAURLs.swift
    // ------------------------------------------------------------------------------------------

    /**
     * New submissions feed (logged-in watchlist).
     * iOS uses: https://www.furaffinity.net/msg/submissions/new@72
     */
    suspend fun getLatestPage(page: Int = 1): List<Submission> = withContext(Dispatchers.IO) {
        try {
            // For page 1: /msg/submissions/new@72
            // For page 2+: FA uses SID-based navigation, so we use latestNextUrl
            val url = if (page == 1) "$FA_BASE/msg/submissions/new@72" else latestNextUrl ?: return@withContext emptyList()
            val doc = fetchDocument(url)

            val nextButton = doc.select("a").firstOrNull { it.text().contains("Next", ignoreCase = true) && it.attr("href").contains("~") }
            latestNextUrl = nextButton?.attr("href")?.let { "$FA_BASE$it" }

            parseMessageCenterSubmissions(doc)
        } catch (e: Exception) {
            Log.e("FurScraper", "getLatestPage error: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Browse / recent art page.
     * iOS doesn't have a "browse" feed — FA's public browse is at /browse/
     * but it returns a different HTML structure. We try it and fall back to search.
     */
    suspend fun getBrowsePage(page: Int = 1, filters: com.furflix.app.data.model.SearchFilters = com.furflix.app.data.model.SearchFilters()): List<Submission> = withContext(Dispatchers.IO) {
        try {
            val browseParams = filters.toBrowseQueryMap().map { "${it.key}=${it.value}" }.joinToString("&")
            val url = "$FA_BASE/browse/?$browseParams&page=$page&perpage=${com.furflix.app.data.SEARCH_PAGE_SIZE}"
            
            val forceNsfw = filters.ratingAdult || filters.ratingMature
            val doc = fetchDocument(url, forceNsfw)
            parseSearchResults(doc)
        } catch (e: Exception) {
            Log.e("FurScraper", "getBrowsePage error: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * 今日站点横幅：任意主页面页头 site-banner 的图片地址（每日更换，1850x300，
     * 形如 //d.furaffinity.net/media/banners/modern/20261005-eeeevio.jpg）。
     * 返回补全协议后的 https URL；拿不到返回 null（调用方静默降级）。
     */
    suspend fun fetchSiteBannerUrl(): String? = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument("$FA_BASE/")
            val src = doc.selectFirst("site-banner img")?.attr("src")
                ?: doc.selectFirst("img[usemap*=banner-map]")?.attr("src")
            src?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("//")) "https:$it" else it }
        } catch (e: Exception) {
            Log.e("FurScraper", "fetchSiteBannerUrl error: ${e.message}")
            null
        }
    }

    /**
     * 搜索（FA 原生 /search/ 表单，2026-10 实测字段全集见 SearchFilters）。
     * 异常不吞：网络失败如实上抛，由 ViewModel 呈现错误态，
     * 避免把"请求失败"误渲染成"无结果"。
     */
    suspend fun search(query: String, page: Int = 1, filters: com.furflix.app.data.model.SearchFilters = com.furflix.app.data.model.SearchFilters()): List<Submission> =
        withContext(Dispatchers.IO) {
            // Deterministic form: q/page/perpage first, then the filter map
            // in declaration order (see SearchFilters.buildSearchForm).
            val formBuilder = okhttp3.FormBody.Builder()
            filters.buildSearchForm(query, page).forEach { (key, value) ->
                formBuilder.add(key, value)
            }

            val forceNsfw = filters.ratingAdult || filters.ratingMature
            val doc = postDocument("$FA_BASE/search/", formBuilder.build(), forceNsfw)

            // Pagination is handled by passing the correct page number,
            // but we can check if a "Next" button exists to know if there are more results.
            val hasNext = doc.select("button[name=page]").any { it.text().contains("Next", ignoreCase = true) }
            if (!hasNext && doc.select("form[action^=/search/]").isNotEmpty()) {
                searchNextUrl = null
            } else {
                searchNextUrl = "has_next" // dummy value so the ViewModel knows there's more
            }

            parseSearchResults(doc)
        }

    suspend fun getSubmissionDetails(submissionId: String, loadStoryText: Boolean = true): Submission? =
        withContext(Dispatchers.IO) {
            try {
                val doc = fetchDocument("$FA_BASE/view/$submissionId/")
                val parsed = parseSubmissionDetail(doc, submissionId)
                // 小说作品：正文由页面 JS 注入，静态 HTML 里是空的——直接抓 Download
                // 指向的 .txt 原文。统计预热不加载（省流量），真实到访才拉全文。
                if (loadStoryText && parsed.fileExt == "txt" && parsed.fileUrl.isNotEmpty()) {
                    parsed.copy(storyText = fetchText(parsed.fileUrl))
                } else {
                    parsed
                }
            } catch (e: Exception) {
                Log.e("FurScraper", "getSubmissionDetails error: ${e.message}", e)
                null
            }
        }

    /** 抓取文本资源（小说 .txt 原文）；失败或超过 2MB 视为空串，阅读器回落占位态。 */
    suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        val bytes = fetchBinary(url, maxBytes = 2_000_000) ?: return@withContext ""
        decodeText(bytes)
    }

    /** 抓取二进制资源（GIF 逐帧播放需要原始字节）；失败返回 null。 */
    suspend fun fetchBinary(url: String, maxBytes: Int = 20_000_000): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Cookie", getCookiesString())
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body ?: return@withContext null
                    if ((body.contentLength()) > maxBytes) return@withContext null
                    val bytes = body.bytes()
                    if (bytes.size > maxBytes) null else bytes
                }
            } catch (e: Exception) {
                Log.e("FurScraper", "fetchBinary error: ${e.message}")
                null
            }
        }

    /** FA 文本多为 UTF-8；老上传可能是 cp1252——先按 UTF-8 严格解码，失败回落单字节。 */
    private fun decodeText(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    } catch (_: Exception) {
        String(bytes, java.nio.charset.Charset.forName("windows-1252"))
    }

    suspend fun getWatchlistPage(page: Int = 1): List<Submission> = withContext(Dispatchers.IO) {
        try {
            val url = if (page == 1) "$FA_BASE/msg/submissions/new@72" else watchlistNextUrl ?: return@withContext emptyList()
            val doc = fetchDocument(url)

            val nextButton = doc.select("a").firstOrNull { it.text().contains("Next", ignoreCase = true) && it.attr("href").contains("~") }
            watchlistNextUrl = nextButton?.attr("href")?.let { "$FA_BASE$it" }

            parseMessageCenterSubmissions(doc)
        } catch (e: Exception) {
            Log.e("FurScraper", "getWatchlistPage error: ${e.message}", e)
            emptyList()
        }
    }

    /** 下拉刷新用：清空该用户的画廊/草稿箱/收藏分页缓存，防止刷新后续页错位。 */
    fun clearUserPagination(username: String) {
        galleryNextUrls.remove(username)
        scrapsNextUrls.remove(username)
        favoritesNextUrls.remove(username)
        folderNextUrls.keys.removeAll { it.startsWith("$username:") }
    }

    private val folderNextUrls = mutableMapOf<String, String>()

    /**
     * 文件夹内容分页：page 1 用文件夹自身的站内链接，后续页走 Next 缓存
     * （键 username:folderId，与画廊/收藏分页互不覆盖）。
     */
    suspend fun getUserGalleryFolder(
        username: String,
        folder: com.furflix.app.data.model.GalleryFolder,
        page: Int = 1,
        forceNsfw: Boolean? = null
    ): List<Submission> = withContext(Dispatchers.IO) {
        try {
            val url = if (page == 1) {
                "$FA_BASE${folder.href}"
            } else {
                folderNextUrls["$username:${folder.id}"] ?: return@withContext emptyList()
            }
            val doc = fetchDocument(url, forceNsfw)
            if (isGuestGate(doc)) throw GuestBlockedException()
            val nextA = doc.select("a").firstOrNull {
                it.text().contains("Next", ignoreCase = true) && it.attr("href").contains("/gallery/", ignoreCase = true)
            }
            val key = "$username:${folder.id}"
            if (nextA != null) {
                folderNextUrls[key] = "$FA_BASE${nextA.attr("href")}"
            } else {
                folderNextUrls.remove(key)
            }
            parseSearchResults(doc)
        } catch (e: Exception) {
            Log.e("FurScraper", "getUserGalleryFolder error: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getUserGallery(username: String, page: Int = 1, forceNsfw: Boolean? = null): List<Submission> = withContext(Dispatchers.IO) {
        try {
            val url = if (page == 1) {
                "$FA_BASE/gallery/$username/"
            } else {
                galleryNextUrls[username] ?: return@withContext emptyList()
            }
            val doc = fetchDocument(url, forceNsfw)
            if (isGuestGate(doc)) throw GuestBlockedException()

            // Gallery Folders 区块就在画廊页侧栏：page 1 顺手解析并缓存，
            // 用户页的文件夹栏目零额外请求取用
            if (page == 1) {
                userFoldersCache[username.lowercase()] = parseGalleryFolders(doc)
            }

            val nextA = doc.select("a").firstOrNull { it.text().contains("Next", ignoreCase = true) && it.attr("href").contains("/gallery/", ignoreCase = true) }
            if (nextA != null) {
                galleryNextUrls[username] = "$FA_BASE${nextA.attr("href")}"
            } else {
                val nextBtn = doc.select("form[action^=/gallery/] button, form[action^=/gallery/] input").firstOrNull { (it.tagName() == "button" && it.text().contains("Next", ignoreCase = true)) || (it.tagName() == "input" && it.attr("value").contains("Next", ignoreCase = true)) }
                val form = nextBtn?.closest("form")
                val actionUrl = form?.attr("action")
                if (!actionUrl.isNullOrEmpty()) {
                    galleryNextUrls[username] = "$FA_BASE$actionUrl"
                } else {
                    galleryNextUrls.remove(username)
                }
            }
            
            parseSearchResults(doc)
        } catch (e: Exception) {
            Log.e("FurScraper", "getUserGallery error: ${e.message}", e)
            emptyList()
        }
    }

    /** 取该用户全部画廊文件夹：优先用画廊页已解析的缓存，未命中才补一次 /gallery/{user}/ 请求。 */
    suspend fun getUserGalleryFolders(username: String): List<GalleryFolder> = withContext(Dispatchers.IO) {
        userFoldersCache[username.lowercase()]?.let { return@withContext it }
        try {
            val doc = fetchDocument("$FA_BASE/gallery/$username/")
            // 门页不得写入缓存：登录后（或下次会话）应重试真实页面
            if (isGuestGate(doc)) throw GuestBlockedException()
            val folders = parseGalleryFolders(doc)
            userFoldersCache[username.lowercase()] = folders
            folders
        } catch (e: Exception) {
            Log.e("FurScraper", "getUserGalleryFolders error: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Scraps（草稿箱，/scraps/{user}/）：与主画廊并列的顶级分区，作品网格结构相同；
     * 分页 URL 为 /scraps/{user}/{page}/，Next 是 form[action^=/scraps/] 里的
     * "Next" 按钮（现代主题分页为 form+button，与画廊页同形态）。
     */
    suspend fun getUserScraps(username: String, page: Int = 1, forceNsfw: Boolean? = null): List<Submission> = withContext(Dispatchers.IO) {
        try {
            val url = if (page == 1) {
                "$FA_BASE/scraps/$username/"
            } else {
                scrapsNextUrls[username] ?: return@withContext emptyList()
            }
            val doc = fetchDocument(url, forceNsfw)
            if (isGuestGate(doc)) throw GuestBlockedException()

            val nextA = doc.select("a").firstOrNull { it.text().contains("Next", ignoreCase = true) && it.attr("href").contains("/scraps/", ignoreCase = true) }
            if (nextA != null) {
                scrapsNextUrls[username] = "$FA_BASE${nextA.attr("href")}"
            } else {
                val nextBtn = doc.select("form[action^=/scraps/] button, form[action^=/scraps/] input").firstOrNull { (it.tagName() == "button" && it.text().contains("Next", ignoreCase = true)) || (it.tagName() == "input" && it.attr("value").contains("Next", ignoreCase = true)) }
                val form = nextBtn?.closest("form")
                val actionUrl = form?.attr("action")
                if (!actionUrl.isNullOrEmpty()) {
                    scrapsNextUrls[username] = "$FA_BASE$actionUrl"
                } else {
                    scrapsNextUrls.remove(username)
                }
            }

            parseSearchResults(doc)
        } catch (e: Exception) {
            Log.e("FurScraper", "getUserScraps error: ${e.message}", e)
            emptyList()
        }
    }

    /** 门页识别：System Message 里“registered users only”是所有者隐私门的固定文案。 */
    private fun isGuestGate(doc: org.jsoup.nodes.Document): Boolean =
        doc.selectFirst(".redirect-message")?.text()
            ?.contains("registered users only", ignoreCase = true) == true

    /**
     * 解析 /gallery/{user}/ 的 Gallery Folders 区块（现代用户页已不再内嵌该列表）。
     * DOM 形态：.user-folders 直接子节点里 h4 分组标题与 <ul> 交替出现；
     * 第一个 h4 是区块自身标题（"Gallery Folders"），不算分组；
     * ul.default-group 的文件夹无分组。计数在链接 title="N submissions" 属性里。
     */
    private fun parseGalleryFolders(doc: org.jsoup.nodes.Document): List<GalleryFolder> {
        val container = doc.selectFirst(".folder-list .user-folders")
            ?: doc.selectFirst(".user-folders")
            ?: doc.selectFirst(".folder-list")
            ?: return emptyList()

        val folders = mutableListOf<GalleryFolder>()
        var group = ""
        var sawAny = false
        for (child in container.children()) {
            val h4 = child.selectFirst("h4")
            if (h4 != null && child.selectFirst("a[href*=/folder/]") == null) {
                val heading = h4.text().trim()
                if (!heading.equals("Gallery Folders", ignoreCase = true)) group = heading
                continue
            }
            // ul.default-group 是站点的“默认分组”（无组头），不应继承上一个 h4
            val effectiveGroup = if (child.hasClass("default-group")) "" else group
            for (a in child.select("a[href*=/folder/]")) {
                val folder = parseFolderAnchor(a, effectiveGroup) ?: continue
                if (folders.any { it.id == folder.id }) continue
                folders.add(folder)
                sawAny = true
            }
        }
        if (!sawAny) {
            // 未知主题形态：整页兜底扫一遍 /folder/ 链接（退化为无分组平铺）。
            // 文本本身是 URL/路径的锚点是导航残留而非文件夹，跳过
            for (a in doc.select("a[href*=/folder/]")) {
                val text = a.text().trim()
                if (text.isEmpty() || text.startsWith("/") || text.contains("://")) continue
                val folder = parseFolderAnchor(a, "") ?: continue
                if (folders.any { it.id == folder.id }) continue
                folders.add(folder)
            }
        }
        return folders
    }

    /** 单个文件夹链接：href/名称/计数（title 属性优先），[fallbackGroup] 为当前分组上下文。 */
    private fun parseFolderAnchor(a: org.jsoup.nodes.Element, fallbackGroup: String): GalleryFolder? {
        val hrefAttr = a.attr("href")
        val m = Regex("/gallery/([^/]+)/folder/(\\d+)/([^/?#]+)").find(hrefAttr) ?: return null
        val id = m.groupValues[2]
        val slug = m.groupValues[3]
        // view 页结构：<strong>分组</strong> - <span>名</span>；画廊侧栏无 strong/span，名即链接文本。
        // 文本缺失或本身就是链接形态（URL/路径）时回落 slug 解码，杜绝把链接当名字展示
        val group = a.selectFirst("strong")?.text()?.trim()
            ?.takeIf { it.isNotEmpty() } ?: fallbackGroup
        val rawName = a.selectFirst("span")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: a.text().trim()
        val name = if (rawName.isEmpty() || rawName.startsWith("/") || rawName.contains("://")) {
            runCatching {
                java.net.URLDecoder.decode(slug.replace('-', ' ').replace('_', ' '), "UTF-8")
            }.getOrDefault(slug)
        } else rawName
        val titleAttr = a.attr("title")
        val count = Regex("([\\d,]+)\\s*submissions", RegexOption.IGNORE_CASE).find(titleAttr)
            ?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
            ?: Regex("[(\\[]\\s*([\\d,]+)").find(a.text())
                ?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
            ?: 0
        return GalleryFolder(
            id = id,
            name = name,
            href = if (hrefAttr.startsWith("/")) hrefAttr else "/$hrefAttr",
            thumbUrl = a.selectFirst("img")?.attr("src") ?: "",
            count = count,
            group = group
        )
    }

    /**
     * 用户日志列表（/journals/{user}/）。
     * 条目 = div.content section[id^="jid:"]：h2 标题 + popup_date 日期 +
     * journal-body 全文（作摘要）+ section-footer 的评论数链接；
     * 分页是 form[action=/journals/{user}/{n}/] 的 "Older" 按钮（"Newer" 表单同时存在，须区分）。
     */
    suspend fun getUserJournals(username: String, page: Int = 1, forceNsfw: Boolean? = null): List<Journal> = withContext(Dispatchers.IO) {
        try {
            val url = if (page == 1) {
                "$FA_BASE/journals/$username/"
            } else {
                journalsNextUrls[username] ?: return@withContext emptyList()
            }
            val doc = fetchDocument(url, forceNsfw)

            val olderForm = doc.select("form[action*=\"/journals/\"]").firstOrNull { form ->
                (form.selectFirst("button")?.text() ?: form.selectFirst("input")?.attr("value") ?: "")
                    .contains("Older", ignoreCase = true)
            }
            val action = olderForm?.attr("action")
            if (!action.isNullOrEmpty()) {
                journalsNextUrls[username] = if (action.startsWith("http")) action else "$FA_BASE$action"
            } else {
                journalsNextUrls.remove(username)
            }

            val journals = mutableListOf<Journal>()
            for (section in doc.select("div.content section")) {
                if (!section.id().startsWith("jid:")) continue
                val id = section.id().removePrefix("jid:").trim()
                if (id.isEmpty()) continue
                val title = section.selectFirst(".section-header h2")?.text()?.trim() ?: continue
                val dateEl = section.selectFirst("span.popup_date")
                val date = dateEl?.attr("title")?.trim() ?: ""
                val timestamp = dateEl?.attr("data-time")?.toLongOrNull() ?: 0
                val rating = section.selectFirst("span[class*=c-contentRating]")?.text()?.trim() ?: ""
                val excerpt = section.selectFirst(".journal-body")?.text()?.trim()
                    ?.replace(Regex("\\s+"), " ")?.take(240) ?: ""
                val commentCount = section.selectFirst(".section-footer a span.font-large")?.text()
                    ?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 0
                journals.add(Journal(
                    id = id,
                    title = title,
                    date = date,
                    timestamp = timestamp,
                    rating = rating,
                    excerpt = excerpt,
                    commentCount = commentCount
                ))
            }
            journals
        } catch (e: Exception) {
            Log.e("FurScraper", "getUserJournals error: ${e.message}", e)
            emptyList()
        }
    }

    /** 日志详情（/journal/{id}/）：正文 + 页脚 + 评论（结构与作品评论一致，共用解析）。 */
    suspend fun getJournalDetails(journalId: String): Journal? = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument("$FA_BASE/journal/$journalId/")
            val content = doc.selectFirst("#columnpage div.content") ?: doc

            val title = content.selectFirst("#c-journalTitleTop__subject h3")?.text()?.trim()
                ?: content.selectFirst(".section-header h3")?.text()?.trim()
                ?: content.selectFirst(".section-header h2")?.text()?.trim()
                ?: "Untitled"
            val dateEl = content.selectFirst("span.popup_date")
            val rating = content.selectFirst("span[class*=c-contentRating]")?.text()?.trim() ?: ""
            val bodyEl = content.selectFirst(".journal-content") ?: content.selectFirst(".journal-body")
            bodyEl?.select("a[href^=\"/\"]")?.forEach {
                it.attr("href", "$FA_BASE${it.attr("href")}")
            }
            val body = bodyEl?.html()?.trim() ?: ""
            val footer = content.selectFirst(".journal-footer")?.text()?.trim() ?: ""
            val commentCount = content.selectFirst(".section-footer span.font-large")?.text()
                ?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 0

            // 作者信息在导航栏用户块
            val authorEl = doc.selectFirst(".username-in-nav-bar a.c-usernameBlock__displayName")
                ?: doc.selectFirst(".username-in-nav-bar a[href*=\"/user/\"]")
            val author = authorEl?.text()?.trim() ?: ""
            val authorUrl = authorEl?.attr("href") ?: ""
            val authorUsername = if (authorUrl.contains("/user/")) {
                authorUrl.substringAfter("/user/").substringBefore("/")
            } else ""
            var authorAvatar = doc.selectFirst("userpage-nav-avatar img")?.attr("src") ?: ""
            if (authorAvatar.isNotEmpty() && !authorAvatar.startsWith("http")) authorAvatar = "https:$authorAvatar"
            if (authorAvatar.isEmpty() && authorUsername.isNotEmpty()) {
                authorAvatar = "https://a.furaffinity.net/$authorUsername.gif"
            }

            Journal(
                id = journalId,
                title = title,
                date = dateEl?.attr("title")?.trim() ?: "",
                timestamp = dateEl?.attr("data-time")?.toLongOrNull() ?: 0,
                rating = rating,
                commentCount = commentCount,
                author = author,
                authorUsername = authorUsername,
                authorAvatar = authorAvatar,
                body = body,
                footer = footer,
                commentList = parseCommentsList(content)
            )
        } catch (e: Exception) {
            Log.e("FurScraper", "getJournalDetails error: ${e.message}", e)
            null
        }
    }

    suspend fun getUserFavorites(username: String, page: Int = 1, forceNsfw: Boolean? = null): List<Submission> = withContext(Dispatchers.IO) {
        try {
            val url = if (page == 1) {
                "$FA_BASE/favorites/$username/"
            } else {
                // Use cached next URL if available, otherwise fall back to page number
                favoritesNextUrls[username] ?: "$FA_BASE/favorites/$username/$page/"
            }
            Log.d("FurScraper", "getUserFavorites: page=$page url=$url username=$username")
            val doc = fetchDocument(url, forceNsfw)

            // Try anchor link with href containing /favorites/
            val nextA = doc.select("a").firstOrNull {
                it.text().contains("Next", ignoreCase = true) && it.attr("href").contains("/favorites/", ignoreCase = true)
            }
            if (nextA != null) {
                val href = nextA.attr("href")
                val nextUrl = if (href.startsWith("http")) href else "$FA_BASE$href"
                favoritesNextUrls[username] = nextUrl
                Log.d("FurScraper", "getUserFavorites: next via <a>: $nextUrl")
            } else {
                // Try form button — check both form action starts with /favorites/ and absolute URLs
                val nextBtn = doc.select("form button, form input[type=submit]").firstOrNull { btn ->
                    val parentForm = btn.closest("form")
                    val action = parentForm?.attr("action") ?: ""
                    (action.contains("/favorites/", ignoreCase = true)) &&
                    ((btn.tagName() == "button" && btn.text().contains("Next", ignoreCase = true)) ||
                     (btn.tagName() == "input" && btn.attr("value").contains("Next", ignoreCase = true))) &&
                    !btn.hasAttr("disabled")
                }
                val form = nextBtn?.closest("form")
                val actionUrl = form?.attr("action")
                if (!actionUrl.isNullOrEmpty()) {
                    val nextUrl = if (actionUrl.startsWith("http")) actionUrl else "$FA_BASE$actionUrl"
                    favoritesNextUrls[username] = nextUrl
                    Log.d("FurScraper", "getUserFavorites: next via form: $nextUrl")
                } else {
                    // No "Next" button found — clear cached URL, but don't block further loads
                    // The fallback page-number URL above will still be tried on next scroll
                    Log.d("FurScraper", "getUserFavorites: no next button found, will use page number fallback")
                    favoritesNextUrls.remove(username)
                }
            }

            val results = parseSearchResults(doc)
            Log.d("FurScraper", "getUserFavorites: page=$page parsed ${results.size} items")
            results
        } catch (e: Exception) {
            Log.e("FurScraper", "getUserFavorites error: ${e.message}", e)
            emptyList()
        }
    }


    fun getImageUrl(url: String): String {
        if (url.startsWith("http")) return url
        return "$FA_BASE$url"
    }

    // ------------------------------------------------------------------------------------------
    // Parsers — ported from iOS FASubmissionsPage.swift and FASearchPage.swift
    // ------------------------------------------------------------------------------------------

    /**
     * Parses the /msg/submissions/ page (logged-in submission feed).
     * iOS query: "figure" inside "#messagecenter-submissions section"
     * Figure id format: "sid-XXXXXX" (not "id-XXXXXX"!)
     */
    private fun parseMessageCenterSubmissions(doc: org.jsoup.nodes.Document): List<Submission> {
        val figures = doc.select(
            "#messagecenter-submissions section figure, " +
                    "#messagecenter-new-submissions figure, " +
                    "div#messagecenter-submissions figure"
        )
        Log.d("FurScraper", "parseMessageCenter: found ${figures.size} figures")

        if (figures.isEmpty()) {
            // Fallback: any figure with sid- prefix
            val fallback = doc.select("figure[id^=\"sid-\"]")
            Log.d("FurScraper", "parseMessageCenter fallback: ${fallback.size} sid- figures")
            return fallback.mapNotNull { parseFigure(it, sidPrefix = "sid-") }
        }

        return figures.mapNotNull { parseFigure(it, sidPrefix = "sid-") }
    }

    /**
     * Parses search results page.
     * iOS: doc.select("section#gallery-search-results figure")
     */
    private fun parseSearchResults(doc: org.jsoup.nodes.Document): List<Submission> {
        val figures = doc.select("section#gallery-search-results figure")
        Log.d("FurScraper", "parseSearch: found ${figures.size} figures")

        if (figures.isEmpty()) {
            // Fallback to any figure with id pattern
            val fallback = doc.select("figure[id^=\"sid-\"], figure[id^=\"id-\"]")
            Log.d("FurScraper", "parseSearch fallback: ${fallback.size} figures")
            return fallback.mapNotNull {
                val prefix = if (it.attr("id").startsWith("sid-")) "sid-" else "id-"
                parseFigure(it, sidPrefix = prefix)
            }
        }

        return figures.mapNotNull { parseFigure(it, sidPrefix = "sid-") }
    }

    /**
     * Parse a single figure element.
     * iOS: figure b u a img for thumbnail; figcaption p a for title/author.
     * Figure ID is "sid-XXXXXX".
     */
    private fun parseFigure(figure: Element, sidPrefix: String): Submission? {
        return try {
            val rawId = figure.attr("id")
            val id = rawId.removePrefix(sidPrefix)
            if (id.isEmpty() || id == rawId) return null

            // Thumbnail — iOS: "figure b u a img"
            val imgEl = figure.selectFirst("b u a img, a img, img")
            var thumbnailUrl = imgEl?.attr("src") ?: ""
            if (thumbnailUrl.isEmpty()) thumbnailUrl = imgEl?.attr("data-src") ?: ""
            if (thumbnailUrl.isNotEmpty() && !thumbnailUrl.startsWith("http")) {
                thumbnailUrl = "https:$thumbnailUrl"
            }

            // Listing thumbs carry display dimensions in data-width/data-height
            // (width can be fractional, e.g. "238.09"); the ratio lets the UI
            // pre-size items before the image loads. Missing attributes → 0,
            // which makes the UI fall back to a fixed ratio instead of failing.
            val thumbWidth = imgEl?.attr("data-width")?.toFloatOrNull() ?: 0f
            val thumbHeight = imgEl?.attr("data-height")?.toFloatOrNull() ?: 0f
            val thumbRatio = if (thumbWidth > 0f && thumbHeight > 0f) thumbWidth / thumbHeight else 0f

            // Title and author — iOS: "figcaption p a" (index 0 = title, 1 = author link)
            val captionLinks = figure.select("figcaption p a")
            val title = captionLinks.getOrNull(0)?.text()?.trim()
                ?: figure.selectFirst("a[href*=\"/view/\"]")?.attr("title")?.trim()
                ?: "Untitled"
            val author = captionLinks.getOrNull(1)?.text()?.trim()
                ?: figure.selectFirst("a[href*=\"/user/\"]")?.text()?.trim()
                ?: ""

            val authorUrl = captionLinks.getOrNull(1)?.attr("href")
                ?: figure.selectFirst("a[href*=\"/user/\"]")?.attr("href") ?: ""
            val authorUsername = if (authorUrl.contains("/user/")) {
                authorUrl.substringAfter("/user/").substringBefore("/")
            } else ""

            // Rating from figure class — iOS: Rating(submissionFigureClass: node.attr("class"))
            val figClass = figure.attr("class")
            val rating = when {
                figClass.contains("r-adult") || figClass.contains("rating-adult") -> "Adult"
                figClass.contains("r-mature") || figClass.contains("rating-mature") -> "Mature"
                else -> "General"
            }

            // 类型 class 直填（2026-10-07 实测词表：t-image/t-text/t-audio/t-flash），
            // 卡片浮层的类型徽标零请求即时点亮，统计预热后再被详情侧 fileExt 精化
            val fileType = when {
                figClass.contains("t-audio") -> "Music"
                figClass.contains("t-flash") -> "Flash"
                figClass.contains("t-story") -> "Story"
                figClass.contains("t-poetry") -> "Poetry"
                figClass.contains("t-text") -> "Text"
                figClass.contains("t-image") -> "Image"
                else -> ""
            }

            Submission(
                id = id,
                title = title,
                author = author,
                authorUsername = authorUsername,
                thumbnailUrl = thumbnailUrl,
                thumbnailWidthOnHeightRatio = thumbRatio,
                rating = rating,
                favorites = 0,
                comments = 0,
                views = 0,
                fileType = fileType
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseSubmissionDetail(
        doc: org.jsoup.nodes.Document,
        submissionId: String
    ): Submission {
        val title =
            doc.selectFirst(".submission-title h2, #page-submission .submission-title h2, #page-submission h2")
                ?.text()?.trim() ?: "Untitled"

        val authorNodes = doc.select(".submission-id-sub-container a[href*=\"/user/\"], .submission-title a[href*=\"/user/\"], .submission-description a[href*=\"/user/\"], .c-usernameBlockSimple a[href*=\"/user/\"], #page-submission a[href*=\"/user/\"]")
        val authorEl = authorNodes.firstOrNull { it.text().trim().isNotEmpty() && it.parents().none { p -> p.id() == "header" } }
        val author = authorEl?.text()?.trim() ?: ""

        val authorUrl = authorEl?.attr("href") ?: ""
        val authorUsername = if (authorUrl.contains("/user/")) {
            authorUrl.substringAfter("/user/").substringBefore("/")
        } else ""

        var authorAvatar =
            doc.selectFirst("div.artist-profile-avatar img, .submission-content-header img, .sidebar-section img.avatar")
                ?.attr("src") ?: ""
        if (authorAvatar.isNotEmpty() && !authorAvatar.startsWith("http")) {
            authorAvatar = "https:$authorAvatar"
        }
        // iOS approach: avatar is always at a.furaffinity.net/{username}.gif
        if (authorAvatar.isEmpty() && authorUsername.isNotEmpty()) {
            authorAvatar = "https://a.furaffinity.net/$authorUsername.gif"
        }

        // Favorite button — "+Fav" / "-Fav" in #submission-options, href carries the CSRF key
        val favButton = doc.select("div#submission-options a.button, div.submission-content-inner a.button")
            .firstOrNull { it.text().trim() in listOf("+Fav", "-Fav") }
        val isFavorited = favButton?.text()?.trim() == "-Fav"
        val favUrlAttr = favButton?.attr("href") ?: ""
        val favUrl = if (favUrlAttr.isNotEmpty() && !favUrlAttr.startsWith("http")) {
            "$FA_BASE$favUrlAttr"
        } else favUrlAttr

        var fullImageUrl = ""
        val mainImg =
            doc.selectFirst("#page-submission img, .submission-image img, .submission-body img")
        if (mainImg != null) {
            fullImageUrl = mainImg.attr("src").ifEmpty { mainImg.attr("data-src") }
            if (fullImageUrl.isNotEmpty() && !fullImageUrl.startsWith("http")) {
                fullImageUrl = "https:$fullImageUrl"
            }
        }
        if (fullImageUrl.isEmpty() || fullImageUrl.startsWith("data:")) {
            val dl = doc.selectFirst("a[href*=\"/download/\"], a.download, a.button[href*=\"d.furaffinity.net\"]")
            if (dl != null) {
                fullImageUrl = absoluteFaUrl(dl.attr("href"))
            }
        }

        val descNode = doc.selectFirst(".submission-description-text")
            ?: doc.selectFirst(".submission-description")
            ?: doc.selectFirst(".text")

        // Remove the 'by User on Date' header if it's included in the description container
        descNode?.select(".submission-id-sub-container")?.remove()
        descNode?.select(".submission-content-header")?.remove()
        descNode?.select("a[href^=\"/\"]")?.forEach {
            it.attr("href", "$FA_BASE${it.attr("href")}")
        }
        val description = descNode?.html()?.trim() ?: ""

        val sidebar = doc.selectFirst(".submission-sidebar, .sidebar, .stats")
        var rating = ""
        var category = ""
        var species = ""
        var gender = ""
        var views = 0
        var favorites = 0
        var comments = 0
        var date = doc.selectFirst("span.popup_date")?.attr("title")?.trim() ?: ""

        if (sidebar != null) {
            for (section in sidebar.select("section, div.stats-row, .mobile-section, .info-container div")) {
                val text = section.text().lowercase()
                val header = section.selectFirst("header, h5, h3, h4, strong")?.text()?.trim()?.lowercase() ?: ""
                val value = section.selectFirst("div, span, p")?.text()?.trim() ?: section.text().trim()

                when {
                    header.contains("category") || text.contains("category:") -> category = value.substringAfter(":")
                    header.contains("species") || text.contains("species:") -> species = value.substringAfter(":")
                    header.contains("gender") || text.contains("gender:") -> gender = value.substringAfter(":")
                    header.contains("views") || text.contains("views:") -> views = value.replace(Regex("[^0-9]"), "").toIntOrNull() ?: views
                    header.contains("favorites") || text.contains("favorites:") -> favorites = value.replace(Regex("[^0-9]"), "").toIntOrNull() ?: favorites
                    header.contains("comments") || text.contains("comments:") -> comments = value.replace(Regex("[^0-9]"), "").toIntOrNull() ?: comments
                    header.contains("date") || header.contains("posted") -> if (date.isEmpty()) date = value
                    header.contains("rating") || text.contains("rating:") -> rating = value.substringAfter(":")
                }
            }
        }

        // Direct selectors for beta theme stats container
        doc.select("div.stats-container div.views, .views, .submission-page-stats div[title=\"Views\"], div[title=\"Views\"]").firstOrNull()?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()?.let { views = it }
        doc.select("div.stats-container div.favorites, .favorites, .submission-page-stats div[title=\"Favorites\"], div[title=\"Favorites\"]").firstOrNull()?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()?.let { favorites = it }
        doc.select("div.stats-container div.comments, .comments, .submission-page-stats div[title=\"Comments\"], div[title=\"Comments\"]").firstOrNull()?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()?.let { comments = it }

        val tags = doc.select("a[href*=\"/search/?q=\"], .tag a, .tags a")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() && it.length < 50 }

        var fileUrl = ""
        val dlLink = doc.selectFirst("a[href*=\"/download/\"], a.download, a.button[href*=\"d.furaffinity.net\"]")
        if (dlLink != null) {
            // 匿名态页面的 Download 按钮直接指向 d.furaffinity.net 的协议相对链接
            fileUrl = absoluteFaUrl(dlLink.attr("href"))
        }

        // 文件类型徽标数据：扩展名从下载链接/原图 URL 截取（仅接受 1-5 位小写字母数字，
        // 防止把 CDN 路径里的句点误当扩展名）；fileName 供下载器与阅读器使用
        fun extFromUrl(url: String): String {
            if (url.isEmpty()) return ""
            val path = url.substringBefore('?').substringBefore('#')
            val ext = path.substringAfterLast('.', "").lowercase()
            return if (Regex("^[a-z0-9]{1,5}$").matches(ext)) ext else ""
        }
        val fileExt = extFromUrl(fileUrl).ifEmpty { extFromUrl(fullImageUrl) }
        val fileName = fileUrl.substringBefore('?').substringBefore('#')
            .substringAfterLast('/')
            .takeIf { it.contains('.') }
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            ?: ""

        var resolution = ""
        var fileSize = ""
        var fileType = ""

        // Flash 作品：object#flash_embed 携带原始画布尺寸（FA 页面用 Ruffle 模拟播放）
        val flashEmbed = doc.selectFirst(
            "object#flash_embed, object[type=\"application/x-shockwave-flash\"]"
        )
        var flashWidth = 0
        var flashHeight = 0
        if (flashEmbed != null) {
            flashWidth = flashEmbed.attr("data-width").toIntOrNull() ?: 0
            flashHeight = flashEmbed.attr("data-height").toIntOrNull() ?: 0
        }

        val statsLabels = doc.select("div.submission-content-stats span.highlight span")
        val statsValues = doc.select("div.submission-content-stats > span:not(.highlight) span")
        if (statsLabels.size == statsValues.size) {
            for (i in statsLabels.indices) {
                val label = statsLabels[i].text().trim().lowercase()
                val value = statsValues[i].text().trim()
                when {
                    label.contains("resolution") -> resolution = value
                    label.contains("file size") -> fileSize = value
                    label.contains("type") || label.contains("category") -> if (fileType.isEmpty()) fileType = value
                }
            }
        }

        val commentList = parseCommentsList(doc)

        // 作品所属画廊文件夹（sidebar-lower 的 Folders 区块）
        val folders = parseSubmissionFolders(doc)

        return Submission(
            id = submissionId,
            title = title,
            author = author,
            authorUsername = authorUsername,
            authorAvatar = authorAvatar,
            thumbnailUrl = fullImageUrl.ifEmpty { authorAvatar },
            fullImageUrl = fullImageUrl,
            description = description,
            rating = rating,
            category = category,
            species = species,
            gender = gender,
            views = views,
            favorites = favorites,
            comments = commentList.size,
            date = date,
            tags = tags,
            fileUrl = fileUrl,
            fileName = fileName,
            fileExt = fileExt,
            flashWidth = flashWidth,
            flashHeight = flashHeight,
            resolution = resolution,
            fileSize = fileSize,
            fileType = fileType,
            folders = folders,
            commentList = commentList,
            isFavorited = isFavorited,
            favUrl = favUrl
        )
    }

    /** 站内相对/协议相对链接 → 绝对 https 链接（FA 静态资源常用 //d.furaffinity.net 形态）。 */
    private fun absoluteFaUrl(url: String): String = when {
        url.isEmpty() -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("http") -> url
        url.startsWith("/") -> "$FA_BASE$url"
        else -> "$FA_BASE/$url"
    }

    /** view 页侧栏的 Folders 区块（#submission-sidebar-lower .submission-folder a）。 */
    private fun parseSubmissionFolders(doc: org.jsoup.nodes.Document): List<GalleryFolder> {
        val anchors = doc.select("#submission-sidebar-lower .submission-folder a")
            .ifEmpty { doc.select(".folder-list-container .submission-folder a") }
        val folders = mutableListOf<GalleryFolder>()
        for (a in anchors) {
            val folder = parseFolderAnchor(a, "") ?: continue
            if (folders.any { it.id == folder.id }) continue
            folders.add(folder)
        }
        return folders
    }

    /**
     * 评论解析（view 页与 journal 页同构，共用）。
     * FA encodes reply nesting as a shrinking container width:
     * style="width:N%" → indentation = 100 - N (iOS FAPageComment).
     */
    private fun parseCommentsList(scope: org.jsoup.nodes.Element): List<Comment> {
        val comments = mutableListOf<Comment>()
        for (commentEl in scope.select("div.comment_container")) {
            val indentation = Regex("width:(\\d+)%").find(commentEl.attr("style"))
                ?.groupValues?.get(1)?.toIntOrNull()
                ?.let { w -> (100 - w).coerceAtLeast(0) } ?: 0

            // Anchor is <a id="cid:N"> — the comment_anchor class is not always
            // present, so fall back to any cid: anchor in the container.
            val commentId = (
                commentEl.selectFirst("a.comment_anchor")?.id()
                    ?: commentEl.selectFirst("a[id^=\"cid:\"]")?.id()
                )?.removePrefix("cid:")?.trim().orEmpty()

            if (commentId.isEmpty()) {
                // Without a cid the row can't be keyed or threaded — drop it
                // rather than render a duplicate-keyed ghost.
                Log.w("FurScraper", "Skipping comment container without cid anchor")
                continue
            }

            try {
                val timestamp = commentEl.attr("data-timestamp").toLongOrNull() ?: 0

                val avatarEl = commentEl.selectFirst("img.comment_useravatar")
                var commentAvatar = avatarEl?.attr("src") ?: ""
                if (commentAvatar.isNotEmpty() && !commentAvatar.startsWith("http")) commentAvatar = "https:$commentAvatar"
                val commentUsername = avatarEl?.attr("alt") ?: ""
                // Fallback — same iOS trick as the author avatar
                if (commentAvatar.isEmpty() && commentUsername.isNotEmpty()) {
                    commentAvatar = "https://a.furaffinity.net/$commentUsername.gif"
                }

                val displayNameEl = commentEl.selectFirst("a.c-usernameBlock__displayName span.js-displayName")
                val commentAuthor = displayNameEl?.text()?.trim() ?: commentUsername

                val commentTitle = commentEl.selectFirst("comment-title")?.text()?.trim() ?: ""

                val commentTextEl = commentEl.selectFirst("comment-user-text")
                commentTextEl?.select("a[href^=\"/\"]")?.forEach {
                    it.attr("href", "$FA_BASE${it.attr("href")}")
                }
                val commentText = commentTextEl?.text()?.trim() ?: ""

                comments.add(Comment(
                    id = commentId,
                    author = commentAuthor,
                    authorUsername = commentUsername,
                    authorAvatar = commentAvatar,
                    title = commentTitle,
                    text = commentText,
                    timestamp = timestamp,
                    indentation = indentation
                ))
            } catch (e: Exception) {
                // Hidden-comment degradation (iOS FAPageComment): keep the row
                // with its cid/indentation instead of dropping it, so the page
                // never loses a comment and counts stay consistent.
                Log.w("FurScraper", "Comment $commentId degraded to hidden: ${e.message}")
                comments.add(Comment(id = commentId, indentation = indentation))
            }
        }
        return comments
    }

    suspend fun getUserProfile(username: String): com.furflix.app.data.model.UserProfile = withContext(Dispatchers.IO) {
        val url = "$FA_BASE/user/$username/"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Cookie", getCookiesString())
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Failed to load profile: ${response.code}")
        val html = response.body.string()
        val doc = org.jsoup.Jsoup.parse(html)

        // iOS approach: look for the watch/unwatch button in userpage-nav-interface-buttons
        val watchBtn = doc.selectFirst("userpage-nav-interface-buttons a.button")
        val watchUrlAttr = watchBtn?.attr("href") ?: ""
        val watchUrl = if (watchUrlAttr.isNotEmpty() && !watchUrlAttr.startsWith("http")) {
            "$FA_BASE$watchUrlAttr"
        } else watchUrlAttr

        // iOS approach: watching = URL path starts with /unwatch/
        val isWatching = watchUrlAttr.startsWith("/unwatch/")

        // iOS approach: avatar is always at a.furaffinity.net/{username}.gif — no HTML parsing needed
        val avatarUrl = "https://a.furaffinity.net/$username.gif"

        // Banner
        var bannerUrl = doc.selectFirst("#header a img")?.attr("src") ?: ""
        if (bannerUrl.isNotEmpty() && !bannerUrl.startsWith("http")) bannerUrl = "https:$bannerUrl"

        // Display name: iOS uses c-usernameBlock a.c-usernameBlock__displayName
        val displayName = doc
            .selectFirst("a.c-usernameBlock__displayName, userpage-nav-header .c-usernameBlock__displayName")
            ?.text()?.trim()
            ?.trimStart('~', '!')
            ?: username

        // User title ("Fursuiter | Registered: ...") — own text of span.user-title before the pipe
        val userTitleEl = doc.selectFirst("span.user-title")
        val userTitle = userTitleEl?.ownText()
            ?.substringBefore('|')?.trim() ?: ""

        // Registration date — the popup_date inside user-title carries title="19 years ago"
        val registeredText = userTitleEl?.selectFirst("span.popup_date")?.attr("title")?.trim() ?: ""

        // PM (note) link sits next to the watch button
        val noteUrlAttr = doc.selectFirst("userpage-nav-interface-buttons a[href*=\"/newpm/\"]")?.attr("href") ?: ""
        val noteUrl = if (noteUrlAttr.isNotEmpty() && !noteUrlAttr.startsWith("http")) {
            "$FA_BASE$noteUrlAttr"
        } else noteUrlAttr

        // Profile bio text — .userpage-profile, keeping <br> line breaks via marker chars
        val profileEl = doc.selectFirst(".userpage-profile")
        profileEl?.select("br")?.forEach { it.after(org.jsoup.nodes.TextNode("")) }
        val profileText = (profileEl?.text() ?: "")
            .replace("", "\n")
            .lines().joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

        // Stats block: <h2>Stats</h2> section with "Views: 78297 Submissions: 498 ..." cells
        var views = 0
        var galleryCount = 0
        var favoritesCount = 0
        var commentsCount = 0
        var journalsCount = 0
        val statsH2 = doc.select("h2").firstOrNull { it.text().trim().equals("Stats", ignoreCase = true) }
        val statsText = statsH2?.closest("section")?.text() ?: ""
        fun statValue(label: String): Int =
            Regex("$label:\\s*([\\d,]+)").find(statsText)?.groupValues?.get(1)
                ?.replace(",", "")?.toIntOrNull() ?: 0
        if (statsText.isNotEmpty()) {
            views = statValue("Views")
            galleryCount = statValue("Submissions")
            favoritesCount = statValue("Favs")
            commentsCount = statValue("Comments Earned")
            journalsCount = statValue("Journals")
        }

        // Watchers / Watching counts live in "View List (Watched by 2944)" links
        var watchersCount = 0
        var watchingCount = 0
        doc.selectFirst("a[href*=\"/watchlist/to/\"]")?.text()?.let { text ->
            watchersCount = Regex("Watched by\\s*([\\d,]+)").find(text)
                ?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0
        }
        doc.selectFirst("a[href*=\"/watchlist/by/\"]")?.text()?.let { text ->
            watchingCount = Regex("Watching\\s*([\\d,]+)").find(text)
                ?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0
        }

        // Legacy fallback for stats if the new selectors found nothing
        if (galleryCount == 0 && favoritesCount == 0) {
            val statElements = doc.select("userpage-nav-stat")
            for (el in statElements) {
                val label = el.selectFirst("span")?.text()?.trim()?.lowercase() ?: ""
                val value = el.selectFirst("b")?.text()?.trim()?.replace(",", "")?.toIntOrNull() ?: 0
                when {
                    label.contains("gallery") -> galleryCount = value
                    label.contains("favorite") -> favoritesCount = value
                    label.contains("watcher") -> watchersCount = value
                    label.contains("watching") -> watchingCount = value
                    label.contains("comment") -> commentsCount = value
                }
            }
        }

        // Profile questions: "Accepting Trades", "Accepting Commissions", "Character Species"
        var acceptingTrades = ""
        var acceptingCommissions = ""
        var species = ""
        for (row in doc.select("#userpage-contact-item .table-row")) {
            val question = row.selectFirst(".userpage-profile-question")?.text()?.trim() ?: continue
            val value = row.text().removePrefix(question).trim()
            when {
                question.contains("Accepting Trades", true) -> acceptingTrades = value
                question.contains("Accepting Commissions", true) -> acceptingCommissions = value
                question.contains("Character Species", true) -> species = value
            }
        }

        // Contact links — icon class carries the type (contact-icon-website etc.)
        val contactLinks = doc.select(".user-contact-item").mapNotNull { item ->
            val type = item.selectFirst("img[class*=\"contact-icon-\"]")?.classNames()
                ?.firstOrNull { it.startsWith("contact-icon-") }
                ?.removePrefix("contact-icon-") ?: "link"
            val info = item.selectFirst(".user-contact-user-info") ?: return@mapNotNull null
            val label = info.selectFirst("strong")?.text()?.trim()
                ?.ifEmpty { null } ?: type.replaceFirstChar { it.uppercase() }
            val anchor = info.selectFirst("a") ?: return@mapNotNull null
            val href = anchor.attr("href").trim()
            val text = anchor.text().trim()
            // Some links (e.g. Website) use javascript:void(0) — keep the visible text instead
            val linkUrl = when {
                href.startsWith("http") -> href
                text.startsWith("http") -> text
                text.contains(".") && !text.contains(" ") -> "https://$text"
                else -> return@mapNotNull null
            }
            com.furflix.app.data.model.ContactLink(type = type, label = label, url = linkUrl)
        }

        // Gallery folders：现代主题的用户页已不含文件夹列表（只在 /gallery/ 页侧栏，
        // 由 ViewModel 经 getUserGalleryFolders 从画廊页解析补取）；此处仅兜底扫描
        // 经典主题仍内嵌的 /folder/ 链接，区块缺失时自然得到空列表（容错，不抛错）。
        val folders = doc.select("a[href*=/folder/]")
            .mapNotNull { parseFolderAnchor(it, "") }
            .distinctBy { it.id }

        com.furflix.app.data.model.UserProfile(
            username = username,
            displayName = displayName,
            avatarUrl = avatarUrl,
            bannerUrl = bannerUrl,
            isWatching = isWatching,
            watchUrl = watchUrl,
            profileText = profileText,
            galleryCount = galleryCount,
            favoritesCount = favoritesCount,
            watchersCount = watchersCount,
            watchingCount = watchingCount,
            commentsCount = commentsCount,
            userTitle = userTitle,
            registeredText = registeredText,
            noteUrl = noteUrl,
            views = views,
            journalsCount = journalsCount,
            acceptingTrades = acceptingTrades,
            acceptingCommissions = acceptingCommissions,
            species = species,
            contactLinks = contactLinks,
            folders = folders
        )
    }

    suspend fun toggleWatch(watchUrl: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(watchUrl)
            .header("User-Agent", USER_AGENT)
            .header("Cookie", getCookiesString())
            .header("Referer", "https://www.furaffinity.net/")
            .build()
        val response = httpClient.newCall(request).execute()
        response.isSuccessful
    }

    suspend fun toggleFavorite(favUrl: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(favUrl)
            .header("User-Agent", USER_AGENT)
            .header("Cookie", getCookiesString())
            .header("Referer", "$FA_BASE/")
            .build()
        httpClient.newCall(request).execute().use { response ->
            // 会话过期时 FA 会 302 到 /login/，OkHttp 跟随后状态码仍是 2xx——
            // 必须复核最终落地 URL，否则收藏被静默判成"成功"
            response.isSuccessful && !response.request.url.encodedPath.contains("/login/")
        }
    }

    /**
     * Returns (users, hasNextPage) for "users I am watching".
     * Endpoint: https://www.furaffinity.net/watchlist/by/{username}?page=N
     * This is a public endpoint that works whether or not the viewer is logged in.
     * Mirrors the iOS implementation in FAWatchlistPage.swift.
     */
    suspend fun getWatchlistBy(
        username: String,
        page: Int = 1
    ): Pair<List<com.furflix.app.data.model.WatchlistUser>, Boolean> = withContext(Dispatchers.IO) {
        val url = "$FA_BASE/watchlist/by/$username?page=$page"
        val cookieHeader = getCookiesString()
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "$FA_BASE/")
        if (cookieHeader.isNotEmpty()) {
            requestBuilder.header("Cookie", cookieHeader)
        }

        Log.d("FurScraper", "getWatchlistBy: requesting $url (cookies=${cookieHeader.isNotEmpty()})")
        val response = httpClient.newCall(requestBuilder.build()).execute()
        val finalUrl = response.request.url.toString()
        Log.d("FurScraper", "getWatchlistBy: status=${response.code}, finalUrl=$finalUrl")
        if (!response.isSuccessful) throw Exception("Failed to load watchlist/by: ${response.code}")
        val html = response.body.string()
        Log.d("FurScraper", "getWatchlistBy: htmlLen=${html.length}")
        if (finalUrl.contains("/login")) {
            Log.w("FurScraper", "getWatchlistBy: redirected to login page, session likely expired")
        }
        val doc = org.jsoup.Jsoup.parse(html)

        val (users, hasNext) = parseWatchlistPage(doc, "by")
        Log.d("FurScraper", "getWatchlistBy: parsed ${users.size} users, hasNext=$hasNext, first=${users.firstOrNull()?.username}")
        Pair(users, hasNext)
    }

    /**
     * Returns (users, hasNextPage) for "users watching me" (Followers tab).
     * Endpoint: https://www.furaffinity.net/watchlist/to/{username}?page=N
     */
    suspend fun getWatchlistTo(
        username: String,
        page: Int = 1
    ): Pair<List<com.furflix.app.data.model.WatchlistUser>, Boolean> = withContext(Dispatchers.IO) {
        val url = "$FA_BASE/watchlist/to/$username?page=$page"
        val cookieHeader = getCookiesString()
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "$FA_BASE/")
        if (cookieHeader.isNotEmpty()) {
            requestBuilder.header("Cookie", cookieHeader)
        }

        Log.d("FurScraper", "getWatchlistTo: requesting $url (cookies=${cookieHeader.isNotEmpty()})")
        val response = httpClient.newCall(requestBuilder.build()).execute()
        val finalUrl = response.request.url.toString()
        Log.d("FurScraper", "getWatchlistTo: status=${response.code}, finalUrl=$finalUrl")
        if (!response.isSuccessful) throw Exception("Failed to load watchlist/to: ${response.code}")
        val html = response.body.string()
        Log.d("FurScraper", "getWatchlistTo: htmlLen=${html.length}")
        if (finalUrl.contains("/login")) {
            Log.w("FurScraper", "getWatchlistTo: redirected to login page, session likely expired")
        }
        val doc = org.jsoup.Jsoup.parse(html)

        val (users, hasNext) = parseWatchlistPage(doc, "to")
        Log.d("FurScraper", "getWatchlistTo: parsed ${users.size} users, hasNext=$hasNext, first=${users.firstOrNull()?.username}")
        Pair(users, hasNext)
    }

    /**
     * Parse the /watchlist/by and /watchlist/to pages.
     * Each user lives in <div class="watch-list-items watch-row ..."> with an
     * <a href="/user/{name}/"> inside a <span class="c-usernameBlockSimple__displayName">.
     * The `title` attribute on the displayName span holds the canonical lowercase username.
     * Pagination is form-based: the "Next" button has `disabled=""` when there is no next page.
     *
     * Returns (unique users, hasNextPage).
     */
    private fun parseWatchlistPage(doc: org.jsoup.nodes.Document, label: String = ""): Pair<List<com.furflix.app.data.model.WatchlistUser>, Boolean> {
        val rawItems = doc.select("div.watch-list-items")
        Log.d("FurScraper", "parseWatchlistPage[$label]: found ${rawItems.size} raw 'div.watch-list-items' blocks")

        if (rawItems.isEmpty()) {
            // The selector matched nothing at all — FA likely changed its markup, or we're on
            // an error/login page. Dump a slice of the HTML so it shows up in Logcat for diagnosis.
            val bodySnippet = doc.body()?.html()?.take(1500) ?: doc.html().take(1500)
            Log.w("FurScraper", "parseWatchlistPage[$label]: 0 raw items — dumping page title='${doc.title()}'")
            Log.w("FurScraper", "parseWatchlistPage[$label]: body snippet: $bodySnippet")
        }

        var skippedFlexHidden = 0
        var skippedNoLink = 0
        var skippedEmptyUsername = 0

        val users = rawItems.mapNotNull { item ->
            // Skip the empty placeholder rows FA uses for flex layout
            if (item.classNames().contains("flex-hidden-item-watches")) {
                skippedFlexHidden++
                return@mapNotNull null
            }
            val link = item.selectFirst("a[href*=\"/user/\"]")
            if (link == null) {
                skippedNoLink++
                return@mapNotNull null
            }

            val href = link.attr("href")
            val extractedUsername = href.substringAfter("/user/").substringBefore("/")
            if (extractedUsername.isEmpty()) {
                skippedEmptyUsername++
                return@mapNotNull null
            }

            // Prefer the displayName span (text = display name, title = canonical lowercase)
            val displayNameEl = item.selectFirst(".c-usernameBlockSimple__displayName")
            val displayName = displayNameEl?.text()?.trim()?.takeIf { it.isNotEmpty() }
                ?: link.text().trim().takeIf { it.isNotEmpty() }
                ?: extractedUsername

            com.furflix.app.data.model.WatchlistUser(
                username = extractedUsername,
                displayName = displayName,
                avatarUrl = "" // /watchlist/by and /watchlist/to do not expose avatars; WatchlistScreen falls back to a.furaffinity.net/{user}.gif
            )
        }.distinctBy { it.username.lowercase() }

        Log.d(
            "FurScraper",
            "parseWatchlistPage[$label]: parsed=${users.size}, skippedFlexHidden=$skippedFlexHidden, " +
                    "skippedNoLink=$skippedNoLink, skippedEmptyUsername=$skippedEmptyUsername"
        )
        if (users.isNotEmpty()) {
            Log.d("FurScraper", "parseWatchlistPage[$label]: usernames=${users.take(10).map { it.username }}")
        }

        // Form-based pagination: the "Next 200" button is disabled when there is no next page.
        // Section structure:
        //   <div class="section-footer watchlist-navigation">
        //     <div class="floatright">
        //       <form method="get" action="/watchlist/by/{user}">
        //         <input type="hidden" name="page" value="N">
        //         <button class="button" type="submit" disabled>Next 200</button>
        //       </form>
        //     </div>
        //   </div>
        val nextButton = doc.selectFirst("div.section-footer div.floatright form button[type=\"submit\"]")
        val hasNext = nextButton?.let { !it.hasAttr("disabled") } ?: false
        Log.d("FurScraper", "parseWatchlistPage[$label]: nextButtonFound=${nextButton != null}, hasNext=$hasNext")

        return Pair(users, hasNext)
    }
}