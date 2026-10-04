package com.furflix.app.translate

import com.furflix.app.translate.data.MAX_ENCODED_GET
import com.furflix.app.translate.data.encodedLength
import com.furflix.app.translate.data.encodeComponent
import com.furflix.app.translate.data.maxEncodedPrefix
import com.furflix.app.translate.data.splitText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSplitterTest {

    @Test
    fun `short text stays single chunk`() {
        assertEquals(listOf("hello"), splitText("hello", 100, 0, 0))
    }

    @Test
    fun `empty text yields no chunks`() {
        assertTrue(splitText("", 100, 0, 0).isEmpty())
    }

    @Test
    fun `paragraph boundary is the preferred cut point`() {
        val text = "a\nb\nc"
        val chunks = splitText(text, 2, 0, 0) // 每块最多 2 字符："a\n" 恰满即成块
        assertEquals(listOf("a\n", "b\n", "c"), chunks)
        // 换行附着块尾：译文 join 即恢复排版
        assertEquals(chunks.joinToString(""), text)
    }

    @Test
    fun `newline attaches to chunk end`() {
        val text = "hello\nworld\n"
        val chunks = splitText(text, 6, 0, 0)
        assertEquals(listOf("hello\n", "world\n"), chunks)
        assertEquals(text, chunks.joinToString(""))
    }

    @Test
    fun `maxParagraphs limits lines per chunk`() {
        val text = "1\n2\n3\n4\n"
        val chunks = splitText(text, 0, 2, 0)
        assertEquals(listOf("1\n2\n", "3\n4\n"), chunks)
    }

    @Test
    fun `oversized single line is hard cut at maxChars`() {
        val line = "abcdefghij" // 10 chars
        val chunks = splitText(line, 4, 0, 0)
        assertEquals(listOf("abcd", "efgh", "ij"), chunks)
        assertEquals(line, chunks.joinToString(""))
    }

    @Test
    fun `encoded limit uses binary search prefix`() {
        // CJK 每字编码后 9 字符（3 字节 × %XX）；限 27 → 恰好 3 个汉字
        val text = "一二三四五六"
        val chunks = splitText(text, 0, 0, 27)
        assertEquals(listOf("一二三", "四五六"), chunks)
        assertTrue(chunks.all { encodedLength(it) <= 27 })
    }

    @Test
    fun `maxEncodedPrefix is exact`() {
        val s = "a b\ncé一二三"
        val limit = 20
        val prefix = s.substring(0, maxEncodedPrefix(s, limit))
        assertTrue(encodedLength(prefix) <= limit)
        // 多取一个字符必然超限
        val next = s.substring(0, (maxEncodedPrefix(s, limit) + 1).coerceAtMost(s.length))
        assertTrue(maxEncodedPrefix(s, limit) == s.length || encodedLength(next) > limit)
    }

    @Test
    fun `encodedLength matches encodeComponent reality`() {
        val s = "hello 世界 & 100% ünïcode"
        assertEquals(encodeComponent(s).length, encodedLength(s))
    }

    @Test
    fun `get default encoded cap is exposed`() {
        assertEquals(1200, MAX_ENCODED_GET)
    }

    @Test
    fun `blank chunks are preserved verbatim`() {
        val text = "hello\n\n\nworld"
        val chunks = splitText(text, 6, 0, 0)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.any { it.isBlank() })
    }
}
