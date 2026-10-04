package com.furflix.app.translate.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 一份 HTTP 请求的完整描述（蓝本 §2）。所有内置/自定义服务商共用此抽象，
 * 执行器不认识任何具体服务商——只渲染模板、发请求、按解析规则取译文。
 */
@Serializable
data class TranslationRequestProfile(
    val method: String = "POST",                  // 仅 GET | POST
    val url: String = "",                         // URL 模板，可自带内联 query
    val query: List<List<String>> = emptyList(),  // 追加 query，保序 [k, v]
    val headers: List<List<String>> = emptyList(),
    val contentType: String = "application/json", // 无显式头且 POST 有 body 时的默认；GET 忽略
    val body: String = "",                        // body 模板；GET 为空
    val parsePath: String = "",                   // 迷你 JSONPath；空 = 整个响应体即译文
    val stripThink: Boolean = false,              // 去 <think>…</think> 与代码围栏（AI 接口用）
    // [FurFlix 增补] HTML 输入直通：true 时 DESCRIPTION 类别原样发送 HTML、译文按 HTML 渲染；
    // false 时 DESCRIPTION 先剥标签再发送。纯文本类别不受影响。
    val htmlIn: Boolean = false,
)

private val profileJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun TranslationRequestProfile.encodeToJson(): String =
    profileJson.encodeToString(TranslationRequestProfile.serializer(), this)

fun decodeProfileOrNull(json: String?): TranslationRequestProfile? {
    if (json.isNullOrBlank()) return null
    return runCatching {
        profileJson.decodeFromString(TranslationRequestProfile.serializer(), json)
    }.getOrNull() // 坏数据回落内置预设
}

/** Chrome 随安装包分发的公开 API Key（Chrome 源码可见，非私人密钥；与蓝本一致明文收录）。 */
const val CHROME_API_KEY = "AIzaSyATBXajvzQLTDHEQbcpq0Ihe0vWDHmO520"

/**
 * 内置五家预设（蓝本 §3 的 url/query/headers/body/parse 原样移植）。
 * curl 验证样例与响应形状见 docs/translation-plan.md §2.3。
 */
fun builtInProfile(provider: TranslationProvider): TranslationRequestProfile = when (provider) {
    TranslationProvider.GOOGLE -> TranslationRequestProfile(
        method = "GET",
        url = "https://translate.googleapis.com/translate_a/single",
        query = listOf(
            listOf("client", "gtx"),
            listOf("sl", "auto"),
            listOf("tl", "@toLang"),
            listOf("dt", "t"),
            listOf("q", "@text"),
        ),
        contentType = "application/json",
        parsePath = "\$[0][*][0]",
    )

    TranslationProvider.CHROME -> TranslationRequestProfile(
        method = "POST",
        url = "https://translate-pa.googleapis.com/v1/translateHtml",
        headers = listOf(listOf("X-Goog-API-Key", CHROME_API_KEY)),
        // JSON 序列化的 protobuf 载荷——只是 Content-Type，无需 protobuf 库
        contentType = "application/json+protobuf",
        body = """[[["@text"],"@fromLang","@toLang"],"wt_lib"]""",
        parsePath = "\$[0][0]",
        htmlIn = true,
    )

    TranslationProvider.EDGE -> TranslationRequestProfile(
        method = "POST",
        url = "https://edge.microsoft.com/translate/translatetext",
        query = listOf(listOf("from", ""), listOf("to", "@toLang")), // from 空 = 自动检测
        headers = listOf(listOf("Accept", "application/json")),
        contentType = "application/json",
        body = """["@text"]""",
        parsePath = "\$[0].translations[0].text",
    )

    TranslationProvider.AZURE -> TranslationRequestProfile(
        method = "POST",
        url = "https://api.cognitive.microsofttranslator.com/translate",
        query = listOf(
            listOf("api-version", "3.0"),
            listOf("to", "@toLang"),
            listOf("textType", "plain"),
        ),
        headers = listOf(
            listOf("Ocp-Apim-Subscription-Key", "@azureKey"),
            listOf("Accept", "application/json"),
        ),
        contentType = "application/json",
        body = """[{"Text":"@text"}]""",
        parsePath = "\$[0].translations[0].text",
    )

    TranslationProvider.OPENAI -> TranslationRequestProfile(
        method = "POST",
        url = "@baseUrl/chat/completions", // @BaseUrl 原样替换，不编码
        headers = listOf(listOf("Authorization", "Bearer @apiKey")),
        contentType = "application/json",
        body = """{"model":"@model","temperature":0.3,"messages":[""" +
            """{"role":"system","content":"@systemPrompt"},""" +
            """{"role":"user","content":"@userPrompt"}]}""",
        parsePath = "\$.choices[0].message.content",
        stripThink = true,
        htmlIn = true,
    )
}

/**
 * 组装一次请求的完整变量表；正式请求与 M2 配置器预览共用（"预览即所得"）。
 * @userPrompt 进表前已完成内部替换（@toLang → 目标语言母语名，@text → 原文）。
 */
fun translationTemplateVars(config: TranslationConfig, text: String): Map<String, String> {
    val toLangName = languageNativeName(config.targetLanguage)
    val userPrompt = (config.userPrompt.ifBlank { DEFAULT_USER_PROMPT_TEMPLATE })
        // 先长后短，避免 @toLang 吃掉 @toLangName 的前缀
        .replace("@toLangName", toLangName)
        .replace("@toLang", toLangName)
        .replace("@text", text)
    return mapOf(
        "text" to text,
        "toLang" to config.targetLanguage,
        "toLangName" to toLangName,
        "fromLang" to "auto",
        "apiKey" to config.apiKey,
        "azureKey" to config.azureKey,
        "baseUrl" to config.baseUrl.trim().trimEnd('/'),
        "model" to config.model,
        "systemPrompt" to config.systemPrompt.ifBlank { DEFAULT_SYSTEM_PROMPT },
        "userPrompt" to userPrompt,
    )
}
