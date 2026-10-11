package com.furflix.app.utils

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF 文本层抽取（文档查看器"复制 PDF 文字 / 翻译 PDF"的底座）。
 *
 * 走 PdfBox-Android（Apache PDFBox 的 Android 移植）：系统 [android.graphics.pdf.PdfRenderer]
 * 只能把页面栅格化成位图，拿不到任何文字，复制与翻译无从谈起。
 *
 * 两类失败如实降级，不谎报成功：
 * - 扫描件/纯图 PDF：文本层本就不存在，抽出来是空白 → [PdfText.EMPTY]；
 * - 加密/损坏 PdfBox 打不开 → null，调用方呈现加载失败。
 *
 * 初始化（[init]）只需一次：PDFBoxResourceLoader 要把 AAR 内的字体/字形资源
 * 挂到应用上下文上，缺了它标准字体 PDF 会抛异常。PdfBox 非线程安全，
 * 文档级操作统一串行化在 [mutex] 上。
 */
object PdfTextExtractor {

    /** 逐页抽取结果：页序号（0 基）→ 该页文本；[pages] 空数组 = 无文本层。 */
    data class PdfText(
        val pages: List<String>,
        val pageCount: Int,
    ) {
        val isEmpty: Boolean get() = pages.all { it.isBlank() }
    }

    private val mutex = Mutex()
    private var initialized = false

    /** 幂等初始化；应在首次解析前调用（Application 或查看器进入时均可）。 */
    fun init(context: Context) {
        if (initialized) return
        runCatching {
            PDFBoxResourceLoader.init(context.applicationContext)
            initialized = true
        }
    }

    /**
     * 抽取全文，按页切分。返回 null = 打不开（加密/损坏/非 PDF）。
     * 解析在 IO 线程且串行——PdfBox 的文档对象不可并发共享。
     */
    suspend fun extract(context: Context, file: File): PdfText? = withContext(Dispatchers.IO) {
        init(context)
        mutex.withLock {
            var doc: PDDocument? = null
            try {
                doc = PDDocument.load(file)
                val stripper = PDFTextStripper()
                val pages = ArrayList<String>(doc.numberOfPages)
                for (i in 0 until doc.numberOfPages) {
                    stripper.startPage = i + 1
                    stripper.endPage = i + 1
                    pages.add(stripper.getText(doc).trim())
                }
                PdfText(pages = pages, pageCount = doc.numberOfPages)
            } catch (e: Exception) {
                // 加密/损坏/字体资源缺失：如实失败，由调用方降级
                android.util.Log.w("PdfTextExtractor", "PDF parse failed: ${e.message}")
                null
            } finally {
                runCatching { doc?.close() }
            }
        }
    }

    /** 把按页文本拼成可复制/可翻译的连续正文（页间空行分隔）。 */
    fun toPlainText(pages: List<String>): String =
        pages.filter { it.isNotBlank() }.joinToString("\n\n")

    /** 页文本 → 段落（长页内按空行再切一段，避免整页糊成一坨）。 */
    fun toParagraphs(pages: List<String>): List<String> =
        pages.flatMap { page ->
            val parts = page.split(Regex("\n{2,}")).map { it.trim() }.filter { it.isNotBlank() }
            // 单页内没有空行分段时（多数小说 PDF 是满页连续行）整页作为一段
            if (parts.isEmpty()) listOf(page.trim()).filter { it.isNotBlank() } else parts
        }
}
