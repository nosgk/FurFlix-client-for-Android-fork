package com.furflix.app.utils

import android.graphics.Bitmap

/**
 * 位图重采样（可选插值算法）的 Android 落地：把 [src] 上的一块区域按
 * 双线性 / 双三次 / 兰索斯重采样到目标尺寸。
 *
 * 为什么需要它：android.graphics 的 Canvas/Paint 只提供双线性（filterBitmap）
 * 与最近邻两档，双三次与兰索斯在 Java 层拿不到（Skia 的 SkSamplingOptions
 * 未开放）。要在放大时真正获得"锐利无损"的重采样，只能自己按核函数卷积。
 *
 * 做法是标准的可分离两趟卷积（先横向再纵向），全程在 IntArray 上做算术、
 * 只在首尾与 Bitmap 互转，避免逐像素 JNI 调用。颜色按预乘 alpha 处理——
 * 不预乘的话透明像素的 RGB 会参与加权，边缘会渗出黑边。
 *
 * 线程：纯函数式，不持有状态，调用方负责放到后台线程。
 */
object BitmapResampler {

    /**
     * 从 [src] 的 [srcLeft],[srcTop],[srcWidth],[srcHeight] 区域重采样出
     * [outWidth]×[outHeight] 的新位图。算法 [algo] 取 [ResamplePlan.BILINEAR] / [ResamplePlan.BICUBIC] / [ResamplePlan.LANCZOS]。
     * 任一参数非法时返回 null，调用方回落到 GPU 绘制。
     */
    fun resample(
        src: Bitmap,
        srcLeft: Int,
        srcTop: Int,
        srcWidth: Int,
        srcHeight: Int,
        outWidth: Int,
        outHeight: Int,
        algo: Int
    ): Bitmap? {
        if (src.isRecycled) return null
        if (srcWidth <= 0 || srcHeight <= 0 || outWidth <= 0 || outHeight <= 0) return null
        val left = srcLeft.coerceIn(0, (src.width - 1).coerceAtLeast(0))
        val top = srcTop.coerceIn(0, (src.height - 1).coerceAtLeast(0))
        val w = srcWidth.coerceAtMost(src.width - left)
        val h = srcHeight.coerceAtMost(src.height - top)
        if (w <= 0 || h <= 0) return null

        // 区域整体读出（一次 getPixels），后续全在数组上算
        val srcPixels = IntArray(w * h)
        runCatching { src.getPixels(srcPixels, 0, w, left, top, w, h) }.getOrNull() ?: return null

        // 1) 预乘 alpha（透明边缘不渗黑）
        val premul = IntArray(srcPixels.size)
        for (i in srcPixels.indices) premul[i] = premultiply(srcPixels[i])

        // 2) 横向：w → outWidth，得到 outWidth×h
        val hPlan = ResamplePlan.plan(algo, w, 0.0, w.toDouble(), outWidth)
        val horizontal = IntArray(outWidth * h)
        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until outWidth) {
                var a = 0f; var r = 0f; var g = 0f; var b = 0f
                val base = x * hPlan.taps
                for (k in 0 until hPlan.taps) {
                    val sx = ResamplePlan.clampIndex(hPlan.starts[x] + k, w)
                    val wgt = hPlan.weights[base + k]
                    val p = premul[rowOffset + sx]
                    a += ((p ushr 24) and 0xFF) * wgt
                    r += ((p ushr 16) and 0xFF) * wgt
                    g += ((p ushr 8) and 0xFF) * wgt
                    b += (p and 0xFF) * wgt
                }
                horizontal[y * outWidth + x] = pack(a, r, g, b)
            }
        }

        // 3) 纵向：h → outHeight，得到 outWidth×outHeight
        val vPlan = ResamplePlan.plan(algo, h, 0.0, h.toDouble(), outHeight)
        val out = IntArray(outWidth * outHeight)
        for (y in 0 until outHeight) {
            for (x in 0 until outWidth) {
                var a = 0f; var r = 0f; var g = 0f; var b = 0f
                val base = y * vPlan.taps
                for (k in 0 until vPlan.taps) {
                    val sy = ResamplePlan.clampIndex(vPlan.starts[y] + k, h)
                    val wgt = vPlan.weights[base + k]
                    val p = horizontal[sy * outWidth + x]
                    a += ((p ushr 24) and 0xFF) * wgt
                    r += ((p ushr 16) and 0xFF) * wgt
                    g += ((p ushr 8) and 0xFF) * wgt
                    b += (p and 0xFF) * wgt
                }
                out[y * outWidth + x] = unpremultiply(pack(a, r, g, b))
            }
        }

        return runCatching {
            Bitmap.createBitmap(out, outWidth, outHeight, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }

    private fun pack(a: Float, r: Float, g: Float, b: Float): Int {
        val ai = a.toInt().coerceIn(0, 255)
        val ri = r.toInt().coerceIn(0, 255)
        val gi = g.toInt().coerceIn(0, 255)
        val bi = b.toInt().coerceIn(0, 255)
        return (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    /** ARGB → 预乘 alpha（RGB 分量乘以 A/255）。 */
    private fun premultiply(c: Int): Int {
        val a = (c ushr 24) and 0xFF
        if (a == 255) return c
        if (a == 0) return 0
        val r = (((c ushr 16) and 0xFF) * a + 127) / 255
        val g = (((c ushr 8) and 0xFF) * a + 127) / 255
        val b = ((c and 0xFF) * a + 127) / 255
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** 预乘 → 直通 alpha（恢复显示用颜色）。 */
    private fun unpremultiply(c: Int): Int {
        val a = (c ushr 24) and 0xFF
        if (a == 255) return c
        if (a == 0) return 0
        val r = (((c ushr 16) and 0xFF) * 255 + a / 2) / a
        val g = (((c ushr 8) and 0xFF) * 255 + a / 2) / a
        val b = ((c and 0xFF) * 255 + a / 2) / a
        return (a shl 24) or (r.coerceAtMost(255) shl 16) or
            (g.coerceAtMost(255) shl 8) or b.coerceAtMost(255)
    }
}
