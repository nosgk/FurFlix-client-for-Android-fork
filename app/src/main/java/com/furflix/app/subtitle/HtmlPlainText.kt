package com.furflix.app.subtitle

import com.furflix.app.translate.data.HtmlEntities

/**
 * 作品描述在抓取层是 HTML（`descNode.html()`，含 `<p>`/`<a href>` 与实体转义），
 * 直接注入提示词会让模型读到标签噪音、也白占上下文。这里先转纯文本。
 *
 * 只做三件事：块级标签转成换行、剥掉其余标签、解码文本实体。实体表复用翻译模块的
 * [HtmlEntities]（同一份纯 Kotlin 实现 + 同一套单测覆盖），不重复维护。
 */
object HtmlPlainText {

    /** 有换行语义的标签：转成 \n 而不是直接删（否则段落会粘连成一行）。 */
    private val BLOCK_BREAK = Regex("""(?i)<\s*(br|/p|/div|/li|/h[1-6]|/tr)\s*/?\s*>""")

    private val ANY_TAG = Regex("""<[^>]*>""")
    private val BLANK_LINES = Regex("""\n{3,}""")
    private val TRAILING_SPACES = Regex("""[ \t]+\n""")

    fun toPlainText(html: String): String {
        if (html.isBlank()) return ""
        val withBreaks = BLOCK_BREAK.replace(html, "\n")
        val stripped = ANY_TAG.replace(withBreaks, "")
        val decoded = HtmlEntities.decode(stripped)
        return decoded
            .replace(TRAILING_SPACES, "\n")
            .replace(BLANK_LINES, "\n\n")
            .trim()
    }
}
