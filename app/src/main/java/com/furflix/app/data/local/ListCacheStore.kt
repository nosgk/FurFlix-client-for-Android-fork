package com.furflix.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.furflix.app.data.model.Submission
import com.furflix.app.data.repository.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 列表页缓存优先（Cache-First）的本地资产仓：浏览/最新/用户画廊/Scraps/收藏
 * 等日常浏览列表的第一页落地为 JSON 单键，冷启动/重进页面零流量直接出列表，
 * 只有用户主动下拉刷新才触发网络校验与整页替换（增量翻页照常走网络，不回写）。
 *
 * 选型与 CardStatsCache 一致：blob 型缓存不值得引入 Room；键插入序 = 新旧序，
 * 超限按 LRU 淘汰。NSFW 口径随条目存取——开关翻转后的旧缓存视为失效，
 * 避免恢复出与当前过滤设置不符的列表。
 */
class ListCacheStore private constructor(private val context: Context) {

    @Serializable
    private data class Entry(
        val items: List<Submission> = emptyList(),
        val savedAt: Long = 0L,
        val nsfw: Boolean = false
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), Entry.serializer())

    /** 命中返回缓存条目；无缓存 / NSFW 口径不符 / 空列表 → null（调用方走网络）。 */
    suspend fun load(key: String, currentNsfw: Boolean): List<Submission>? {
        val raw = context.dataStore.data.first()[STORAGE_KEY] ?: return null
        val entry = decode(raw)[key] ?: return null
        if (entry.items.isEmpty()) return null
        if (entry.nsfw != currentNsfw) return null
        return entry.items
    }

    suspend fun save(key: String, items: List<Submission>, currentNsfw: Boolean) {
        if (items.isEmpty()) return
        context.dataStore.edit { prefs ->
            val ordered = LinkedHashMap(decode(prefs[STORAGE_KEY]))
            ordered.remove(key)
            ordered[key] = Entry(items.take(MAX_ITEMS_PER_LIST), System.currentTimeMillis(), currentNsfw)
            while (ordered.size > MAX_KEYS) {
                ordered.remove(ordered.keys.first())
            }
            prefs[STORAGE_KEY] = json.encodeToString(mapSerializer, ordered)
        }
    }

    suspend fun remove(key: String) {
        context.dataStore.edit { prefs ->
            val ordered = LinkedHashMap(decode(prefs[STORAGE_KEY]))
            ordered.remove(key)
            prefs[STORAGE_KEY] = json.encodeToString(mapSerializer, ordered)
        }
    }

    private fun decode(raw: String?): Map<String, Entry> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return try {
            json.decodeFromString(mapSerializer, raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    companion object {
        private val STORAGE_KEY = stringPreferencesKey("list_page_cache")

        /** 24 个列表 × 每列表 96 条 ≈ 单键 ~400KB，对 DataStore 可接受 */
        const val MAX_KEYS = 24
        const val MAX_ITEMS_PER_LIST = 96

        const val KEY_BROWSE = "browse"
        const val KEY_LATEST = "latest"

        fun userGallery(username: String, scraps: Boolean) =
            "user:${username.lowercase()}:" + if (scraps) "scraps" else "gallery"

        fun userFavorites(username: String) = "user:${username.lowercase()}:favorites"

        @Volatile
        private var INSTANCE: ListCacheStore? = null

        fun getInstance(context: Context): ListCacheStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ListCacheStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
