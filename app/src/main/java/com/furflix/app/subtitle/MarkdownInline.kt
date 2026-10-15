package com.furflix.app.subtitle

/** 行内样式片段：文本 + 是否加粗/斜体。UI 层据此拼 AnnotatedString。 */
data class InlineSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false
)

/**
 * 字幕文本的行内 Markdown 解析（只处理 `**粗体**` 与 `*斜体*`）。
 *
 * 提示词要求模型用 `**` 标注拟声/重音（如 `**BURP**`），故渲染前必须把标记摘出来
 * 变成样式，否则观众会看到字面的星号。刻意不处理 `_斜体_`：台词里的下划线比星号
 * 常见得多，误吞代价大于收益。
 *
 * 纯函数、无 Android 依赖，可直接单测。
 */
object MarkdownInline {

    fun parse(text: String): List<InlineSpan> {
        if (text.isEmpty()) return emptyList()
        val spans = mutableListOf<InlineSpan>()
        val buffer = StringBuilder()
        var bold = false
        var italic = false

        fun flush() {
            if (buffer.isNotEmpty()) {
                spans.add(InlineSpan(buffer.toString(), bold, italic))
                buffer.clear()
            }
        }

        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    flush()
                    bold = !bold
                    i += 2
                }
                text[i] == '*' -> {
                    flush()
                    italic = !italic
                    i += 1
                }
                else -> {
                    buffer.append(text[i])
                    i += 1
                }
            }
        }
        flush()
        return spans
    }
}
