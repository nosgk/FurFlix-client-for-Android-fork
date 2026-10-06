package com.furflix.app.data.remote

/**
 * GIF 块结构解析：按 Graphic Control Extension 出现顺序取每帧延时（单位 10ms → ms）。
 * 帧序 = GCE 顺序；异常帧按 100ms 处理（与浏览器对空延时的默认一致）。
 * 纯 Kotlin、无 Android 依赖，便于 JVM 单测——历史上图像描述符（0x2C）的
 * packed/局部色表/LZW 跳步错位会让帧表只解析出首帧，逐帧控制整体失效。
 */
object GifFrameTable {

    fun parseFrameDelaysMs(bytes: ByteArray): List<Int> {
        val delays = mutableListOf<Int>()
        fun u16(o: Int): Int = if (o + 1 < bytes.size) {
            (bytes[o].toInt() and 0xFF) or ((bytes[o + 1].toInt() and 0xFF) shl 8)
        } else 0

        try {
            var i = 6 + 7 // header(6) + logical screen descriptor(7)
            if (i >= bytes.size) return delays
            if ((bytes[10].toInt() and 0x80) != 0) {
                // 全局色表：3 字节/项，项数 = 2^(n+1)
                i += 3 * (1 shl ((bytes[10].toInt() and 0x07) + 1))
            }
            while (i < bytes.size) {
                when (bytes[i].toInt() and 0xFF) {
                    0x21 -> { // 扩展块：label(1) + 子块链(以 0x00 终止)
                        val label = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else -1
                        if (label == 0xF9 && i + 6 < bytes.size) {
                            val delay = u16(i + 4) * 10
                            delays.add(if (delay <= 0) 100 else delay)
                        }
                        i += 2
                        while (i < bytes.size && bytes[i].toInt() != 0) {
                            i += 1 + (bytes[i].toInt() and 0xFF)
                        }
                        i += 1
                    }
                    0x2C -> { // 图像描述符：0x2C + left/top/w/h(8) → packed + 可选局部色表 + LZW 子块链
                        i += 1 + 8 // 消费 0x2C 与 8 字节定长段，i 停在 packed
                        if (i >= bytes.size) break
                        val packed = bytes[i].toInt() and 0xFF
                        var advance = 1 // packed 本身
                        if (packed and 0x80 != 0) {
                            advance += 3 * (1 shl ((packed and 0x07) + 1)) // 局部色表紧跟 packed
                        }
                        i += advance // → LZW 最小编码长度
                        i += 1       // 跳过 LZW 字节，i 落在首个子块长度上
                        while (i < bytes.size && bytes[i].toInt() != 0) {
                            i += 1 + (bytes[i].toInt() and 0xFF)
                        }
                        i += 1       // 子块链终止符
                    }
                    0x3B -> break // trailer
                    else -> break // 结构异常：以已解析的为准
                }
            }
        } catch (_: Exception) {
            // 越界等异常：返回已解析部分
        }
        return delays
    }
}
