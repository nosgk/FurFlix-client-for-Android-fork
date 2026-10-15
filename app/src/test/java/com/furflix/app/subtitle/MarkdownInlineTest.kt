package com.furflix.app.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownInlineTest {

    @Test
    fun `plain text yields a single span`() {
        assertEquals(listOf(InlineSpan("hello")), MarkdownInline.parse("hello"))
    }

    @Test
    fun `double star toggles bold`() {
        assertEquals(
            listOf(InlineSpan("say "), InlineSpan("BURP", bold = true), InlineSpan(" now")),
            MarkdownInline.parse("say **BURP** now")
        )
    }

    @Test
    fun `single star toggles italic`() {
        assertEquals(listOf(InlineSpan("soft", italic = true)), MarkdownInline.parse("*soft*"))
    }

    @Test
    fun `bold and italic combine in one line`() {
        assertEquals(
            listOf(InlineSpan("a", bold = true), InlineSpan(" "), InlineSpan("b", italic = true)),
            MarkdownInline.parse("**a** *b*")
        )
    }

    @Test
    fun `underscore is left untouched`() {
        assertEquals(listOf(InlineSpan("a_b")), MarkdownInline.parse("a_b"))
    }

    @Test
    fun `empty input yields no spans`() {
        assertTrue(MarkdownInline.parse("").isEmpty())
    }
}
