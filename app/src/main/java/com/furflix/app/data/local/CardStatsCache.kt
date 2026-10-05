package com.furflix.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.furflix.app.data.model.CardStats
import com.furflix.app.data.repository.dataStore
import kotlinx.coroutines.flow.Flow
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

    suspend fun put(id: String, stats: CardStats) {
        context.dataStore.edit { prefs ->
            val current = decode(prefs[STORAGE_KEY])
            prefs[STORAGE_KEY] = json.encodeToString(mapSerializer, upsert(current, id, stats))
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
        ): Map<String, CardStats> {
            val ordered = LinkedHashMap(existing)
            ordered.remove(id)
            ordered[id] = stats
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
