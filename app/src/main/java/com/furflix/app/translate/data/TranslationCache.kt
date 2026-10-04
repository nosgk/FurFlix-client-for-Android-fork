package com.furflix.app.translate.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.IOException

/**
 * 翻译功能专用 DataStore（独立于 FurRepository 的 furflix_prefs）：
 * 翻译键 20+ 全部自包含，最小化对上游文件的触碰面（rebase 冲突最小）。
 */
val Context.translationDataStore: DataStore<Preferences> by preferencesDataStore(name = "translate_prefs")

/**
 * 持久化 LRU 缓存（蓝本 §8）：全部条目放一个 DataStore 键（单键 JSON 对象，按 LRU 序）；
 * 写回 800ms 防抖；加载时不淘汰；坏 JSON 直接清空重来；写失败静默。
 * 选型说明：本仓库 Room 为声明未用的幽灵依赖，为 blob 型缓存引入 Room 是纯增重，故走 DataStore。
 */
class TranslationCache private constructor(private val context: Context) {

    companion object {
        private const val DEBOUNCE_MS = 800L

        @Volatile
        private var instance: TranslationCache? = null

        fun getInstance(context: Context): TranslationCache =
            instance ?: synchronized(this) {
                instance ?: TranslationCache(context.applicationContext).also { instance = it }
            }
    }

    private val mutex = Mutex()
    private val map = LinkedHashMap<String, String>() // 迭代序 = LRU 序（最旧在前）
    private var loaded = false
    private var writeJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val storageKey = stringPreferencesKey("translate_cache")

    private suspend fun ensureLoadedLocked() {
        if (loaded) return
        loaded = true // 只尝试一次：坏数据视为空缓存
        val prefs = try {
            context.translationDataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .first()
        } catch (_: Exception) {
            return
        }
        val raw = prefs[storageKey] ?: return
        val obj = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        for ((k, v) in obj) {
            val s = v as? JsonPrimitive ?: continue
            if (s.isString) map[k] = s.content
        }
    }

    suspend fun get(key: String): String? = mutex.withLock {
        ensureLoadedLocked()
        val v = map.remove(key) ?: return@withLock null
        map[key] = v // 读命中：挪到末尾（recency 只更新内存，不触发写）
        v
    }

    suspend fun put(key: String, value: String, limit: Int) {
        mutex.withLock {
            ensureLoadedLocked()
            map.remove(key)
            map[key] = value
            if (limit > 0) {
                while (map.size > limit) {
                    val oldest = map.keys.firstOrNull() ?: break
                    map.remove(oldest)
                }
            }
            writeJob?.cancel()
            writeJob = scope.launch { delay(DEBOUNCE_MS); persist() }
        }
    }

    /** (条目数, key+value 的 UTF-8 字节数)，供设置页展示。 */
    suspend fun stats(): Pair<Int, Int> = mutex.withLock {
        ensureLoadedLocked()
        var bytes = 0
        for ((k, v) in map) {
            bytes += k.toByteArray(Charsets.UTF_8).size + v.toByteArray(Charsets.UTF_8).size
        }
        map.size to bytes
    }

    suspend fun clear() {
        mutex.withLock {
            map.clear()
            writeJob?.cancel()
            writeJob = null
        }
        runCatching {
            context.translationDataStore.edit { it[storageKey] = "{}" }
        }
    }

    private suspend fun persist() {
        val snapshot: LinkedHashMap<String, String>
        mutex.withLock { snapshot = LinkedHashMap(map) }
        if (snapshot.isEmpty()) return
        val json = JsonObject(snapshot.mapValues { (_, v) -> JsonPrimitive(v) }).toString()
        runCatching {
            context.translationDataStore.edit { it[storageKey] = json }
        } // 写失败静默：缓存坏永不影响翻译
    }
}
