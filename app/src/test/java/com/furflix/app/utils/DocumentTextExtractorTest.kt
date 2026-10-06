package com.furflix.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 文档解析器回归：docx/odt 用测试内构造的最小 zip 包（与 FA 存储的同构），
 * rtf 用典型控制词样本逐字节校验。
 */
class DocumentTextExtractorTest {

    private fun zipOf(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `docx paragraphs and media are extracted`() {
        val docXml = """
            <?xml version="1.0"?>
            <w:document xmlns:w="urn:x">
              <w:body>
                <w:p><w:r><w:t>First paragraph</w:t></w:r></w:p>
                <w:p><w:r><w:t>Second </w:t><w:t>line</w:t></w:r></w:p>
              </w:body>
            </w:document>
        """.trimIndent().toByteArray()
        val bytes = zipOf(
            mapOf(
                "word/document.xml" to docXml,
                "word/media/image1.png" to byteArrayOf(1, 2, 3),
                "word/media/image2.jpg" to byteArrayOf(4),
                "word/notes.txt" to "skip".toByteArray()
            )
        )
        val parsed = DocumentTextExtractor.parse("docx", bytes)
        assertEquals(listOf("First paragraph", "Second line"), parsed.paragraphs)
        assertEquals(2, parsed.media.size)
    }

    @Test
    fun `odt paragraphs are extracted`() {
        val contentXml = """
            <?xml version="1.0"?>
            <office:document-content xmlns:text="urn:x">
              <office:body><text:p>Hello</text:p><text:p>World</text:p></office:body>
            </office:document-content>
        """.trimIndent().toByteArray()
        val bytes = zipOf(
            mapOf(
                "content.xml" to contentXml,
                "Pictures/100000000000.png" to byteArrayOf(9)
            )
        )
        val parsed = DocumentTextExtractor.parse("odt", bytes)
        assertEquals(listOf("Hello", "World"), parsed.paragraphs)
        assertEquals(1, parsed.media.size)
    }

    @Test
    fun `rtf control words are stripped and paragraphs split`() {
        val rtf = "{\\rtf1\\ansi{\\fonttbl{\\f0 Times;}}\\fs24 Hello, world!\\par Second \\'b6 paragraph\\par bold{\\b text} end}".toByteArray()
        val paragraphs = DocumentTextExtractor.parse("rtf", rtf).paragraphs
        assertEquals(listOf("Hello, world!", "Second ¶ paragraph", "boldtext end"), paragraphs)
    }

    @Test
    fun `txt decodes utf-8 with paragraph split`() {
        val text = "One\r\n\r\nTwo".toByteArray(Charsets.UTF_8)
        val parsed = DocumentTextExtractor.parse("txt", text)
        assertEquals(listOf("One", "Two"), parsed.paragraphs)
    }

    @Test
    fun `doc is unsupported and other types are supported`() {
        assertTrue(!DocumentTextExtractor.isSupported("doc"))
        assertTrue(DocumentTextExtractor.isSupported("txt"))
        assertTrue(DocumentTextExtractor.isSupported("docx"))
        assertTrue(DocumentTextExtractor.isSupported("odt"))
        assertTrue(DocumentTextExtractor.isSupported("rtf"))
        // 不支持类型解析为空文档（查看器呈现降级态，不崩溃）
        assertEquals(0, DocumentTextExtractor.parse("doc", "x".toByteArray()).paragraphs.size)
    }
}
