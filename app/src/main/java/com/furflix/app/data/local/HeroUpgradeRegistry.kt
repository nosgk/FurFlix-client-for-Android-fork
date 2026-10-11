package com.furflix.app.data.local

import android.util.LruCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 跨视图资源复用登记表：详情页完整加载过的原图（fullUrl）按帖子 id 登记，
 * 返回画廊/搜索列表时卡片查表命中内存缓存即用高清图顶替缩略图——
 * "进过详情的作品回列表直接是高清"的无缝升级，原缩略图请求随之剔除。
 *
 * 只有内存缓存仍持有解码结果时卡片才真正启用升级（磁盘命中要重新解码、
 * 缓存被逐后甚至要重新联网，宁回落缩略图）；GIF 原图不登记——列表卡解码
 * 动图会拖垮网格内存。
 *
 * 登记表是**可观察**的：[version] 在每次新登记时自增，卡片用
 * [observe] 订阅后即使在同一个组合实例内（下拉刷新后 key 不变、remember
 * 不重算的场景）也能及时读到新登记的原图。
 */
object HeroUpgradeRegistry {
    private val heroes = LruCache<String, String>(256)

    private val _version = MutableStateFlow(0L)
    /** 登记表版本号：每次成功登记自增，供画廊卡片订阅以触发重查。 */
    val version: StateFlow<Long> = _version.asStateFlow()

    fun record(postId: String, fullUrl: String) {
        if (postId.isBlank() || fullUrl.isBlank()) return
        if (fullUrl.substringBefore('?').endsWith(".gif", ignoreCase = true)) return
        heroes.put(postId, fullUrl)
        // 重复登记同一 URL 也自增：读侧缓存可能因逐出而失效，让它重查一次
        _version.value++
    }

    fun lookup(postId: String): String? = heroes.get(postId)

    fun clear() {
        heroes.evictAll()
        _version.value++
    }
}
