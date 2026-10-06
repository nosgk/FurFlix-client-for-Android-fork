package com.furflix.app.translate.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

private val K_ENABLED = booleanPreferencesKey("translateEnabled")
/** 旧版全局"自动翻译内容"开关：仅作逐项开关缺失时的迁移回落值读取，不再写入 */
private val K_AUTO = booleanPreferencesKey("translateAuto")
private val K_TAGS_AUTO = booleanPreferencesKey("translateTagsAuto")
/** 逐类别自动翻译开关（2026-10 拆分自全局 translateAuto；旧用户自动迁移） */
private val K_TITLE_AUTO = booleanPreferencesKey("translateTitleAuto")
private val K_DESCRIPTION_AUTO = booleanPreferencesKey("translateDescriptionAuto")
private val K_COMMENT_AUTO = booleanPreferencesKey("translateCommentAuto")
private val K_PROFILE_AUTO = booleanPreferencesKey("translateProfileAuto")
private val K_JOURNAL_AUTO = booleanPreferencesKey("translateJournalAuto")
private val K_DISPLAY_MODES = stringPreferencesKey("translateDisplayModes")
private val K_STORY_GRANULARITY = stringPreferencesKey("translateStoryGranularity")
private val K_TARGET_LANGUAGE = stringPreferencesKey("translateTargetLanguage")
private val K_PROVIDER = stringPreferencesKey("translateProvider")
private val K_AZURE_KEY = stringPreferencesKey("translateAzureKey")
private val K_API_KEY = stringPreferencesKey("translateApiKey")
private val K_BASE_URL = stringPreferencesKey("translateBaseUrl")
private val K_MODEL = stringPreferencesKey("translateModel")
private val K_SYSTEM_PROMPT = stringPreferencesKey("translateSystemPrompt")
private val K_USER_PROMPT = stringPreferencesKey("translateUserPrompt")
private val K_CONCURRENCY = intPreferencesKey("translateConcurrency")
private val K_INTERVAL_MS = intPreferencesKey("translateIntervalMs")
private val K_TIMEOUT_SECONDS = intPreferencesKey("translateTimeoutSeconds")
private val K_RETRY_COUNT = intPreferencesKey("translateRetryCount")
private val K_MAX_TEXT_LENGTH = intPreferencesKey("translateMaxTextLength")
private val K_MAX_PARAGRAPHS = intPreferencesKey("translateMaxParagraphs")
private val K_CACHE_LIMIT = intPreferencesKey("translateCacheLimit")

// M2 高级配置器使用：每服务商 Profile 覆盖（空 = 内置预设）。M1 仅占键位。
@Suppress("unused")
private val K_PROFILE_EDGE = stringPreferencesKey("translateProfileEdge")

@Suppress("unused")
private val K_PROFILE_GOOGLE = stringPreferencesKey("translateProfileGoogle")

@Suppress("unused")
private val K_PROFILE_CHROME = stringPreferencesKey("translateProfileChrome")

@Suppress("unused")
private val K_PROFILE_AZURE = stringPreferencesKey("translateProfileAzure")

@Suppress("unused")
private val K_PROFILE_OPENAI = stringPreferencesKey("translateProfileOpenAI")

/** UI 层消费的聚合快照。 */
data class TranslationUiPrefs(
    val enabled: Boolean = false,
    // [FurFlix 偏离蓝本] 标签自动翻译默认开：FA 标签短小廉价（缓存+合并兜底），
    // 且"启用翻译即标签可译"符合 zh-first 用户预期。用户显式关闭后存键生效。
    val tagsAuto: Boolean = true,
    val titleAuto: Boolean = false,
    val descriptionAuto: Boolean = false,
    val commentAuto: Boolean = false,
    val profileAuto: Boolean = false,
    val journalAuto: Boolean = false,
    val displayModes: Map<TranslationCategory, TranslationDisplayMode> = emptyMap(),
    // 小说正文翻译切分粒度（默认整段）
    val storyGranularity: StoryGranularity = StoryGranularity.PARAGRAPH,
) {
    fun displayMode(category: TranslationCategory): TranslationDisplayMode =
        displayModes[category] ?: TranslationDisplayMode.BILINGUAL

    /** 逐类别自动翻译开关（原全局开关已按类别拆分，TAG 沿用独立旧键） */
    fun autoFor(category: TranslationCategory): Boolean = when (category) {
        TranslationCategory.TITLE -> titleAuto
        TranslationCategory.DESCRIPTION -> descriptionAuto
        TranslationCategory.TAG -> tagsAuto
        TranslationCategory.COMMENT -> commentAuto
        TranslationCategory.USER_PROFILE -> profileAuto
        TranslationCategory.JOURNAL -> journalAuto
    }

    companion object {
        val DEFAULT = TranslationUiPrefs()
    }
}

/** `{"description":"translationOnly",…}` → 类别映射；缺省类别 = bilingual。 */
fun parseDisplayModes(json: String): Map<TranslationCategory, TranslationDisplayMode> {
    if (json.isBlank()) return emptyMap()
    return runCatching {
        val obj = Json.parseToJsonElement(json).jsonObject
        obj.entries.mapNotNull { (k, v) ->
            val category = TranslationCategory.fromId(k) ?: return@mapNotNull null
            val modeId = (v as? JsonPrimitive)?.content ?: return@mapNotNull null
            val mode = TranslationDisplayMode.fromId(modeId) ?: return@mapNotNull null
            category to mode
        }.toMap()
    }.getOrDefault(emptyMap())
}

