package com.furflix.app.translate

import com.furflix.app.translate.data.HtmlEntities
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * HTML 实体解码 —— K-007 回归锁定。
 *
 * 用户可见缺陷:翻译结果里的 `<3`(爱心)被上游转义成 `&lt; 3` 后,
 * 译文经 Compose `Text` 直出(不经 HtmlCompat),转义串原样显示。
 */
class HtmlEntitiesTest {

    // ---------------------------------------------------------------- K-007

    @Test
    fun `decodes escaped heart in translated text`() {
        assertEquals("I love this < 3", HtmlEntities.decode("I love this &lt; 3"))
    }

    @Test
    fun `decodes core named entities`() {
        assertEquals("<", HtmlEntities.decode("&lt;"))
        assertEquals(">", HtmlEntities.decode("&gt;"))
        assertEquals("&", HtmlEntities.decode("&amp;"))
        assertEquals("\"", HtmlEntities.decode("&quot;"))
        assertEquals("'", HtmlEntities.decode("&apos;"))
    }

    // ---------------------------------------------------------------- 数字实体

    @Test
    fun `decodes decimal numeric entities`() {
        assertEquals("<", HtmlEntities.decode("&#60;"))
        assertEquals("A", HtmlEntities.decode("&#65;"))
    }

    @Test
    fun `decodes hexadecimal numeric entities`() {
        assertEquals("<", HtmlEntities.decode("&#x3C;"))
        assertEquals("<", HtmlEntities.decode("&#x3c;"))
    }

    @Test
    fun `keeps out of range numeric entities literal`() {
        // Kotlin 的 Int.toChar() 会静默截断,必须显式校验码点范围才不误转
        assertEquals("&#999999999;", HtmlEntities.decode("&#999999999;"))
        assertEquals("&#1114112;", HtmlEntities.decode("&#1114112;")) // > 0x10FFFF
        assertEquals("&#x110000;", HtmlEntities.decode("&#x110000;"))
    }

    @Test
    fun `keeps surrogate code points literal`() {
        assertEquals("&#55296;", HtmlEntities.decode("&#55296;"))   // 0xD800
        assertEquals("&#xD800;", HtmlEntities.decode("&#xD800;"))
        assertEquals("&#57343;", HtmlEntities.decode("&#57343;"))   // 0xDFFF
    }

    @Test
    fun `keeps malformed numeric entities literal`() {
        assertEquals("&#zz;", HtmlEntities.decode("&#zz;"))
        assertEquals("&#;", HtmlEntities.decode("&#;"))
    }

    @Test
    fun `decodes supplementary plane code point`() {
        // U+1F600 笑脸,增补平面需代理对
        assertEquals("\uD83D\uDE00", HtmlEntities.decode("&#128512;"))
        assertEquals("\uD83D\uDE00", HtmlEntities.decode("&#x1F600;"))
    }

    // ---------------------------------------------------------------- 未知实体

    @Test
    fun `unknown named entity stays literal`() {
        assertEquals("&nonexistent;", HtmlEntities.decode("&nonexistent;"))
    }

    // ---------------------------------------------------------------- 幂等与边界

    @Test
    fun `text without ampersand is returned as is`() {
        assertEquals("plain text", HtmlEntities.decode("plain text"))
        assertEquals("", HtmlEntities.decode(""))
    }

    @Test
    fun `decoding is applied per occurrence`() {
        assertEquals("a & b & c", HtmlEntities.decode("a &amp; b &amp; c"))
    }

    @Test
    fun `typographic entities decode`() {
        assertEquals("…", HtmlEntities.decode("&hellip;"))
        assertEquals("—", HtmlEntities.decode("&mdash;"))
        assertEquals("“q”", HtmlEntities.decode("&ldquo;q&rdquo;"))
    }

    @Test
    fun `already decoded text is not double unescaped`() {
        // 输入已是字面 `<3`(未转义),不应凭空插入实体
        assertEquals("<3", HtmlEntities.decode("<3"))
    }

    @Test
    fun `ampersand before entity is not confused`() {
        // "&amp;lt;" 解码一次应为 "&lt;",不递归解码
        assertEquals("&lt;", HtmlEntities.decode("&amp;lt;"))
    }
}
