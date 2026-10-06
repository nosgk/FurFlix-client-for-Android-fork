package com.furflix.app.data.remote

import android.net.Uri

/**
 * FA 链接解析：外部浏览器 VIEW intent（App Links）、剪贴板识别、搜索框粘贴直达
 * 三处共用。scheme 可缺省——剪贴板里常是 "www.furaffinity.net/view/123" 裸文本。
 */
sealed class FaLink {
    /** 作品页：/view/{id}/（含 ?key= 变体）与 /full/{id}/ 原图页 */
    data class Submission(val id: String) : FaLink()

    /** 用户主页：/user/{name}/；/gallery/{name}/ 与 /favorites/{name}/ 首页视作用户页直达 */
    data class User(val username: String) : FaLink()

    /**
     * 搜索页：/search/?q=...（2026-10 表单 method=get，query 即 URL 状态）。
     * 入参白名单钳制见 [parseSearchFilters]：非法枚举丢弃、数值钳制到合法档位，
     * 保证深链进来的筛选与站内发起的同等"合法可控"。
     */
    data class Search(val query: String, val filters: com.furflix.app.data.model.SearchFilters) : FaLink()
}

private val FA_HOSTS = setOf("furaffinity.net", "www.furaffinity.net")

/** 不是 FA 链接时返回 null；含空白的整段文本（普通搜索词）一律不算链接。 */
fun parseFaLink(raw: String): FaLink? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.contains(' ') || trimmed.contains('\n')) return null
    val withScheme = if (trimmed.startsWith("http")) trimmed else "https://$trimmed"
    val uri = try {
        Uri.parse(withScheme)
    } catch (_: Exception) {
        return null
    }
    if (uri.host?.lowercase() !in FA_HOSTS) return null
    val segments = uri.path?.split('/')?.filter { it.isNotEmpty() } ?: return null
    return when {
        segments.size >= 2 &&
            segments[0] in listOf("view", "full") &&
            segments[1].toLongOrNull() != null ->
            FaLink.Submission(segments[1])
        segments.size >= 2 && segments[0] == "user" && segments[1].isNotBlank() ->
            FaLink.User(segments[1])
        segments.size >= 2 &&
            segments[0] in listOf("gallery", "favorites") &&
            segments[1].isNotBlank() ->
            FaLink.User(segments[1])
        segments.isNotEmpty() && segments[0] == "search" ->
            parseSearchDeepLink(uri)
        else -> null
    }
}

/** /search/ 深链：q 与筛选参数白名单解析；无 q 也接受（keywords 组可承载）。 */
private fun parseSearchDeepLink(uri: Uri): FaLink.Search? {
    val query = uri.getQueryParameter("q")?.trim() ?: ""
    val filters = parseSearchFilters(uri)
    if (query.isEmpty() && !filters.hasKeywords) return null
    return FaLink.Search(query, filters)
}

/**
 * 搜索 URL 参数 → SearchFilters（严格白名单：任何非法/越界值回落默认，
 * 与 [com.furflix.app.data.model.SearchFilters.toSearchQueryMap] 互为镜像）。
 */
private fun parseSearchFilters(uri: Uri): com.furflix.app.data.model.SearchFilters {
    var f = com.furflix.app.data.model.SearchFilters()
    fun param(key: String) = uri.getQueryParameter(key)

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
        .any { uri.getQueryParameter(it) != null }
    if (hasRatingKeys) {
        f = f.copy(
            ratingGeneral = param("rating-general") == "1",
            ratingMature = param("rating-mature") == "1",
            ratingAdult = param("rating-adult") == "1"
        )
    }
    val typeKeys = listOf("type-art", "type-flash", "type-photo", "type-music", "type-story", "type-poetry")
    if (typeKeys.any { uri.getQueryParameter(it) != null }) {
        f = f.copy(
            typeArt = param("type-art") == "1",
            typeFlash = param("type-flash") == "1",
            typePhoto = param("type-photo") == "1",
            typeMusic = param("type-music") == "1",
            typeStory = param("type-story") == "1",
            typePoetry = param("type-poetry") == "1"
        )
    }
    return f
}
