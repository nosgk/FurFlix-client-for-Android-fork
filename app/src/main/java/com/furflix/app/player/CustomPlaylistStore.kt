package com.furflix.app.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** 自定义播放列表的持久化条目（直链可选：加入时若已知就存下来，省一次解析）。 */
@Serializable
data class StoredPlaylistItem(
    val postId: String,
    val title: String = "",
    val artist: String = "",
    val coverUrl: String? = null,
    val url: String? = null,
    val artistUsername: String = ""
)

@Serializable
data class StoredPlaylist(
    val id: String,
    val name: String,
    val items: List<StoredPlaylistItem> = emptyList()
) {
    /** 内置"收藏"列表：名字由界面按 id 本地化，不落库（站点/用户数据不进 i18n）。 */
    val isBuiltIn: Boolean get() = id == CustomPlaylistStore.BUILT_IN_FAVORITES
}

private val Context.playlistDataStore: DataStore<Preferences> by preferencesDataStore(name = "playlists")

/**
 * 自定义播放列表仓（独立 playlists DataStore，与 furflix_prefs 互不干扰）。
 *
 * 一个内置"收藏"列表 + 用户自建列表；条目按 postId 去重（同一作品只留一条，
 * 重复添加视为"已在列表里"）。
 */
class CustomPlaylistStore private constructor(private val context: Context) {

    companion object {
        /** 内置"收藏"列表 id（界面按此 id 显示本地化名称）。 */
        const val BUILT_IN_FAVORITES = "builtin_favorites"

        private val json = Json { ignoreUnknownKeys = true }
        private val serializer =
            MapSerializer(String.serializer(), StoredPlaylist.serializer())

        @Volatile
        private var instance: CustomPlaylistStore? = null

        fun getInstance(context: Context): CustomPlaylistStore =
            instance ?: synchronized(this) {
                instance ?: CustomPlaylistStore(context.applicationContext).also { instance = it }
            }

        /** 解码：坏 JSON / 空一律回落成"只有内置收藏列表"。 */
        fun decode(raw: String?): List<StoredPlaylist> {
            val map = raw?.takeIf { it.isNotBlank() }
                ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
                ?: emptyMap()
            val stored = map.values.toMutableList()
            if (stored.none { it.id == BUILT_IN_FAVORITES }) {
                stored.add(0, StoredPlaylist(id = BUILT_IN_FAVORITES, name = ""))
            }
            // 内置列表恒在最前，其余按名称排
            return stored.sortedWith(
                compareBy({ it.id != BUILT_IN_FAVORITES }, { it.name.lowercase() })
            )
        }
    }

    private val storageKey = stringPreferencesKey("custom_playlists")

    /** 全部列表（含内置收藏），已按"内置在前"排好。 */
    val playlists: Flow<List<StoredPlaylist>> = context.playlistDataStore.data.map { prefs ->
        decode(prefs[storageKey])
    }

    suspend fun current(): List<StoredPlaylist> = playlists.first()

    /** 新建列表，返回新 id。 */
    suspend fun create(name: String): String {
        val id = "list_" + System.currentTimeMillis()
        write(current().filterNot { it.id == id } + StoredPlaylist(id = id, name = name.trim()))
        return id
    }

    suspend fun rename(id: String, name: String) {
        write(current().map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    suspend fun delete(id: String) {
        if (id == BUILT_IN_FAVORITES) return // 内置收藏不可删
        write(current().filterNot { it.id == id })
    }

    /** 加入一条：按 postId 去重（已存在则原样保留，不重复）。 */
    suspend fun addItem(id: String, item: StoredPlaylistItem) {
        write(
            current().map { list ->
                if (list.id != id) list
                else if (list.items.any { it.postId == item.postId }) list
                else list.copy(items = list.items + item)
            }
        )
    }

    suspend fun removeItem(id: String, postId: String) {
        write(
            current().map { list ->
                if (list.id != id) list
                else list.copy(items = list.items.filterNot { it.postId == postId })
            }
        )
    }

    private suspend fun write(lists: List<StoredPlaylist>) {
        val map = lists.associateBy { it.id }
        context.playlistDataStore.edit { prefs ->
            prefs[storageKey] = json.encodeToString(serializer, map)
        }
    }
}

/** 持久化条目 → 队列条目。 */
fun StoredPlaylistItem.toPlaylistItem() = PlaylistItem(
    postId = postId,
    title = title,
    artist = artist,
    coverUrl = coverUrl,
    url = url,
    artistUsername = artistUsername
)
