package com.furflix.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.furflix.app.data.model.CardStats
import com.furflix.app.data.repository.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 已看帖子的统计缓存：全部条目放 furflix_prefs 的单键（JSON 对象，插入序 =
 * 新旧序），put 时 LRU 上限淘汰；坏 JSON 视为空缓存重来。
 * 选型与翻译缓存一致：blob 型缓存不值得为它引入 Room。
 * 纯逻辑收敛在 [upsert]，便于无 Android 依赖的单测。
 */
class CardStatsCache private constructor(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), CardStats.serializer())

    val flow: Flow<Map<String, CardStats>> = context.dataStore.data.map { prefs ->
        decode(prefs[STORAGE_KEY])
    }

    /** 一次性快照（统计预热入口用于 TTL/visited 判定，避免订阅整表） */
    suspend fun snapshot(): Map<String, CardStats> =
        context.dataStore.data.first()[STORAGE_KEY]?.let { decode(it) } ?: emptyMap()

    suspend fun put(id: String, stats: CardStats) {
        context.dataStore.edit { prefs ->
            val current = decode(prefs[STORAGE_KEY])
            prefs[STORAGE_KEY] = json.encodeToString(mapSerializer, upsert(current, id, stats))
        }
    }

    /**
     * 批量写入：单次 DataStore 编辑、单次 JSON 序列化。统计预热并发化后回填
     * 频率显著升高，逐条 put 的全量序列化会成为新瓶颈，故预热侧一律走本入口。
     * [preserveVisited] 仅预热链路开启：回填（visited=false）撞上真实到访历史
     * （visited=true）时保留标记只更新计数；显式写 visited 的调用方
     * （如移除浏览历史）必须保持默认关，否则写 false 会被静默吞掉。
     */
    suspend fun putAll(entries: Map<String, CardStats>, preserveVisited: Boolean = false) {
        if (entries.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = decode(prefs[STORAGE_KEY])
            prefs[STORAGE_KEY] =
                json.encodeToString(mapSerializer, upsertAll(current, entries, preserveVisited = preserveVisited))
        }
    }

    suspend fun clear() {
        context.dataStore.edit { prefs ->
            prefs.remove(STORAGE_KEY)
        }
    }

    private fun decode(raw: String?): Map<String, CardStats> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return try {
            json.decodeFromString(mapSerializer, raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    companion object {
        private val STORAGE_KEY = stringPreferencesKey("card_stats")

        /**
         * 800 条 ≈ 单键 ~60KB，对 DataStore 足够轻。统计预热全面铺开后条目流速
         * 远高于从前，靠下面的"预热条目先淘汰"策略保护 visited 历史条目。
         */
        const val MAX_ENTRIES = 800

    /** 命中即挪到末尾（最新）；超限优先淘汰未到访的预热条目，visited 历史最后淘汰 */
    fun upsert(
        existing: Map<String, CardStats>,
        id: String,
        stats: CardStats,
        limit: Int = MAX_ENTRIES
    ): Map<String, CardStats> = upsertAll(existing, mapOf(id to stats), limit)

    /**
     * 批量版 upsert：逐条沿用 [upsert] 的"命中即最新 + 未到访先淘汰"次序。
     * [preserveVisited] 语义见 [putAll]：开启时预热回填（visited=false）覆盖
     * 到访历史（visited=true）会保留 visited 标记、只更新计数——预热数据比
     * 到访时点更新，但不能因此把帖子挤出浏览历史。
     */
    fun upsertAll(
        existing: Map<String, CardStats>,
        entries: Map<String, CardStats>,
        limit: Int = MAX_ENTRIES,
        preserveVisited: Boolean = false
    ): Map<String, CardStats> {
        if (entries.isEmpty()) return existing
        val ordered = LinkedHashMap(existing)
        for ((id, incoming) in entries) {
            val merged = if (preserveVisited && !incoming.visited) {
                ordered[id]?.takeIf { it.visited }?.let { incoming.copy(visited = true) } ?: incoming
            } else {
                incoming
            }
            ordered.remove(id)
            ordered[id] = merged
        }
        while (ordered.size > limit) {
            val victim = ordered.entries.firstOrNull { !it.value.visited }?.key
                ?: ordered.keys.firstOrNull()
                ?: break
            ordered.remove(victim)
        }
        return ordered
    }

        @Volatile
        private var INSTANCE: CardStatsCache? = null

        fun getInstance(context: Context): CardStatsCache {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CardStatsCache(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
