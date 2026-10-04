package com.furflix.app.translate

import com.furflix.app.translate.data.CHROME_API_KEY
import com.furflix.app.translate.data.TranslationConfig
import com.furflix.app.translate.data.TranslationProvider
import com.furflix.app.translate.data.builtInProfile
import com.furflix.app.translate.data.decodeProfileOrNull
import com.furflix.app.translate.data.encodeToJson
import com.furflix.app.translate.data.translationTemplateVars
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCodecTest {

    @Test
    fun `encode decode roundtrip preserves all fields`() {
        val profile = builtInProfile(TranslationProvider.OPENAI)
        val decoded = decodeProfileOrNull(profile.encodeToJson())
        assertNotNull(decoded)
        assertEquals(profile, decoded)
    }

    @Test
    fun `bad json falls back to null`() {
        assertNull(decodeProfileOrNull("not-json{"))
        assertNull(decodeProfileOrNull(""))
        assertNull(decodeProfileOrNull(null))
    }

    @Test
    fun `unknown extra keys are ignored`() {
        val decoded = decodeProfileOrNull("""{"method":"GET","url":"https://x","futureField":1}""")
        assertNotNull(decoded)
        assertEquals("GET", decoded!!.method)
        assertEquals("https://x", decoded.url)
    }

    @Test
    fun `five presets are well formed`() {
        for (provider in TranslationProvider.entries) {
            val p = builtInProfile(provider)
            assertTrue(p.method == "GET" || p.method == "POST")
            assertTrue(p.url.isNotEmpty())
            if (p.method == "POST") assertTrue(p.body.isNotEmpty())
            if (p.method == "GET") assertEquals("", p.body)
        }
    }

    @Test
    fun `chrome preset carries the public key and html passthrough`() {
        val p = builtInProfile(TranslationProvider.CHROME)
        assertTrue(p.headers.any { it.size == 2 && it[0] == "X-Goog-API-Key" && it[1] == CHROME_API_KEY })
        assertTrue(p.htmlIn)
        assertTrue(p.contentType.contains("protobuf"))
    }

    @Test
    fun `openai preset strips think and uses baseUrl verbatim`() {
        val p = builtInProfile(TranslationProvider.OPENAI)
        assertTrue(p.stripThink)
        assertTrue(p.htmlIn)
        assertTrue(p.url.startsWith("@baseUrl/"))
        val url = com.furflix.app.translate.data.buildRequestUrl(
            p,
            translationTemplateVars(TranslationConfig(), "hi"),
        )
        assertEquals("https://api.openai.com/v1/chat/completions", url)
    }

    @Test
    fun `google preset is GET with sentence splitter path`() {
        val p = builtInProfile(TranslationProvider.GOOGLE)
        assertEquals("GET", p.method)
        assertEquals("\$[0][*][0]", p.parsePath)
        assertFalse(p.htmlIn)
    }

    @Test
    fun `template vars resolve prompts and native names`() {
        val vars = translationTemplateVars(TranslationConfig(), "hello")
        assertEquals("hello", vars["text"])
        assertEquals("zh-CN", vars["toLang"])
        assertEquals("简体中文", vars["toLangName"])
        assertEquals("auto", vars["fromLang"])
        assertTrue(vars["userPrompt"]!!.startsWith("Translate the following text into 简体中文."))
        assertTrue(vars["userPrompt"]!!.endsWith("hello"))
        assertEquals(
            com.furflix.app.translate.data.DEFAULT_SYSTEM_PROMPT,
            vars["systemPrompt"],
        )
    }

    @Test
    fun `custom user prompt supports toLang and text tokens`() {
        val config = TranslationConfig(userPrompt = "Into @toLangName (@toLang): @text")
        val vars = translationTemplateVars(config, "hi")
        assertEquals("Into 简体中文 (zh-CN): hi", vars["userPrompt"])
    }
}
