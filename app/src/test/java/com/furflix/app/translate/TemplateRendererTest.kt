package com.furflix.app.translate

import com.furflix.app.translate.data.buildRequestUrl
import com.furflix.app.translate.data.encodeComponent
import com.furflix.app.translate.data.renderJsonTemplate
import com.furflix.app.translate.data.renderPlainTemplate
import com.furflix.app.translate.data.renderUrlTemplate
import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateRendererTest {

    private val vars = mapOf(
        "text" to "hello \"world\" & line\nbreak",
        "toLang" to "zh-CN",
        "baseUrl" to "https://api.example.com/v1",
        "apiKey" to "sk-test",
    )

    @Test
    fun `plain template replaces verbatim`() {
        assertEquals("Bearer sk-test", renderPlainTemplate("Bearer @apiKey", vars))
    }

    @Test
    fun `url template encodes variables but not baseUrl`() {
        val out = renderUrlTemplate("@baseUrl/chat/completions?q=@toLang", vars)
        assertEquals("https://api.example.com/v1/chat/completions?q=zh-CN", out)
    }

    @Test
    fun `space encodes as percent20 not plus`() {
        assertEquals("a%20b", encodeComponent("a b"))
        assertEquals("100%25", encodeComponent("100%"))
        assertEquals("caf%C3%A9", encodeComponent("café"))
    }

    @Test
    fun `json quoted token produces full escaped literal`() {
        val out = renderJsonTemplate("""{"q":"@text"}""", vars)
        assertEquals("""{"q":"hello \"world\" & line\nbreak"}""", out)
    }

    @Test
    fun `json bare token inserted verbatim`() {
        val out = renderJsonTemplate("""[{"n":@toLang}]""", mapOf("toLang" to "42"))
        assertEquals("""[{"n":42}]""", out)
    }

    @Test
    fun `longer tokens win over prefixes`() {
        val out = renderUrlTemplate("@toLangName|@toLang", mapOf("toLang" to "A", "toLangName" to "B"))
        assertEquals("B|A", out)
    }

    @Test
    fun `injected values are not rescanned for tokens`() {
        // 译文内容里带 @apiKey 也不能被二次替换
        val out = renderJsonTemplate("""{"q":"@text"}""", mapOf("text" to "say @apiKey hi"))
        assertEquals("""{"q":"say @apiKey hi"}""", out)
    }

    @Test
    fun `buildRequestUrl appends query with correct separator`() {
        val profile = com.furflix.app.translate.data.TranslationRequestProfile(
            method = "GET",
            url = "https://e.example/single",
            query = listOf(listOf("tl", "@toLang"), listOf("q", "@text")),
        )
        val out = buildRequestUrl(profile, vars)
        assertEquals("https://e.example/single?tl=zh-CN&q=hello%20%22world%22%20%26%20line%0Abreak", out)
    }

    @Test
    fun `buildRequestUrl reuses inline query with ampersand`() {
        val profile = com.furflix.app.translate.data.TranslationRequestProfile(
            method = "GET",
            url = "https://e.example/single?client=gtx",
            query = listOf(listOf("q", "a b")),
        )
        assertEquals("https://e.example/single?client=gtx&q=a%20b", buildRequestUrl(profile, vars))
    }
}
