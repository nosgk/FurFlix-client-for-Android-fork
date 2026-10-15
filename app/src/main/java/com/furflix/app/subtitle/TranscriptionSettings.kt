package com.furflix.app.subtitle

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 字幕功能专用 DataStore（独立 subtitle_prefs 文件，与 translate_prefs /
 * furflix_prefs 互不干扰）：键位与默认值只在本文件出现一次。
 */
val Context.subtitleDataStore: DataStore<Preferences> by preferencesDataStore(name = "subtitle_prefs")

private val K_ENABLED = booleanPreferencesKey("subtitleEnabled")
private val K_BASE_URL = stringPreferencesKey("subtitleBaseUrl")
private val K_API_KEY = stringPreferencesKey("subtitleApiKey")
private val K_MODEL = stringPreferencesKey("subtitleModel")
private val K_TEMPLATE = stringPreferencesKey("subtitleTemplate")
private val K_TIMEOUT = intPreferencesKey("subtitleTimeoutSeconds")
private val K_PRESETS = stringPreferencesKey("subtitlePresets")
private val K_ACTIVE_PRESETS = stringPreferencesKey("subtitleActivePresets")

/** 字幕生成服务的配置快照。 */
data class TranscriptionConfig(
    val enabled: Boolean = false,
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = "",
    val template: String = TranscriptionPrompt.DEFAULT_TEMPLATE,
    val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS
) {
    /** 三项齐备才算可用：缺任一项发起请求只会得到 4xx。 */
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com"
        const val DEFAULT_TIMEOUT_SECONDS = 180
    }
}

/**
 * 字幕设置仓。消费方（设置页 / 播放器）统一经此类访问，禁止散落直读 DataStore。
 */
class TranscriptionSettings private constructor(private val context: Context) {

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val presetSerializer = ListSerializer(StylePreset.serializer())
        private val idSerializer = ListSerializer(String.serializer())

        @Volatile
        private var instance: TranscriptionSettings? = null

        fun getInstance(context: Context): TranscriptionSettings =
            instance ?: synchronized(this) {
                instance ?: TranscriptionSettings(context.applicationContext).also { instance = it }
            }

        /** 预设解码：未写过 / 坏 JSON / 空表一律回落内置默认预设。 */
        fun decodePresets(raw: String?): List<StylePreset> {
            if (raw.isNullOrBlank()) return TranscriptionPrompt.DEFAULT_PRESETS
            val decoded = runCatching { json.decodeFromString(presetSerializer, raw) }.getOrNull()
            return if (decoded.isNullOrEmpty()) TranscriptionPrompt.DEFAULT_PRESETS else decoded
        }

        fun decodeActiveIds(raw: String?): Set<String> {
            if (raw.isNullOrBlank()) return emptySet()
            return runCatching { json.decodeFromString(idSerializer, raw) }
                .getOrDefault(emptyList())
                .toSet()
        }
    }

    val config: Flow<TranscriptionConfig> = context.subtitleDataStore.data.map { prefs ->
        TranscriptionConfig(
            enabled = prefs[K_ENABLED] ?: false,
            baseUrl = prefs[K_BASE_URL] ?: TranscriptionConfig.DEFAULT_BASE_URL,
            apiKey = prefs[K_API_KEY] ?: "",
            model = prefs[K_MODEL] ?: "",
            template = prefs[K_TEMPLATE]?.takeIf { it.isNotBlank() }
                ?: TranscriptionPrompt.DEFAULT_TEMPLATE,
            timeoutSeconds = prefs[K_TIMEOUT] ?: TranscriptionConfig.DEFAULT_TIMEOUT_SECONDS
        )
    }

    val presets: Flow<List<StylePreset>> =
        context.subtitleDataStore.data.map { decodePresets(it[K_PRESETS]) }

    val activePresetIds: Flow<Set<String>> =
        context.subtitleDataStore.data.map { decodeActiveIds(it[K_ACTIVE_PRESETS]) }

    /** 启用中的预设拼成的额外风格提示词（发起请求时注入模板）。 */
    val extraStylePrompt: Flow<String> = combine(presets, activePresetIds) { list, ids ->
        TranscriptionPrompt.combinePresets(list, ids)
    }

    suspend fun saveEnabled(value: Boolean) = edit { it[K_ENABLED] = value }

    suspend fun saveBaseUrl(value: String) = edit { it[K_BASE_URL] = value.trim() }

    suspend fun saveApiKey(value: String) = edit { it[K_API_KEY] = value.trim() }

    suspend fun saveModel(value: String) = edit { it[K_MODEL] = value.trim() }

    suspend fun saveTemplate(value: String) = edit { it[K_TEMPLATE] = value }

    suspend fun saveTimeoutSeconds(value: Int) = edit { it[K_TIMEOUT] = value.coerceIn(30, 600) }

    suspend fun savePresets(list: List<StylePreset>) =
        edit { it[K_PRESETS] = json.encodeToString(presetSerializer, list) }

    suspend fun saveActivePresetIds(ids: Set<String>) =
        edit { it[K_ACTIVE_PRESETS] = json.encodeToString(idSerializer, ids.toList()) }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.subtitleDataStore.edit(block)
    }
}
