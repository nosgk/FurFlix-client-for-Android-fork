package com.furflix.app.translate.data

import java.net.URLEncoder

/** GET 请求的 URL 长度上限（编码后字符数；蓝本经验值：Google gtx 端点超过会被截断/拒绝）。 */
const val MAX_ENCODED_GET = 1200

/**
 * 长文分块（蓝本 §7，纯函数）：
 * - 段落边界（\n）优先作为切点；换行附着在行尾，各块译文直接 join() 即恢复原始排版。
 * - 单行超长硬切：maxChars 直接截断；maxEncoded 用二分找编码后仍不超限的最长前缀。
 * - 空白块原样保留在返回值中（调用方跳过翻译、原样拼接）。
 */
fun splitText(text: String, maxChars: Int, maxParagraphs: Int, maxEncoded: Int): List<String> {
    if (text.isEmpty()) return emptyList()
    val charLimit = if (maxChars > 0) maxChars else Int.MAX_VALUE
    val paraLimit = if (maxParagraphs > 0) maxParagraphs else Int.MAX_VALUE
    val encodedLimit = if (maxEncoded > 0) maxEncoded else Int.MAX_VALUE

    val chunks = ArrayList<String>()
    val pending = StringBuilder()
    var pendingChars = 0
    var pendingParas = 0

    fun appendLine(line: String) {
        if (pendingChars + line.length > charLimit || pendingParas + 1 > paraLimit) {
            flush(pending, charLimit, encodedLimit, chunks)
            pending.setLength(0)
            pendingChars = 0
            pendingParas = 0
        }
        pending.append(line)
        pendingChars += line.length
        pendingParas += 1
        // 单行本身已触限：立即成块，让硬切逻辑按行处理
        if (pendingChars >= charLimit || pendingParas >= paraLimit) {
            flush(pending, charLimit, encodedLimit, chunks)
            pending.setLength(0)
            pendingChars = 0
            pendingParas = 0
        }
    }

    var lineStart = 0
    for (i in text.indices) {
        if (text[i] == '\n') {
            appendLine(text.substring(lineStart, i + 1))
            lineStart = i + 1
        }
    }
    if (lineStart < text.length) appendLine(text.substring(lineStart))
    flush(pending, charLimit, encodedLimit, chunks)
    return chunks
}

private fun flush(
    pending: StringBuilder,
    charLimit: Int,
    encodedLimit: Int,
    out: MutableList<String>,
) {
    if (pending.isEmpty()) return
    val block = pending.toString()
    var rest = block
    while (rest.isNotEmpty()) {
        if (rest.length <= charLimit && encodedLength(rest) <= encodedLimit) {
            out.add(rest)
            return
        }
        var cut = minOf(rest.length, charLimit)
        if (encodedLength(rest.substring(0, cut)) > encodedLimit) {
            cut = maxEncodedPrefix(rest, encodedLimit)
        }
        if (cut <= 0) {
            // 极端：单字符编码后仍超限，按 1 字符推进避免死循环
            out.add(rest.substring(0, 1))
            rest = rest.substring(1)
        } else {
            out.add(rest.substring(0, cut))
            rest = rest.substring(cut)
        }
    }
}

/** encodeComponent 后的精确长度。 */
internal fun encodedLength(s: String): Int = encodeComponent(s).length

/** 二分：最长的、百分号编码后不超过 limit 的前缀长度（编码膨胀约 3 倍，二分远省于逐字符试探）。 */
internal fun maxEncodedPrefix(s: String, limit: Int): Int {
    var lo = 1
    var hi = s.length
    var best = 0
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (encodedLength(s.substring(0, mid)) <= limit) {
            best = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return best
}
