package com.furflix.app.subtitle

import kotlin.math.roundToLong

/**
 * SRT 解析：把模型返回的（或缓存下来的）字幕文本解析成 [SubtitleTrack]。
 *
 * 模型输出不可控，这里刻意容错而非严格：
 *  - **时间轴两种写法都收**：模型常按提示词输出 `MM:SS,sss`（无小时位），而标准
 *    SRT 是 `HH:MM:SS,sss`；毫秒分隔符 `,` 与 `.` 都接受。
 *  - 缺失的序号、多余空行、`-->` 两侧空格差异都不影响解析。
 *  - 整块无法解析的条目直接丢弃，不抛异常——字幕是增强信息，不该让播放崩。
 *
 * 纯函数、无 Android 依赖，可直接单测。
 */
object SrtParser {

    private const val ARROW = "-->"

    /**
     * 从模型整段回复里取出字幕正文：优先取 ``` 包裹的代码块（提示词要求如此输出），
     * 没有代码块时退回整段原文（模型偶尔会直接给裸 SRT）。
     */
    fun extractFencedBlock(raw: String): String {
        val text = raw.trim()
        // ```srt / ```SRT / ``` 开头，到下一个 ``` 结束
        val fenceStart = text.indexOf("```")
        if (fenceStart >= 0) {
            val afterFence = text.indexOf('\n', fenceStart)
            if (afterFence > 0) {
                val fenceEnd = text.indexOf("```", afterFence)
                if (fenceEnd > afterFence) {
                    return text.substring(afterFence + 1, fenceEnd).trim()
                }
            }
        }
        return text
    }

    /** 解析字幕文本（已可含/不含代码块包裹，内部会先抽一次）。 */
    fun parse(raw: String): SubtitleTrack {
        val body = extractFencedBlock(raw)
        if (body.isEmpty()) return SubtitleTrack.EMPTY

        val lines = body.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val cues = mutableListOf<SubtitleCue>()
        var i = 0
        var sequence = 1
        while (i < lines.size) {
            val line = lines[i]
            if (!line.contains(ARROW)) {
                i++
                continue
            }
            val (start, end) = splitArrow(line) ?: run { i++; continue }
            // 文本行：直到空行 / 下一条时间轴 / 下一条的序号行
            val textLines = mutableListOf<String>()
            var j = i + 1
            while (j < lines.size) {
                val candidate = lines[j]
                if (candidate.isBlank()) break
                if (candidate.contains(ARROW)) break
                if (isCueIndex(candidate) && j + 1 < lines.size && lines[j + 1].contains(ARROW)) break
                textLines.add(candidate.trim())
                j++
            }
            val text = textLines.joinToString("\n").trim()
            if (text.isNotEmpty()) {
                cues.add(
                    SubtitleCue(
                        index = sequence,
                        startMs = start,
                        // 时间轴倒挂（模型偶发）时钳成零长度而非丢弃
                        endMs = if (end > start) end else start,
                        text = text
                    )
                )
                sequence++
            }
            i = if (j > i) j else i + 1
        }
        return SubtitleTrack(cues.sortedBy { it.startMs })
    }

    /** `00:00,000 --> 00:02,908` → (0L, 2908L)；任一侧不可解析返回 null。 */
    private fun splitArrow(line: String): Pair<Long, Long>? {
        val idx = line.indexOf(ARROW)
        if (idx < 0) return null
        val left = parseTimestamp(line.substring(0, idx)) ?: return null
        val right = parseTimestamp(line.substring(idx + ARROW.length)) ?: return null
        return left to right
    }

    /**
     * 时间戳 → 毫秒。接受 `HH:MM:SS,sss`、`MM:SS,sss`、`MM:SS`（无毫秒）以及
     * `,`/`.` 两种毫秒分隔符；空串或非数字返回 null。
     */
    internal fun parseTimestamp(raw: String): Long? {
        val cleaned = raw.trim().replace(',', '.')
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split(':')
        if (parts.size !in 2..3) return null
        val seconds = parts.last().toDoubleOrNull() ?: return null
        val minutes = parts[parts.size - 2].toLongOrNull() ?: return null
        val hours = if (parts.size == 3) (parts[0].toLongOrNull() ?: return null) else 0L
        if (seconds < 0 || minutes < 0 || hours < 0) return null
        val totalMs = (hours * 3600 + minutes * 60) * 1000 + (seconds * 1000).roundToLong()
        return totalMs
    }

    private fun isCueIndex(line: String): Boolean {
        val t = line.trim()
        return t.isNotEmpty() && t.length <= 5 && t.all { it.isDigit() }
    }
}
