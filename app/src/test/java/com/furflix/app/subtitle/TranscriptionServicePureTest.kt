package com.furflix.app.subtitle

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 转录客户端的纯逻辑部分：URL 拼接、模型列表解析、请求体构造、响应取文。 */
class TranscriptionServicePureTest {

    @Test
    fun `parseModelIds reads the data ids`() {
        val raw = """{"object":"list","data":[{"id":"whisper-1"},{"id":"gpt-4o-audio"}]}"""
        assertEquals(listOf("whisper-1", "gpt-4o-audio"), parseModelIds(raw))
    }

    @Test
    fun `parseModelIds tolerates malformed payloads`() {
        assertTrue(parseModelIds("not json").isEmpty())
        assertTrue(parseModelIds("{}").isEmpty())
        assertTrue(parseModelIds("""{"data":"nope"}""").isEmpty())
    }

    @Test
    fun `endpointFor accepts bare host trailing slash and v1 suffix`() {
        assertEquals("https://api.openai.com/v1/models", endpointFor("https://api.openai.com", "/v1/models"))
        assertEquals("https://api.openai.com/v1/models", endpointFor("https://api.openai.com/", "/v1/models"))
        assertEquals("https://api.openai.com/v1/models", endpointFor("https://api.openai.com/v1", "/v1/models"))
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            endpointFor("https://api.openai.com/v1/", "/v1/chat/completions")
        )
    }

    @Test
    fun `audioFormat reads the extension and falls back to mp3`() {
        assertEquals("flac", audioFormat("https://cdn.example/a/song.FLAC"))
        assertEquals("m4a", audioFormat("https://cdn.example/a/song.m4a?token=1"))
        assertEquals("mp3", audioFormat("https://cdn.example/download/12345"))
    }

    @Test
    fun `buildChatPayload carries the system prompt and base64 audio`() {
        val payload = buildChatPayload("gpt-4o-audio", "SYS", byteArrayOf(1, 2, 3), "mp3")
        val root = Json.parseToJsonElement(payload).jsonObject
        assertEquals("gpt-4o-audio", root["model"]?.jsonPrimitive?.content)

        val messages = root["messages"]!!.jsonArray
        assertEquals("system", messages[0].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("SYS", messages[0].jsonObject["content"]?.jsonPrimitive?.content)

        val content = messages[1].jsonObject["content"]!!.jsonArray
        assertEquals("text", content[0].jsonObject["type"]?.jsonPrimitive?.content)
        val audio = content[1].jsonObject["input_audio"]!!.jsonObject
        assertEquals("mp3", audio["format"]?.jsonPrimitive?.content)
        assertEquals("AQID", audio["data"]?.jsonPrimitive?.content)
    }

    @Test
    fun `parseChatContent returns the first choice content`() {
        val raw = """{"choices":[{"message":{"role":"assistant","content":"1\n00:00,000 --> 00:01,000\nhi"}}]}"""
        assertTrue(parseChatContent(raw).contains("00:00,000 --> 00:01,000"))
    }

    @Test(expected = TranscriptionException::class)
    fun `parseChatContent rejects an empty choice list`() {
        parseChatContent("""{"choices":[]}""")
    }

    @Test(expected = TranscriptionException::class)
    fun `parseChatContent rejects non json`() {
        parseChatContent("<html>502</html>")
    }
}
