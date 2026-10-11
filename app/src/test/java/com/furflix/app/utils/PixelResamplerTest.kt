package com.furflix.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 插值算法"真实有效"的判据。这些断言刻意针对"看起来像实现了其实没实现"的
 * 几种失败方式：退化成最近邻、三个档位其实是同一个实现、缩小时直接丢像素、
 * 透明边缘渗黑。
 *
 * 逐像素在 JVM 上验证 [PixelResampler]（纯 Kotlin），不依赖 Android。
 */
class PixelResamplerTest {

    private val algos = listOf(
        ResamplePlan.BILINEAR,
        ResamplePlan.BICUBIC,
        ResamplePlan.LANCZOS
    )

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private fun alpha(c: Int) = (c ushr 24) and 0xFF
    private fun red(c: Int) = (c ushr 16) and 0xFF
    private fun green(c: Int) = (c ushr 8) and 0xFF
    private fun blue(c: Int) = c and 0xFF

    /** 2×1 的黑白台阶：左黑右白，放大 8 倍。 */
    private fun stepEdge(): IntArray = intArrayOf(argb(255, 0, 0, 0), argb(255, 255, 255, 255))

    /**
     * 核心判据一：**确实在插值**。
     * 放大台阶边缘必然产生介于黑白之间的中间值；最近邻只会吐出 0 或 255。
     */
    @Test
    fun upscalingProducesIntermediateValues() {
        for (algo in algos) {
            val out = PixelResampler.resample(stepEdge(), 2, 1, 8, 1, algo)
            assertNotNull("algo=$algo", out)
            val mids = out!!.map { red(it) }.filter { it in 1..254 }
            assertTrue(
                "algo=$algo 没有产生任何中间值，说明退化成最近邻了: ${out.map { red(it) }}",
                mids.isNotEmpty()
            )
        }
    }

    /**
     * 核心判据二：**三个档位不是同一个实现**。
     * 同一输入的输出必须两两不同，否则"可选插值算法"是假的。
     */
    @Test
    fun theThreeAlgorithmsAreActuallyDifferent() {
        val out = algos.associateWith { PixelResampler.resample(stepEdge(), 2, 1, 16, 1, it)!! }
        assertNotEquals("Bilinear 与 Bicubic 输出完全相同", out[ResamplePlan.BILINEAR]!!.toList(), out[ResamplePlan.BICUBIC]!!.toList())
        assertNotEquals("Bilinear 与 Lanczos 输出完全相同", out[ResamplePlan.BILINEAR]!!.toList(), out[ResamplePlan.LANCZOS]!!.toList())
        assertNotEquals("Bicubic 与 Lanczos 输出完全相同", out[ResamplePlan.BICUBIC]!!.toList(), out[ResamplePlan.LANCZOS]!!.toList())
    }

    /** 1:1 必须逐像素恒等（含 alpha），否则画面上会整体糊掉或位移。 */
    @Test
    fun identityAtSameSize() {
        val src = intArrayOf(
            argb(255, 10, 20, 30), argb(255, 200, 100, 50),
            argb(128, 0, 255, 0), argb(0, 255, 0, 0)
        )
        for (algo in algos) {
            val out = PixelResampler.resample(src, 2, 2, 2, 2, algo)!!
            for (i in src.indices) {
                assertEquals("algo=$algo i=$i", src[i], out[i])
            }
        }
    }

    /**
     * 缩小时要做低通：高频棋盘格降采样后应趋于中间灰（能量守恒），
     * 而不是继续输出 0/255 的极端值（那是直接丢像素的混叠表现）。
     */
    @Test
    fun downscalingLowPassesInsteadOfAliasing() {
        val n = 8
        val checker = IntArray(n * n) { i ->
            val x = i % n
            val y = i / n
            if ((x + y) % 2 == 0) argb(255, 255, 255, 255) else argb(255, 0, 0, 0)
        }
        for (algo in algos) {
            val out = PixelResampler.resample(checker, n, n, 2, 2, algo)!!
            val values = out.map { red(it) }
            val extremes = values.count { it == 0 || it == 255 }
            val mean = values.average()
            assertTrue(
                "algo=$algo 缩小后仍在输出极端值（混叠）: $values",
                extremes == 0
            )
            assertTrue(
                "algo=$algo 缩小后均值应接近中灰(128)，实际 $mean",
                abs(mean - 127.5) < 20.0
            )
        }
    }

