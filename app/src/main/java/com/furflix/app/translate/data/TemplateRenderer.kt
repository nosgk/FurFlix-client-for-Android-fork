package com.furflix.app.translate.data

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.net.URLEncoder

/**
 * 模板渲染（蓝本 §4）。@token 引用变量；同一份变量表驱动正式请求与预览。
 *
 * 两条关键规则（决定移植正确性）：
 * 1. URL/query 中变量替换后百分号编码；@baseUrl 例外原样（编码会破坏 https://）。
 * 2. body 中 "@token"（带引号）→ 完整 JSON 字符串字面量（保转义）；裸 @token → 原样插入。
 */

/** JVM 侧对齐 Dart Uri.encodeComponent：URLEncoder 是表单语义（空格 → +），须转回 %20。 */
internal fun encodeComponent(s: String): String =
    URLEncoder.encode(s, "UTF-8").replace("+", "%20")

/** 等价 Dart jsonEncode：产出含引号与转义的完整 JSON 字符串字面量。 */
internal fun jsonLiteral(s: String): String = Json.encodeToString(String.serializer(), s)

// 单趟扫描 + 长 token 优先：已替换内容不参与二次匹配（注入文本含 @token 也不会被误替换）
private fun bareTokenRegex(keys: Collection<String>): Regex =
    Regex("@(" + keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } + ")")

/** header 值等纯文本：@token 原样替换。 */
fun renderPlainTemplate(template: String, vars: Map<String, String>): String {
    if (vars.isEmpty()) return template
    return bareTokenRegex(vars.keys).replace(template) { m -> vars[m.groupValues[1]].orEmpty() }
}

/** URL 模板：@baseUrl 原样，其余变量替换后百分号编码。 */
fun renderUrlTemplate(template: String, vars: Map<String, String>): String {
    if (vars.isEmpty()) return template
    return bareTokenRegex(vars.keys).replace(template) { m ->
        val key = m.groupValues[1]
        val value = vars[key].orEmpty()
        if (key == "baseUrl") value else encodeComponent(value)
    }
}

/**
 * JSON body：单趟正则同时匹配 "@token"（含引号，JSON 转义）与裸 @token（原样）——
 * 引号形式优先于裸形式，替换产物不被重扫。
 */
fun renderJsonTemplate(template: String, vars: Map<String, String>): String {
    if (vars.isEmpty()) return template
    val alt = vars.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
    val combined = Regex("\"@($alt)\"|@($alt)")
    return combined.replace(template) { m ->
        val quotedKey = m.groupValues[1]
        val bareKey = m.groupValues[2]
        if (quotedKey.isNotEmpty()) jsonLiteral(vars[quotedKey].orEmpty())
        else vars[bareKey].orEmpty()
    }
}

/** 渲染 url 模板后按 key=value 追加 query（? 或 & 自动选择）。 */
fun buildRequestUrl(profile: TranslationRequestProfile, vars: Map<String, String>): String {
    val rendered = renderUrlTemplate(profile.url, vars)
    if (profile.query.isEmpty()) return rendered
    val qs = profile.query.joinToString("&") { pair ->
        "${renderUrlTemplate(pair[0], vars)}=${renderUrlTemplate(pair.getOrElse(1) { "" }, vars)}"
    }
    return rendered + (if (rendered.contains('?')) "&" else "?") + qs
}
