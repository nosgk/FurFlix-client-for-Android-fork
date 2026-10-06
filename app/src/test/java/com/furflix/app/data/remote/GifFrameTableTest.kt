package com.furflix.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class GifFrameTableTest {

    /** 手工构造的最小双帧 GIF：帧 1 无局部色表(50ms)，帧 2 带局部色表(100ms)。 */
    private fun twoFrameGif(): ByteArray {
        val out = mutableListOf<Byte>()
        fun b(vararg v: Int) = v.forEach { out.add(it.toByte()) }
        fun str(s: String) = s.forEach { out.add(it.code.toByte()) }

        str("GIF89a")
        // 逻辑屏幕描述符：2x2，packed=0x80(GCT，2 色)，bg=0，aspect=0
        b(0x02, 0x00, 0x02, 0x00, 0x80, 0x00, 0x00)
        // 全局色表：2 项 × 3 字节
        b(0xFF, 0x00, 0x00, 0x00, 0x00, 0xFF)

        // 帧 1：GCE(delay=5 → 50ms) + 图像描述符(无局部色表)
        b(0x21, 0xF9, 0x04, 0x00, 0x05, 0x00, 0x00, 0x00)
        b(0x2C, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x02, 0x00, 0x00)
        b(0x02)                    // LZW 最小编码长度
        b(0x02, 0xAA, 0xBB, 0x00)  // 子块 + 终止

        // 帧 2：GCE(delay=10 → 100ms) + 图像描述符(局部色表 2 色)
        b(0x21, 0xF9, 0x04, 0x00, 0x0A, 0x00, 0x00, 0x00)
        b(0x2C, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x02, 0x00, 0x81)
        b(0xFF, 0x00, 0x00, 0x00, 0x00, 0xFF) // 局部色表 2 项
        b(0x02)                    // LZW
        b(0x02, 0xCC, 0xDD, 0x00)
        b(0x3B)
        return out.toByteArray()
    }

    @Test
    fun `parses one delay per frame including local-color-table frames`() {
        val delays = GifFrameTable.parseFrameDelaysMs(twoFrameGif())
        assertEquals(listOf(50, 100), delays)
    }

    @Test
    fun `treats zero delay as 100ms`() {
        val base = twoFrameGif()
        // 帧 1 的 GCE delay 置 0：GCE 起始 = header(6)+LSD(7)+GCT(6)，delayLo 在 +4
        val gceStart = 6 + 7 + 6
        base[gceStart + 4] = 0
        base[gceStart + 5] = 0
        val delays = GifFrameTable.parseFrameDelaysMs(base)
        assertEquals(listOf(100, 100), delays)
    }

    @Test
    fun `returns empty for non gif bytes`() {
        assertEquals(emptyList<Int>(), GifFrameTable.parseFrameDelaysMs(byteArrayOf(0x00, 0x01, 0x02)))
    }

    @Test
    fun `stops gracefully on truncated stream`() {
        val full = twoFrameGif()
        val truncated = full.copyOf(full.size / 2)
        // 不抛异常即可；已解析出的前缀帧数 >= 1
        org.junit.Assert.assertTrue(GifFrameTable.parseFrameDelaysMs(truncated).size >= 1)
    }
}
