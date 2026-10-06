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
 * “我的 → 收藏夹 → 别人的收藏夹”栏目：用户点过“收藏 TA 的收藏夹”的其他用户记录。
 * 收藏的是“该用户的收藏夹入口”（跳转其用户页收藏 tab），不是作品本身。
 * 选型与 UserVisitCache 一致：blob 型缓存不值得为它引入 Room。
 */
@Serializable
data class CollectedFavoritesRecord(
    val username: String,
    val displayName: String = "",
    val avatarUrl: String = "",
    val collectedAt: Long = 0L
)

class CollectedFavoritesStore private constructor(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(CollectedFavoritesRecord.serializer())

    val flow: Flow<List<CollectedFavoritesRecord>> = context.dataStore.data.map { prefs ->
        decode(prefs[STORAGE_KEY])
    }

    /** 收藏一条并顶到最前；同名（忽略大小写）去重。 */
    suspend fun add(username: String, displayName: String, avatarUrl: String) {
        if (username.isBlank()) return
        context.dataStore.edit { prefs ->
            val rest = decode(prefs[STORAGE_KEY]).filterNot { it.username.equals(username, ignoreCase = true) }
            val record = CollectedFavoritesRecord(
                username = username,
                displayName = displayName,
                avatarUrl = avatarUrl,
                collectedAt = System.currentTimeMillis()
            )
            prefs[STORAGE_KEY] = json.encodeToString(serializer, (listOf(record) + rest).take(MAX_ENTRIES))
        }
    }

    /** 取消收藏（忽略大小写）。 */
    suspend fun remove(username: String) {
        context.dataStore.edit { prefs ->
            val rest = decode(prefs[STORAGE_KEY]).filterNot { it.username.equals(username, ignoreCase = true) }
            if (rest.isEmpty()) prefs.remove(STORAGE_KEY)
            else prefs[STORAGE_KEY] = json.encodeToString(serializer, rest)
        }
    }

    private fun decode(raw: String?): List<CollectedFavoritesRecord> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    companion object {
        private val STORAGE_KEY = stringPreferencesKey("collected_favorites_users")

        /** 别人的收藏夹是手工精选入口，100 条远超实际使用 */
        const val MAX_ENTRIES = 100

        @Volatile
        private var INSTANCE: CollectedFavoritesStore? = null

        fun getInstance(context: Context): CollectedFavoritesStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CollectedFavoritesStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
