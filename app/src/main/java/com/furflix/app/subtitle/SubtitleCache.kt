package com.furflix.app.subtitle

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * 字幕缓存：postId → SRT 正文。同一作品重复点"生成字幕"零 Token 消耗——
 * 转录是按音频时长计费的调用，重复生成是纯浪费。
 *
 * 与 [com.furflix.app.translate.data.TranslationCache] 同构：单键 JSON + LRU 序 +
 * 800ms 防抖写回 + 坏数据当作空缓存，只是键换成了作品号。
 */
class SubtitleCache private constructor(private val context: Context) {

    companion object {
        private const val DEBOUNCE_MS = 800L

        /** 条目上限：SRT 正文多为几 KB，40 条不过几百 KB。 */
        private const val MAX_ENTRIES = 40

        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = MapSerializer(String.serializer(), String.serializer())

        @Volatile
        private var instance: SubtitleCache? = null

        fun getInstance(context: Context): SubtitleCache =
            instance ?: synchronized(this) {
                instance ?: SubtitleCache(context.applicationContext).also { instance = it }
            }
    }

    private val mutex = Mutex()
    private val map = LinkedHashMap<String, String>() // 迭代序 = LRU 序（最旧在前）
    private var loaded = false
    private var writeJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val storageKey = stringPreferencesKey("subtitle_cache")

    suspend fun get(postId: String): String? = mutex.withLock {
        ensureLoadedLocked()
        val value = map.remove(postId) ?: return@withLock null
        map[postId] = value // 命中即刷新 recency（只动内存，不触发写）
        value
    }

    suspend fun put(postId: String, srt: String) {
        mutex.withLock {
            ensureLoadedLocked()
            map.remove(postId)
            map[postId] = srt
            while (map.size > MAX_ENTRIES) {
                val oldest = map.keys.firstOrNull() ?: break
                map.remove(oldest)
            }
            writeJob?.cancel()
            writeJob = scope.launch { delay(DEBOUNCE_MS); persist() }
        }
    }

    suspend fun clear() {
        mutex.withLock {
            map.clear()
            writeJob?.cancel()
            writeJob = null
        }
        runCatching { context.subtitleDataStore.edit { it[storageKey] = "{}" } }
    }

    private suspend fun ensureLoadedLocked() {
        if (loaded) return
        loaded = true // 只尝试一次：坏数据视为空缓存
        val prefs = try {
            context.subtitleDataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .first()
        } catch (_: Exception) {
            return
        }
        val raw = prefs[storageKey] ?: return
        map.putAll(runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyMap()))
    }

    private suspend fun persist() {
        val snapshot: LinkedHashMap<String, String>
        mutex.withLock { snapshot = LinkedHashMap(map) }
        if (snapshot.isEmpty()) return
        runCatching {
            context.subtitleDataStore.edit { it[storageKey] = json.encodeToString(serializer, snapshot) }
        } // 写失败静默：缓存坏不该影响已生成的字幕
    }
}
