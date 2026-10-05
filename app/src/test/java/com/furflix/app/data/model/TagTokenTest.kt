package com.furflix.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagTokenTest {

    @Test
    fun `normalize lowercases and keeps the exclusion prefix`() {
        assertEquals("cute", normalizeTagToken(" Cute "))
        assertEquals("!feral", normalizeTagToken("!Feral"))
        assertNull(normalizeTagToken("!  "))
        assertNull(normalizeTagToken(""))
    }

    @Test
    fun `add replaces both polarities of the same base`() {
        assertEquals(listOf("a", "b"), addTagToken(listOf("a", "!b"), "b"))
        assertEquals(listOf("a", "!b"), addTagToken(listOf("a", "b"), "!b"))
        assertEquals(listOf("a", "c", "!b"), addTagToken(listOf("a", "c"), "!b"))
    }

    @Test
    fun `toggle flips polarity in place`() {
        assertEquals(listOf("a", "!b"), toggleTagExclusion(listOf("a", "b"), "b"))
        assertEquals(listOf("a", "b"), toggleTagExclusion(listOf("a", "!b"), "!b"))
        assertEquals(listOf("a", "b"), toggleTagExclusion(listOf("a", "b"), "zzz"))
    }
}
