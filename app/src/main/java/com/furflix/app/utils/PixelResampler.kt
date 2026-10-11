package com.furflix.app.utils

/**
 * 重采样的像素级核心：纯 Kotlin、无 Android 依赖，只吃/吐 ARGB_8888 的 IntArray。
 *
 * 与 [BitmapResampler] 分开是为了可验证性：位图读写绑死在 android.graphics 上，
 * 放一起就只能靠人眼在真机上看"好像清楚了一点"；拆出来后插值本身能在 JVM 单测里
 * 逐像素断言——**确实在算中间值**（而不是退化成最近邻）、**三种算法彼此不同**
 * （而不是换了个名字的同一个实现）、**1:1 是恒等**、**缩小时不混叠**、
 * **透明边缘不渗黑**。这几条才是"插值算法真实有效"的判据。
 */
object PixelResampler {

    /**
     * 把 [srcPixels]（[srcW]×[srcH]，行主序 ARGB_8888）重采样到 [outW]×[outH]。
     * 算法 [algo] 取 [ResamplePlan.BILINEAR] / [ResamplePlan.BICUBIC] /
     * [ResamplePlan.LANCZOS]。参数非法返回 null。
     */
    fun resample(
        srcPixels: IntArray,
        srcW: Int,
        srcH: Int,
        outW: Int,
        outH: Int,
        algo: Int
    ): IntArray? {
        if (srcW <= 0 || srcH <= 0 || outW <= 0 || outH <= 0) return null
        if (srcPixels.size < srcW * srcH) return null
        // 1:1 直通：不需要重采样时原样返回。既省掉两趟卷积，也让"同尺寸恒等"
        // 严格无损——预乘 alpha 的往返取整对半透明像素有 ±1 误差，而 1:1 本就
        // 不该动像素（LOD 在缩放比为 1 时会走到这里）。
        if (outW == srcW && outH == srcH) return srcPixels.copyOf(srcW * srcH)

        // 1) 预乘 alpha：透明像素的 RGB 若不预乘，会以"黑"的身份参与加权，
        //    边缘就会渗出黑边（白底图缩小时尤其明显）
        val premul = IntArray(srcW * srcH)
        for (i in premul.indices) premul[i] = premultiply(srcPixels[i])

        // 2) 横向：srcW → outW，得到 outW×srcH（**保持预乘**，理由见第 3 步）
        val hPlan = ResamplePlan.plan(algo, srcW, 0.0, srcW.toDouble(), outW)
        val horizontal = IntArray(outW * srcH)
        for (y in 0 until srcH) {
            val rowOffset = y * srcW
            for (x in 0 until outW) {
                var a = 0f
                var r = 0f
                var g = 0f
                var b = 0f
                val base = x * hPlan.taps
                for (k in 0 until hPlan.taps) {
                    val sx = ResamplePlan.clampIndex(hPlan.starts[x] + k, srcW)
                    val wgt = hPlan.weights[base + k]
                    val p = premul[rowOffset + sx]
                    a += ((p ushr 24) and 0xFF) * wgt
                    r += ((p ushr 16) and 0xFF) * wgt
                    g += ((p ushr 8) and 0xFF) * wgt
                    b += (p and 0xFF) * wgt
                }
                horizontal[y * outW + x] = pack(a, r, g, b)
            }
        }

        // 3) 纵向：srcH → outH，得到 outW×outH；两趟都算完才反预乘
        //
        // 中途不能反预乘：卷积后 alpha 可能落到 254（255×权重和，浮点误差），
        // 反预乘会把颜色按 255/254 放大一次，两趟下来最多偏 2 级——纯色图重采样
        // 后颜色会变，半透明边缘还会发亮。全程预乘、末尾还原一次才正确，
        // 顺带省掉一半的反预乘运算。
        val vPlan = ResamplePlan.plan(algo, srcH, 0.0, srcH.toDouble(), outH)
        val out = IntArray(outW * outH)
        for (y in 0 until outH) {
            for (x in 0 until outW) {
                var a = 0f
                var r = 0f
                var g = 0f
                var b = 0f
                val base = y * vPlan.taps
                for (k in 0 until vPlan.taps) {
                    val sy = ResamplePlan.clampIndex(vPlan.starts[y] + k, srcH)
                    val wgt = vPlan.weights[base + k]
                    val p = horizontal[sy * outW + x]
                    a += ((p ushr 24) and 0xFF) * wgt
                    r += ((p ushr 16) and 0xFF) * wgt
                    g += ((p ushr 8) and 0xFF) * wgt
                    b += (p and 0xFF) * wgt
                }
                out[y * outW + x] = unpremultiply(pack(a, r, g, b))
            }
        }
        return out
    }

    /**
     * 四通道打包为 ARGB，**四舍五入**（不是截断）。
     *
     * 截断会让每个通道系统性偏小：纯色 199 经两趟卷积掉到 198 甚至 197，整幅图
     * 肉眼可见地压暗一点。四舍五入把误差压回 0（单色图重采样后逐像素与源一致，
     * 见 PixelResamplerTest.flatColorSurvivesResampling）。
     */
    private fun pack(a: Float, r: Float, g: Float, b: Float): Int {
        val ai = roundToInt(a).coerceIn(0, 255)
        val ri = roundToInt(r).coerceIn(0, 255)
        val gi = roundToInt(g).coerceIn(0, 255)
        val bi = roundToInt(b).coerceIn(0, 255)
        return (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    /** 非负通道值就近取整（负数只可能来自核的负瓣，一律夹到 0）。 */
    private fun roundToInt(v: Float): Int = if (v <= 0f) 0 else (v + 0.5f).toInt()

    /** ARGB → 预乘 alpha（RGB 分量乘以 A/255）。 */
    fun premultiply(c: Int): Int {
        val a = (c ushr 24) and 0xFF
        if (a == 255) return c
        if (a == 0) return 0
        val r = (((c ushr 16) and 0xFF) * a + 127) / 255
        val g = (((c ushr 8) and 0xFF) * a + 127) / 255
        val b = ((c and 0xFF) * a + 127) / 255
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** 预乘 → 直通 alpha（恢复显示用颜色）。 */
    fun unpremultiply(c: Int): Int {
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
