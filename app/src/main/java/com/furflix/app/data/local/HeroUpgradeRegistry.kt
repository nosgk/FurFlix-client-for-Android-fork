package com.furflix.app.data.local

import android.util.LruCache

/**
 * 跨视图资源复用登记表：详情页完整加载过的原图（fullUrl）按帖子 id 登记，
 * 返回画廊/搜索列表时卡片查表命中内存缓存即用高清图顶替缩略图——
 * "进过详情的作品回列表直接是高清"的无缝升级，原缩略图请求随之剔除。
 *
 * 只有内存缓存仍持有解码结果时卡片才真正启用升级（磁盘命中要重新解码、
 * 缓存被逐后甚至要重新联网，宁回落缩略图）；GIF 原图不登记——列表卡解码
 * 动图会拖垮网格内存。
 */
object HeroUpgradeRegistry {
    private val heroes = LruCache<String, String>(256)

    fun record(postId: String, fullUrl: String) {
        if (postId.isBlank() || fullUrl.isBlank()) return
        if (fullUrl.substringBefore('?').endsWith(".gif", ignoreCase = true)) return
        heroes.put(postId, fullUrl)
    }

    fun lookup(postId: String): String? = heroes.get(postId)

    fun clear() = heroes.evictAll()
}