/**
 * 翻译设置仓（独立 translate_prefs 文件）。所有消费方（设置页 / entry / service）
 * 统一经此类访问，禁止散落直读 DataStore——键名与默认值只在这里出现一次。
 */
class TranslationSettings private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: TranslationSettings? = null

        fun getInstance(context: Context): TranslationSettings =
            instance ?: synchronized(this) {
                instance ?: TranslationSettings(context.applicationContext).also { instance = it }
            }
    }

    val uiPrefs: Flow<TranslationUiPrefs> = context.translationDataStore.data.map { p ->
        TranslationUiPrefs(
            enabled = p[K_ENABLED] ?: false,
            // 逐项键缺失时回落旧全局开关值（老用户迁移：原来全开就继续全开）
            titleAuto = p[K_TITLE_AUTO] ?: (p[K_AUTO] ?: false),
            descriptionAuto = p[K_DESCRIPTION_AUTO] ?: (p[K_AUTO] ?: false),
            commentAuto = p[K_COMMENT_AUTO] ?: (p[K_AUTO] ?: false),
            profileAuto = p[K_PROFILE_AUTO] ?: (p[K_AUTO] ?: false),
            journalAuto = p[K_JOURNAL_AUTO] ?: (p[K_AUTO] ?: false),
            tagsAuto = p[K_TAGS_AUTO] ?: true, // 默认开，见 TranslationUiPrefs 注释
            displayModes = parseDisplayModes(p[K_DISPLAY_MODES] ?: "{}"),
            storyGranularity = StoryGranularity.fromId(p[K_STORY_GRANULARITY] ?: "")
                ?: StoryGranularity.PARAGRAPH,
        )
    }

    val config: Flow<TranslationConfig> = context.translationDataStore.data.map { p ->
        TranslationConfig(
            provider = TranslationProvider.fromId(p[K_PROVIDER] ?: ""),
            targetLanguage = p[K_TARGET_LANGUAGE] ?: "zh-CN",
            azureKey = p[K_AZURE_KEY] ?: "",
            apiKey = p[K_API_KEY] ?: "",
            baseUrl = p[K_BASE_URL] ?: "https://api.openai.com/v1",
            model = p[K_MODEL] ?: "gpt-4o-mini",
            systemPrompt = p[K_SYSTEM_PROMPT] ?: "",
            userPrompt = p[K_USER_PROMPT] ?: "",
            maxConcurrency = (p[K_CONCURRENCY] ?: 1).coerceIn(1, 8),
            requestIntervalMs = (p[K_INTERVAL_MS] ?: 0).coerceAtLeast(0),
            timeoutSeconds = (p[K_TIMEOUT_SECONDS] ?: 30).coerceIn(5, 120),
            retryCount = (p[K_RETRY_COUNT] ?: 2).coerceIn(0, 5),
            maxTextLength = p[K_MAX_TEXT_LENGTH] ?: 1200,
            maxParagraphs = p[K_MAX_PARAGRAPHS] ?: 0,
            cacheLimit = p[K_CACHE_LIMIT] ?: 500,
        )
    }

    // ---- savers（设置页用；空 prompt = 回落内置默认）----

    suspend fun saveEnabled(v: Boolean) = edit { it[K_ENABLED] = v }
    suspend fun saveTitleAuto(v: Boolean) = edit { it[K_TITLE_AUTO] = v }
    suspend fun saveDescriptionAuto(v: Boolean) = edit { it[K_DESCRIPTION_AUTO] = v }
    suspend fun saveCommentAuto(v: Boolean) = edit { it[K_COMMENT_AUTO] = v }
    suspend fun saveProfileAuto(v: Boolean) = edit { it[K_PROFILE_AUTO] = v }
    suspend fun saveJournalAuto(v: Boolean) = edit { it[K_JOURNAL_AUTO] = v }
    suspend fun saveTagsAuto(v: Boolean) = edit { it[K_TAGS_AUTO] = v }
    suspend fun saveStoryGranularity(v: StoryGranularity) = edit { it[K_STORY_GRANULARITY] = v.id }
    suspend fun saveConcurrency(v: Int) = edit { it[K_CONCURRENCY] = v.coerceIn(1, 8) }
    suspend fun saveTargetLanguage(v: String) = edit { it[K_TARGET_LANGUAGE] = v }
    suspend fun saveProvider(v: TranslationProvider) = edit { it[K_PROVIDER] = v.id }
    suspend fun saveAzureKey(v: String) = edit { it[K_AZURE_KEY] = v }
    suspend fun saveApiKey(v: String) = edit { it[K_API_KEY] = v }
    suspend fun saveBaseUrl(v: String) = edit { it[K_BASE_URL] = v }
    suspend fun saveModel(v: String) = edit { it[K_MODEL] = v }
    suspend fun saveSystemPrompt(v: String) = edit { it[K_SYSTEM_PROMPT] = v }
    suspend fun saveUserPrompt(v: String) = edit { it[K_USER_PROMPT] = v }

    suspend fun saveDisplayMode(category: TranslationCategory, mode: TranslationDisplayMode) {
        edit { prefs ->
            val current = parseDisplayModes(prefs[K_DISPLAY_MODES] ?: "{}").toMutableMap()
            // 缺省即双语：BILINGUAL 不落盘，保持键值最小
            if (mode == TranslationDisplayMode.BILINGUAL) current.remove(category)
            else current[category] = mode
            prefs[K_DISPLAY_MODES] = if (current.isEmpty()) "{}"
            else current.entries.joinToString(",", "{", "}") { (k, m) -> "\"${k.id}\":\"${m.id}\"" }
        }
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.translationDataStore.edit(block)
    }
}