    /**
     * 透明边缘不渗黑：全透明的白色像素与不透明红色相邻，缩小后红通道不应被
     * 透明像素的"黑"拉低太多。不预乘 alpha 的实现会明显偏暗。
     */
    @Test
    fun transparentPixelsDoNotBleedBlack() {
        val src = intArrayOf(
            argb(255, 255, 0, 0), argb(255, 255, 0, 0),
            argb(0, 255, 255, 255), argb(0, 255, 255, 255)
        )
        for (algo in algos) {
            val out = PixelResampler.resample(src, 2, 2, 1, 1, algo)!!
            // 输出是红与透明白各占一半：颜色应仍是纯红（不渗黑），alpha 约一半
            assertTrue("algo=$algo 红通道被拉暗: r=${red(out[0])}", red(out[0]) >= 240)
            assertTrue("algo=$algo alpha 不合理: a=${alpha(out[0])}", alpha(out[0]) in 100..160)
        }
    }

    /** 输出尺寸必须严格等于请求尺寸（tile 的 dstSize 依赖这一点）。 */
    @Test
    fun outputSizeMatchesRequest() {
        val src = IntArray(4 * 4) { argb(255, 40, 40, 40) }
        for (algo in algos) {
            assertEquals(7 * 3, PixelResampler.resample(src, 4, 4, 7, 3, algo)!!.size)
            assertEquals(2 * 9, PixelResampler.resample(src, 4, 4, 2, 9, algo)!!.size)
        }
    }

    /** 非法参数一律返回 null，调用方据此回落 GPU 绘制，不该抛异常。 */
    @Test
    fun invalidArgumentsReturnNull() {
        assertTrue(PixelResampler.resample(IntArray(0), 0, 0, 4, 4, ResamplePlan.BILINEAR) == null)
        assertTrue(PixelResampler.resample(IntArray(4), 2, 2, 0, 4, ResamplePlan.BILINEAR) == null)
        assertTrue(PixelResampler.resample(IntArray(4), 2, 2, 4, 0, ResamplePlan.BILINEAR) == null)
        // 像素数组比声明的尺寸小：拒绝而不是越界
        assertTrue(PixelResampler.resample(IntArray(3), 2, 2, 4, 4, ResamplePlan.BILINEAR) == null)
    }

    /** 单色图重采样后仍是同一颜色（核归一化正确、无亮度漂移）。 */
    @Test
    fun flatColorSurvivesResampling() {
        val src = IntArray(5 * 5) { argb(255, 77, 133, 199) }
        for (algo in algos) {
            val out = PixelResampler.resample(src, 5, 5, 3, 8, algo)!!
            for (c in out) {
                assertTrue("algo=$algo r=${red(c)}", abs(red(c) - 77) <= 1)
                assertTrue("algo=$algo g=${green(c)}", abs(green(c) - 133) <= 1)
                assertTrue("algo=$algo b=${blue(c)}", abs(blue(c) - 199) <= 1)
            }
        }
    }

    /** 设置项的三档都必须走 CPU 重采样；AUTO 不走（交给 GPU）。 */
    @Test
    fun onlyRealAlgorithmsTriggerCpuResampling() {
        assertTrue(ResamplePlan.isRealAlgorithm(ResamplePlan.BILINEAR))
        assertTrue(ResamplePlan.isRealAlgorithm(ResamplePlan.BICUBIC))
        assertTrue(ResamplePlan.isRealAlgorithm(ResamplePlan.LANCZOS))
        assertTrue(!ResamplePlan.isRealAlgorithm(0))
        assertTrue(!ResamplePlan.isRealAlgorithm(99))
    }

    /** 档位常量与算法常量必须一一对应，否则设置页选的档位会喂错算法。 */
    @Test
    fun settingTiersMapToTheRightKernels() {
        assertEquals(
            ResamplePlan.BILINEAR,
            com.furflix.app.data.repository.FurRepository.IMAGE_INTERPOLATION_BILINEAR
        )
        assertEquals(
            ResamplePlan.BICUBIC,
            com.furflix.app.data.repository.FurRepository.IMAGE_INTERPOLATION_BICUBIC
        )
        assertEquals(
            ResamplePlan.LANCZOS,
            com.furflix.app.data.repository.FurRepository.IMAGE_INTERPOLATION_LANCZOS
        )
    }
}
