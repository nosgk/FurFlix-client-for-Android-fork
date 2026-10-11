package com.furflix.app.data.remote

/**
 * FA 链接解析：外部浏览器 VIEW intent（App Links）、剪贴板识别、搜索框粘贴直达、
 * 正文内链（描述/日志的 HtmlTextView）共用。scheme 可缺省——剪贴板里常是
 * "www.furaffinity.net/view/123" 裸文本。
 *
 * 解析为纯 Kotlin 实现（不依赖 android.net.Uri）：一是 JVM 单测能直接覆盖
 * 真实脏样本；二是手动解析比 Uri.parse 更能容忍复制分享时的引号/句读/
 * 零宽字符等包裹噪声——历史上"链接尾部粘着句号/引号"、"整段聊天记录里
 * 抠链接"都会解析失败，表现为"应用内打不开 FA 链接"。
 */
sealed class FaLink {
    /** 作品页：/view/{id}/（含 ?key= 变体）与 /full/{id}/ 原图页 */
    data class Submission(val id: String) : FaLink()

    /**
     * 用户主页：/user/{name}/；/gallery/{name}/、/favorites/{name}/、
     * /scraps/{name}/ 等用户维度子页首页同样视作用户页直达（站内路由到
     * 对应 profile，用户可在页内切 tab）。
     */
    data class User(val username: String) : FaLink()

    /**
     * 搜索页：/search/?q=...（2026-10 表单 method=get，query 即 URL 状态）。
     * 入参白名单钳制见 [parseSearchFilters]：非法枚举丢弃、数值钳制到合法档位，
     * 保证深链进来的筛选与站内发起的同等"合法可控"。
     */
    data class Search(val query: String, val filters: com.furflix.app.data.model.SearchFilters) : FaLink()
}

private val FA_HOSTS = setOf("furaffinity.net", "www.furaffinity.net")

/** 用户维度入口段：user 之外的子页首页都回落用户页。 */
private val USER_SEGMENTS = setOf(
    "user", "gallery", "favorites", "scraps", "journals", "commissions", "submissions", "stats"
)

/**
 * 复制分享常见的首尾包裹噪声：空白/零宽字符、引号书名号、全角括号与中英文
 * 句读。只剥首尾（URL 中段合法字符不动），"链接后紧跟句号/引号"不再污染 id。
 */
private const val WRAPPER_CHARS =
    " \t\r\n\u200B\u200C\u200D\uFEFF\"'“”‘’《》〈〉()（）[]【】<>,，.。;；:：!！?？、|"

private val URL_RE =
    Regex("""^https?://([^/?#]+)(/[^?#]*)?(\?[^#]*)?(#.*)?""", RegexOption.IGNORE_CASE)

private val ZERO_WIDTH_RE = Regex("[\\u200B\\u200C\\u200D\\uFEFF]")

/** 整段文本里抠 FA 链接（聊天记录/帖子标题整段复制进剪贴板或搜索框时兜底）。 */
private val FA_URL_IN_TEXT = Regex(
    """(?:https?://)?(?:[a-z0-9-]+\.)*furaffinity\.net/\S{1,}""",
    RegexOption.IGNORE_CASE
)

/** URL 尾部合法收尾字符：之外的句读/散文文字都是复制粘贴粘上来的噪声。 */
private const val TRAIL_SAFE_CHARS =
    "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789/?&=%#._-~+:@"

private fun String.trimTrailingNoise(): String {
    var end = length
    while (end > 0 && this[end - 1] !in TRAIL_SAFE_CHARS) end--
    return substring(0, end)
}

/** 不是 FA 链接时返回 null。 */
fun parseFaLink(raw: String): FaLink? {
    val stripped = raw.replace(ZERO_WIDTH_RE, "")
    val cleaned = stripped.trim { it in WRAPPER_CHARS }
    if (cleaned.isEmpty()) return null
    parseFaUrl(cleaned)?.let { return it }
    parseFaUrl(cleaned.trimTrailingNoise())?.let { return it }
    // 整段聊天记录/句子内嵌链接（中文散文前后常无空格）：抠出首个 FA 链接重试。
    // 普通搜索词抠不出 FA 链接，按原语义返回 null 交回搜索。
    val candidate = FA_URL_IN_TEXT.find(cleaned)?.value?.trimTrailingNoise() ?: return null
    if (candidate == cleaned) return null
    return parseFaUrl(candidate)
}

private fun parseFaUrl(url: String): FaLink? {
    if (url.isEmpty() || url.contains(' ') || url.contains('\n')) return null
    val withScheme = if (url.startsWith("http", ignoreCase = true)) url else "https://$url"
    val match = URL_RE.find(withScheme) ?: return null
    val host = match.groupValues[1].lowercase().substringBefore(':')
    if (host !in FA_HOSTS) return null
    val segments = match.groupValues[2].trimStart('/').split('/').filter { it.isNotEmpty() }
    val query = match.groupValues[3]
    return when {
        segments.size >= 2 &&
            segments[0] in listOf("view", "full") &&
            segments[1].toLongOrNull() != null ->
            FaLink.Submission(segments[1])
        segments.size >= 2 && segments[0] in USER_SEGMENTS && segments[1].isNotBlank() ->
            FaLink.User(segments[1])
        segments.isNotEmpty() && segments[0] == "search" -> {
            // FA 现代搜索两种形态：/search/?q=...（标准 query）与
            // /search/@q=...（@ 后内嵌同构参数串）；后者取 @ 后按 query 解析
            val inlineRaw = match.groupValues[2].removePrefix("/search/")
            val effective = when {
                query.isNotBlank() -> query
                inlineRaw.startsWith("@") -> "?" + inlineRaw.substring(1)
                else -> ""
            }
            parseSearchDeepLink(effective)
        }
        else -> null
    }
}

