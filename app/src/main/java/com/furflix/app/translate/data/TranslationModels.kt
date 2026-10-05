package com.furflix.app.translate.data

/**
 * 翻译服务商（五家内置预设；蓝本 e1547 translation.md §3）。
 * 默认为 EDGE：免密钥且大陆可达——"打开开关即用"对中文用户成立。
 */
enum class TranslationProvider(val id: String, val shortLabel: String) {
    GOOGLE("google", "Google"),
    CHROME("chrome", "Google (Chrome)"),
    EDGE("edge", "Microsoft Edge"),
    AZURE("azure", "Azure"),
    OPENAI("openai", "OpenAI");

    companion object {
        fun fromId(id: String): TranslationProvider =
            entries.firstOrNull { it.id == id } ?: EDGE
    }
}

/** 可翻译内容类别（蓝本 7 类裁至本项目存在的 5 类）。 */
enum class TranslationCategory(val id: String) {
    DESCRIPTION("description"),
    TITLE("title"),
    TAG("tag"),
    COMMENT("comment"),
    USER_PROFILE("userProfile");

    companion object {
        fun fromId(id: String): TranslationCategory? = entries.firstOrNull { it.id == id }
    }
}

/** 双语对照 / 仅显示译文（缺省双语；键存 translateDisplayModes）。 */
enum class TranslationDisplayMode(val id: String) {
    BILINGUAL("bilingual"),
    TRANSLATION_ONLY("translationOnly");

    companion object {
        fun fromId(id: String): TranslationDisplayMode? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 小说正文翻译的切分粒度：整段（默认，请求数 = 段落数）或整句
 * （逐句送翻，翻译质量更稳但请求数大增，长文请配合并发与缓存使用）。
 * 只影响送翻单元的切分，展示仍按"简介"类别的双语/仅译文方式逐段渲染。
 */
enum class StoryGranularity(val id: String) {
    PARAGRAPH("paragraph"),
    SENTENCE("sentence");

    companion object {
        fun fromId(id: String): StoryGranularity? = entries.firstOrNull { it.id == id }
    }
}

/** 执行器内部错误串（与蓝本一字不差；UI 层做本地化映射）。 */
object TranslationErrors {
    const val TIMEOUT = "connection timed out"
    const val CONNECTION_FAILED = "connection failed"
    const val INVALID_KEY = "invalid API key"
    const val RATE_LIMITED = "rate limited"
    const val CANCELLED = "request cancelled"
    const val NETWORK = "network error"
    const val EMPTY = "empty translation"
    const val BAD_FORMAT = "unexpected response format"
    const val NO_KEY = "no API key configured"
    const val NOTHING = "nothing to translate"

    fun serverError(status: Int) = "server error ($status)"
}

/** 携带 HTTP 状态码与服务器响应摘录（detail ≤200 字符）的翻译异常。 */
class TranslationException(
    message: String,
    val code: Int? = null,
    val detail: String? = null,
) : Exception(message)

/** 蓝本重试判定：配置类错误与取消不重试；本项目追加——4xx（除 408/429）是确定性
 *  客户端错误，同载荷重试无意义，应立即走降级路径。 */
val TranslationException.isRetryable: Boolean
    get() = when (message) {
        TranslationErrors.NO_KEY,
        TranslationErrors.INVALID_KEY,
        TranslationErrors.CANCELLED,
        TranslationErrors.NOTHING -> false
        else -> !(code != null && code in 400..499 && code != 408 && code != 429)
    }

/** 内置目标语言表（15 种；母语名恒显，不本地化——与语言选择器原则一致）。 */
val TRANSLATION_LANGUAGES: List<Pair<String, String>> = listOf(
    "zh-CN" to "简体中文",
    "zh-TW" to "繁體中文",
    "en" to "English",
    "ja" to "日本語",
    "ko" to "한국어",
    "ru" to "Русский",
    "es" to "Español",
    "fr" to "Français",
    "de" to "Deutsch",
    "pt" to "Português",
    "it" to "Italiano",
    "th" to "ไทย",
    "vi" to "Tiếng Việt",
    "id" to "Bahasa Indonesia",
    "ar" to "العربية",
)

fun languageNativeName(code: String): String =
    TRANSLATION_LANGUAGES.firstOrNull { it.first == code }?.second ?: code

const val DEFAULT_SYSTEM_PROMPT =
    "You are a professional, authentic machine translation engine."

// [FurFlix 改良] 蓝本 prompt 提及 DText（e621 语境）；FA 是 HTML，改为保留 HTML 标签。
const val DEFAULT_USER_PROMPT_TEMPLATE =
    "Translate the following text into @toLang. Preserve the original formatting: " +
        "HTML tags and line breaks must remain intact. If the text is already in the " +
        "target language, return it unchanged. Return only the translation, without " +
        "any explanation:\n\n@text"

/** 一次 translate 调用所需的完整配置快照（自 DataStore 聚合）。 */
data class TranslationConfig(
    val provider: TranslationProvider = TranslationProvider.EDGE,
    val targetLanguage: String = "zh-CN",
    val azureKey: String = "",
    val apiKey: String = "",
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-4o-mini",
    val systemPrompt: String = "",
    val userPrompt: String = "",
    val maxConcurrency: Int = 1,
    val requestIntervalMs: Int = 0,
    val timeoutSeconds: Int = 30,
    val retryCount: Int = 2,
    val maxTextLength: Int = 1200,
    val maxParagraphs: Int = 0,
    val cacheLimit: Int = 500,
)

/**
 * 中文跳过检测（蓝本 §10.4）：非空白码点中汉字占比 >25% 且不含假名或谚文 → 判中文。
 * 目标语言为 zh* 且原文判中文时跳过自动翻译。
 */
fun translationLooksChinese(text: String): Boolean {
    var han = 0
    var total = 0
    var hasKanaOrHangul = false
    var i = 0
    val n = text.length
    while (i < n) {
        val cp = Character.codePointAt(text, i)
        i += Character.charCount(cp)
        if (Character.isWhitespace(cp)) continue
        total++
        when (cp) {
            in 0x4E00..0x9FFF, in 0x3400..0x4DBF -> han++
            in 0x3040..0x30FF, in 0xAC00..0xD7AF -> hasKanaOrHangul = true
        }
    }
    if (total == 0 || hasKanaOrHangul) return false
    return han * 100 > total * 25
}
