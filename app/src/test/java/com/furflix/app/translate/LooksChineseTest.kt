package com.furflix.app.translate

import com.furflix.app.translate.data.translationLooksChinese
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LooksChineseTest {

    @Test
    fun `pure chinese is detected`() {
        assertTrue(translationLooksChinese("你好世界"))
        assertTrue(translationLooksChinese("你好，世界！这是一段测试文本。"))
    }

    @Test
    fun `english is not chinese`() {
        assertFalse(translationLooksChinese("hello world"))
        assertFalse(translationLooksChinese(""))
    }

    @Test
    fun `japanese with kana is excluded even when kanji present`() {
        // 含假名 → 判为日文，不跳过
        assertFalse(translationLooksChinese("こんにちは世界"))
        assertFalse(translationLooksChinese("かわいい dragon 好可爱"))
    }

    @Test
    fun `korean hangul is excluded`() {
        assertFalse(translationLooksChinese("안녕하세요"))
    }

    @Test
    fun `mostly english with one han char stays below threshold`() {
        assertFalse(translationLooksChinese("this is a long english sentence with 一 char"))
    }

    @Test
    fun `whitespace does not count toward ratio`() {
        assertTrue(translationLooksChinese("你  好   \n\t世界"))
    }

    @Test
    fun `mixed cjk extensions still counted`() {
        // 0x3400–0x4DBF 扩展区
        assertTrue(translationLooksChinese("㐀㐁㐂㐃㐄"))
    }
}
