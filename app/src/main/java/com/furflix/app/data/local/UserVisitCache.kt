package com.furflix.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.furflix.app.data.repository.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * “浏览历史 → 用户”栏目：用户真正点进过的用户页记录（用户主页加载即记一笔），
 * 与帖子的 CardStats 分开存。LRU 上限淘汰，最新在前；坏 JSON 视为空缓存重来。
 * 选型与 CardStatsCache 一致：blob 型缓存不值得为它引入 Room。
 */
@Serializable
data class UserVisitRecord(
    val username: String,
    val displayName: String = "",
    val avatarUrl: String = "",
    val visitedAt: Long = 0L
)

class UserVisitCache private constructor(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(UserVisitRecord.serializer())

    val flow: Flow<List<UserVisitRecord>> = context.dataStore.data.map { prefs ->
        decode(prefs[STORAGE_KEY])
    }

    /** 记一笔并顶到最前；同名（忽略大小写）去重，超限从最旧端淘汰。 */
    suspend fun put(username: String, displayName: String, avatarUrl: String) {
        if (username.isBlank()) return
        context.dataStore.edit { prefs ->
            val rest = decode(prefs[STORAGE_KEY]).filterNot { it.username.equals(username, ignoreCase = true) }
            val record = UserVisitRecord(
                username = username,
                displayName = displayName,
                avatarUrl = avatarUrl,
                visitedAt = System.currentTimeMillis()
            )
            prefs[STORAGE_KEY] = json.encodeToString(serializer, (listOf(record) + rest).take(MAX_ENTRIES))
        }
    }

    /** 长按删除单条（忽略大小写）。 */
    suspend fun remove(username: String) {
        context.dataStore.edit { prefs ->
            val rest = decode(prefs[STORAGE_KEY]).filterNot { it.username.equals(username, ignoreCase = true) }
            if (rest.isEmpty()) prefs.remove(STORAGE_KEY)
            else prefs[STORAGE_KEY] = json.encodeToString(serializer, rest)
        }
    }

    suspend fun clear() {
        context.dataStore.edit { prefs ->
            prefs.remove(STORAGE_KEY)
        }
    }

    private fun decode(raw: String?): List<UserVisitRecord> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    companion object {
        private val STORAGE_KEY = stringPreferencesKey("user_visit_history")

        /** 与 CardStatsCache 的 400 条同量级；用户记录更小，留 300 条足够 */
        const val MAX_ENTRIES = 300

        @Volatile
        private var INSTANCE: UserVisitCache? = null

        fun getInstance(context: Context): UserVisitCache {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserVisitCache(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
