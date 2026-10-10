package com.furflix.app.translate.data

/**
 * HTML 实体解码(纯 Kotlin,供单测;不依赖 android.text.Html)。
 *
 * ## 背景(K-007)
 * 翻译结果中的 `<3`(爱心)经上游 HTML 转义后变成 `&lt; 3`,
 * 而译文展示走的是 Compose `Text`,**不经过** `HtmlCompat.fromHtml`,
 * 于是转义串直接暴露给用户(截图可见 `&lt; 3`)。
 *
 * 对比:作品描述/简介走 `HtmlTextView` → `HtmlCompat.fromHtml`,无此问题;
 * 所以缺陷只出现在"纯文本类别"(评论、简介译文)这条路径上。
 *
 * ## 范围
 * 只解码**文本**实体,不处理标签:译文是纯文本,`Text` 也不渲染标签。
 * 刻意不把 `<br>`/`<p>` 转成换行——`Text` 本身按 `\n` 换行,
 * 若上游送来 `<br>` 应保留字面量,避免误吞用户内容。
 */
object HtmlEntities {

    /** 命名实体 → 字符。仅收录文本类,够覆盖翻译/评论场景。 */
    private val NAMED: Map<String, String> = mapOf(
        "lt" to "<",
        "gt" to ">",
        "amp" to "&",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
        // 常见排版符号:翻译结果里高频出现
        "hellip" to "…",
        "mdash" to "—",
        "ndash" to "–",
        "lsquo" to "‘",
        "rsquo" to "’",
        "ldquo" to "“",
        "rdquo" to "”",
        "laquo" to "«",
        "raquo" to "»",
        "deg" to "°",
        "times" to "×",
        "copy" to "©",
        "reg" to "®",
        "trade" to "™",
        "euro" to "€",
        "sect" to "§",
    )

    private val NAMED_ENTITY = Regex("""&([a-zA-Z][a-zA-Z0-9]{0,31});""")
    private val NUMERIC_ENTITY = Regex("""&#(x[0-9a-fA-F]+|[0-9]+);""")

    /**
     * 解码文本实体。未知实体保持原样(不猜测、不吞内容)。
     *
     * 数字实体支持十进制(`&#60;`)与十六进制(`&#x3C;`);
     * 越界或非法码点保留字面量。
     *
     * 注意:`Int.toChar()` 在 Kotlin 里是**静默截断**(取低 16 位),不抛异常,
     * 直接用它会把 `&#999999999;` 变成某个无关字符。故必须显式校验码点范围。
     */
    fun decode(text: String): String {
        if ('&' !in text) return text

        var out = NUMERIC_ENTITY.replace(text) { m ->
            val body = m.groupValues[1]
            val code = if (body.startsWith("x", ignoreCase = true)) {
                body.drop(1).toIntOrNull(16)
            } else {
                body.toIntOrNull(10)
            }
            if (code == null || !isValidCodePoint(code)) {
                m.value
            } else {
                // Character.toChars 支持增补平面(> 0xFFFF),越界会抛异常,兜底保留字面量
                runCatching { String(Character.toChars(code)) }.getOrDefault(m.value)
            }
        }

        out = NAMED_ENTITY.replace(out) { m ->
            NAMED[m.groupValues[1]] ?: m.value
        }

        return out
    }

    /** 合法 Unicode 标量值:0..0x10FFFF,且不在代理区(0xD800..0xDFFF)。 */
    private fun isValidCodePoint(code: Int): Boolean =
        code in 0..0x10FFFF && code !in 0xD800..0xDFFF
}
