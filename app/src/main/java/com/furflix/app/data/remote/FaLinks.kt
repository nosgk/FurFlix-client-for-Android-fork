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
        else -> null
    }
}
