package com.furflix.app.utils

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * 文档类作品（FA 文档/store 类型：txt/docx/odt/rtf）的纯 JVM 解析器，
 * 供全屏文档查看器取正文段落与内嵌媒体图。零外部依赖：
 * docx/odt 走 zip+xml（jsoup xmlParser），rtf 走控制词状态机，txt 直解码。
 * .doc（OLE2 二进制）无可靠纯手写解析，明确声明不支持并回落下载提示。
 */
object DocumentTextExtractor {

    /** 解析产物：段落列表（已剔除空段）+ 内嵌媒体图字节（docx/odt 有）。 */
    data class ParsedDocument(
        val paragraphs: List<String>,
        val media: List<ByteArray> = emptyList()
    )

    private val SUPPORTED_EXTS = setOf("txt", "docx", "odt", "rtf")
    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

    fun isSupported(ext: String): Boolean = ext.lowercase() in SUPPORTED_EXTS

    /** FA 文本多为 UTF-8；老上传可能是 cp1252——先按 UTF-8 严格解码，失败回落单字节。 */
    fun decodeText(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    } catch (_: Exception) {
        String(bytes, java.nio.charset.Charset.forName("windows-1252"))
    }

    /** 纯文本 → 段落（空行分隔；归一化 CRLF）。 */
    fun textToParagraphs(text: String): List<String> =
        text.replace("\r\n", "\n")
            .split(Regex("\n{2,}"))
            .map { it.trim('\n', ' ') }
            .filter { it.isNotBlank() }

    private fun zipMediaEntries(entries: Map<String, ByteArray>, prefix: String): List<ByteArray> =
        entries.entries.asSequence()
            .filter { it.key.startsWith(prefix) }
            .filter { it.key.substringAfterLast('.', "").lowercase() in IMAGE_EXTS }
            .sortedBy { it.key }
            .map { it.value }
            .toList()

    private fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val map = LinkedHashMap<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                val buf = ByteArray(8192)
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val out = ByteArrayOutputStream()
                        while (true) {
                            val n = zis.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                        }
                        map[entry.name] = out.toByteArray()
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (_: Exception) {
            // 坏 zip：返回已读到的部分，调用方按空文档降级
        }
        return map
    }

    /** docx：word/document.xml 的 w:p → 段落，w:t → 文本；word/media/* → 内嵌媒体。 */
    private fun parseDocx(bytes: ByteArray): ParsedDocument {
        val entries = readZipEntries(bytes)
        val xml = entries["word/document.xml"]?.let { String(it, Charsets.UTF_8) }
        val paragraphs = if (xml != null) {
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            doc.getElementsByTag("w:p")
                .map { p -> p.getElementsByTag("w:t").joinToString("") { it.text() }.trim() }
                .filter { it.isNotEmpty() }
        } else emptyList()
        return ParsedDocument(paragraphs, zipMediaEntries(entries, "word/media/"))
    }

    /** odt：content.xml 的 text:p → 段落；Pictures/* → 内嵌媒体。 */
    private fun parseOdt(bytes: ByteArray): ParsedDocument {
        val entries = readZipEntries(bytes)
        val xml = entries["content.xml"]?.let { String(it, Charsets.UTF_8) }
        val paragraphs = if (xml != null) {
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            doc.getElementsByTag("text:p")
                .map { it.text().trim() }
                .filter { it.isNotEmpty() }
        } else emptyList()
        return ParsedDocument(paragraphs, zipMediaEntries(entries, "Pictures/"))
    }

    /**
     * RTF 最小解析：剥控制词/目标组（fonttbl/colortbl/pict 等），\par 视作段落
     * 分隔，\'hh 十六进制转义还原字符，\\ \{ \} 还原字面量。足以读常规文本文档。
     */
    private fun rtfToParagraphs(bytes: ByteArray): List<String> {
        val raw = decodeText(bytes)
        val text = StringBuilder()
        var i = 0
        var groupDepth = 0
        var skipUntilDepth = -1
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == '{' -> groupDepth++
                c == '}' -> {
                    groupDepth--
                    if (skipUntilDepth >= 0 && groupDepth < skipUntilDepth) skipUntilDepth = -1
                }
                c == '\\' && i + 1 < raw.length -> {
                    val next = raw[i + 1]
                    when {
                        next == '\\' || next == '{' || next == '}' -> {
                            if (skipUntilDepth < 0) text.append(next)
                            i += 2
                            continue
                        }
                        next == '\'' -> {
                            val hex = raw.substring(i + 2, (i + 4).coerceAtMost(raw.length))
                            val code = hex.toIntOrNull(16)
                            if (code != null) {
                                if (skipUntilDepth < 0) text.append(code.toChar())
                                i += 4
                            } else {
                                i += 2
                            }
                            continue
                        }
                        else -> {
                            var j = i + 1
                            while (j < raw.length && raw[j].isLetter()) j++
                            val word = raw.substring(i + 1, j)
                            var k = j
                            if (k < raw.length && raw[k] == '-') k++
                            while (k < raw.length && raw[k].isDigit()) k++
                            if (k < raw.length && raw[k] == ' ') k++
                            when (word) {
                                "par", "line", "sect", "page" -> text.append('\n')
                                "tab" -> if (skipUntilDepth < 0) text.append('\t')
                                "fonttbl", "colortbl", "stylesheet", "info",
                                "pict", "object", "header", "footer" -> skipUntilDepth = groupDepth
                            }
                            i = k
                            continue
                        }
                    }
                }
                c == '\n' || c == '\r' -> Unit // RTF 源码换行不是内容
                skipUntilDepth < 0 -> text.append(c)
            }
            i++
        }
        return text.toString().split('\n').map { it.trim() }.filter { it.isNotBlank() }
    }

    /** 按扩展名分发；不支持的类型返回空文档（调用方呈现不支持态）。 */
    fun parse(ext: String, bytes: ByteArray): ParsedDocument = when (ext.lowercase()) {
        "txt" -> ParsedDocument(textToParagraphs(decodeText(bytes)))
        "rtf" -> ParsedDocument(rtfToParagraphs(bytes))
        "docx" -> parseDocx(bytes)
        "odt" -> parseOdt(bytes)
        else -> ParsedDocument(emptyList())
    }
}
