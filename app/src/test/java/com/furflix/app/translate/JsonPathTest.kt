package com.furflix.app.translate

import kotlinx.serialization.json.Json
import com.furflix.app.translate.data.resolveJsonPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JsonPathTest {

    @Test
    fun `dollar key chain`() {
        val root = Json.parseToJsonElement(
            """{"choices":[{"message":{"content":"你好世界"}}]}"""
        )
        assertEquals("你好世界", resolveJsonPath(root, "$.choices[0].message.content"))
    }

    @Test
    fun `dollar is optional`() {
        val root = Json.parseToJsonElement("""{"a":{"b":"x"}}""")
        assertEquals("x", resolveJsonPath(root, "a.b"))
    }

    @Test
    fun `wildcard concatenates without separator`() {
        val root = Json.parseToJsonElement(
            """[[["你好","hello",null,null,10],["世界","world",null,null,10]],null,"en"]"""
        )
        assertEquals("你好世界", resolveJsonPath(root, "$[0][*][0]"))
    }

    @Test
    fun `nested index and key mixed`() {
        val root = Json.parseToJsonElement(
            """[{"detectedLanguage":{"language":"en"},"translations":[{"text":"你好","to":"zh-CN"}]}]"""
        )
        assertEquals("你好", resolveJsonPath(root, "$[0].translations[0].text"))
    }

    @Test
    fun `entire body when path is root`() {
        val root = Json.parseToJsonElement("""["你好世界","wt_lib"]""")
        assertEquals("你好世界", resolveJsonPath(root, "$[0]"))
    }

    @Test
    fun `numbers and booleans stringify via content`() {
        val root = Json.parseToJsonElement("""{"n":42,"ok":true}""")
        assertEquals("42", resolveJsonPath(root, "n"))
        assertEquals("true", resolveJsonPath(root, "ok"))
    }

    @Test
    fun `missing key throws`() {
        val root = Json.parseToJsonElement("""{"a":"1"}""")
        assertThrows(IllegalArgumentException::class.java) { resolveJsonPath(root, "b") }
    }

    @Test
    fun `index out of bounds throws`() {
        val root = Json.parseToJsonElement("""[1]""")
        assertThrows(IllegalArgumentException::class.java) { resolveJsonPath(root, "[3]") }
    }

    @Test
    fun `type mismatch throws`() {
        val root = Json.parseToJsonElement("""{"a":{"b":1}}""")
        assertThrows(IllegalArgumentException::class.java) { resolveJsonPath(root, "a[0]") }
    }

    @Test
    fun `bad index literal throws`() {
        val root = Json.parseToJsonElement("""[1]""")
        assertThrows(IllegalArgumentException::class.java) { resolveJsonPath(root, "[x]") }
    }
}