/** /search/ 深链：q 与筛选参数白名单解析；无 q 也接受（keywords 组可承载）。 */
private fun parseSearchDeepLink(query: String): FaLink.Search? {
    val raw = queryParam(query, "q")?.trim() ?: ""
    var filters = parseSearchFilters(query)
    // buildKeywordQuery 的逆变换：@keywords 组拆回 keywords/genders，恢复出与
    // 站内发起时一致的筛选面板状态（双向同步的另一半）；无组时 q 原样
    var q = raw
    com.furflix.app.data.model.SearchFilters.splitKeywordQuery(raw)?.let { (base, keywords, genders) ->
        q = base
        filters = filters.copy(keywords = keywords, genders = genders)
    }
    if (q.isEmpty() && !filters.hasKeywords) return null
    return FaLink.Search(q, filters)
}

/**
 * 搜索 URL 参数 → SearchFilters（严格白名单：任何非法/越界值回落默认，
 * 与 [com.furflix.app.data.model.SearchFilters.toSearchQueryMap] 互为镜像）。
 */
private fun parseSearchFilters(query: String): com.furflix.app.data.model.SearchFilters {
    var f = com.furflix.app.data.model.SearchFilters()
    fun param(key: String) = queryParam(query, key)

    when (param("order-by")?.lowercase()) {
        "date", "relevancy", "popularity" -> f = f.copy(orderBy = param("order-by")!!.lowercase())
    }
    when (param("order-direction")?.lowercase()) {
        "asc", "desc" -> f = f.copy(orderDirection = param("order-direction")!!.lowercase())
    }
    when (param("range")?.lowercase()) {
        "1day", "3days", "7days", "30days", "90days", "1year", "3years", "5years", "all" ->
            f = f.copy(range = param("range")!!.lowercase())
        "manual" -> {
            val from = param("range_from").orEmpty()
            val to = param("range_to").orEmpty()
            val dateRe = Regex("""\d{4}-\d{2}-\d{2}""")
            if (dateRe.matches(from) || dateRe.matches(to)) {
                f = f.copy(
                    range = "manual",
                    rangeFrom = if (dateRe.matches(from)) from else "",
                    rangeTo = if (dateRe.matches(to)) to else ""
                )
            }
        }
    }
    when (param("mode")?.lowercase()) {
        "all", "any", "extended" -> f = f.copy(mode = param("mode")!!.lowercase())
    }
    // FA 只认 24/48/72 三档：钳到最近档
    param("perpage")?.toIntOrNull()?.let { requested ->
        val nearest = com.furflix.app.data.model.SearchFilters.PERPAGE_CHOICES.minByOrNull {
            kotlin.math.abs(it - requested)
        } ?: com.furflix.app.data.model.SearchFilters.PERPAGE_MAX
        f = f.copy(perpage = nearest)
    }
    param("category")?.toIntOrNull()?.let { if (it > 0) f = f.copy(category = it) }
    param("arttype")?.toIntOrNull()?.let { if (it > 0) f = f.copy(artType = it) }
    param("species")?.toIntOrNull()?.let { if (it > 0) f = f.copy(species = it) }
    // rating/type 复选组语义（2026-10-07 实测）：URL 出现任一键 = 该组显式过滤，
    // 缺省 = 该组不过滤（实测省略全部 type-* 键不产生类型过滤）——
    // 因此只有组内至少一键存在时才按 "=1" 与否整体改写，避免极简分享链接
    // ?q=fox 被误解析成"全不勾"而搜空。
    val hasRatingKeys = listOf("rating-general", "rating-mature", "rating-adult")
        .any { queryParam(query, it) != null }
    if (hasRatingKeys) {
        f = f.copy(
            ratingGeneral = param("rating-general") == "1",
            ratingMature = param("rating-mature") == "1",
            ratingAdult = param("rating-adult") == "1"
        )
    }
    val typeKeys = listOf("type-art", "type-flash", "type-photo", "type-music", "type-story", "type-poetry")
    if (typeKeys.any { queryParam(query, it) != null }) {
        f = f.copy(
            typeArt = param("type-art") == "1",
            typeFlash = param("type-flash") == "1",
            typePhoto = param("type-photo") == "1",
            typeMusic = param("type-music") == "1",
            typeStory = param("type-story") == "1",
            typePoetry = param("type-poetry") == "1"
        )
    }
    // 统一收敛到站点合法入参：手改/第三方产出的链接里的越界 id、过期枚举
    // 都在这里被挡下，不让它们进入请求与持久化（与 toSearchQueryMap 镜像）。
    return f.normalized()
}

/** 最小 query 解析：首个同名键生效；%XX 与 + 解码，失败回落原值。 */
private fun queryParam(query: String, key: String): String? {
    if (query.isEmpty()) return null
    for (pair in query.removePrefix("?").split('&')) {
        if (pair.isEmpty()) continue
        val idx = pair.indexOf('=')
        val k = if (idx >= 0) pair.substring(0, idx) else pair
        if (k != key) continue
        val v = if (idx >= 0) pair.substring(idx + 1) else ""
        return try {
            java.net.URLDecoder.decode(v, "UTF-8")
        } catch (_: Exception) {
            v
        }
    }
    return null
}
