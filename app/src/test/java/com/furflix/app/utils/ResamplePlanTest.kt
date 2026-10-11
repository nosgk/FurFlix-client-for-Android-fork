package com.furflix.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ceil

/**
 * 重采样计划的纯数学约束：这些性质一旦破坏，画面上会直接表现为
 * 整体偏移半像素、亮度漂移或缩小时出现摩尔纹。
 */
class ResamplePlanTest {

    private val algos = listOf(
        ResamplePlan.BILINEAR,
        ResamplePlan.BICUBIC,
        ResamplePlan.LANCZOS
    )

    /** 权重必须归一化：和不为 1 会让整幅图变亮/变暗。 */
    @Test
    fun weightsAreNormalized() {
        for (algo in algos) {
            val plan = ResamplePlan.plan(algo, srcLen = 100, srcStart = 0.0, srcSpan = 100.0, outLen = 37)
            for (i in 0 until 37) {
                var sum = 0.0
                for (k in 0 until plan.taps) sum += plan.weights[i * plan.taps + k]
                assertTrue(
                    "algo=$algo out=$i sum=$sum",
                    abs(sum - 1.0) < 1e-4
                )
            }
        }
    }

    /** 1:1 时必须是恒等：只有中心抽头权重为 1，其余为 0（否则图像会糊/位移）。 */
    @Test
    fun identityWhenScaleIsOne() {
        for (algo in algos) {
            val n = 16
            val plan = ResamplePlan.plan(algo, srcLen = n, srcStart = 0.0, srcSpan = n.toDouble(), outLen = n)
            for (i in 0 until n) {
                for (k in 0 until plan.taps) {
                    val idx = ResamplePlan.clampIndex(plan.starts[i] + k, n)
                    val w = plan.weights[i * plan.taps + k]
                    if (idx == i) {
                        assertTrue("algo=$algo i=$i w=$w", abs(w - 1f) < 1e-3)
                    } else {
                        assertTrue("algo=$algo i=$i idx=$idx w=$w", abs(w) < 1e-3)
                    }
                }
            }
        }
    }

    /**
     * 缩小时核必须展宽：抽头数要随缩放比增长（否则直接丢高频 → 摩尔纹）。
     * 放大时保持核宽（抽头数不变），否则细节被抹平。
     */
    @Test
    fun tapsWidenOnlyWhenDownscaling() {
        for (algo in algos) {
            val baseSupport = ResamplePlan.support(algo)
            val up = ResamplePlan.plan(algo, 10, 0.0, 10.0, 40) // 放大 4x
            assertEquals("upscale taps algo=$algo", baseSupport * 2, up.taps)

            val down = ResamplePlan.plan(algo, 100, 0.0, 100.0, 25) // 缩小 4x
            assertTrue(
                "downscale taps should grow: algo=$algo taps=${down.taps}",
                down.taps >= baseSupport * 2
            )
            assertEquals("downscale taps algo=$algo", 2 * ceil(baseSupport * 4.0).toInt(), down.taps)
        }
    }

    /**
     * 采样区间必须覆盖核半径（差 1 以内——floor 取整会把核的零点端点切掉，
     * 而核在 ±radius 处恰好为 0，截掉零权重不影响结果）。
     */
    @Test
    fun samplingWindowCoversKernelRadius() {
        for (algo in algos) {
            val plan = ResamplePlan.plan(algo, 200, 0.0, 200.0, 50)
            val baseSupport = ResamplePlan.support(algo)
            val radius = baseSupport * 4.0 // 缩小 4x
            for (i in 0 until 50) {
                val center = (i + 0.5) * 4.0 - 0.5
                val first = plan.starts[i]
                val last = first + plan.taps - 1
                assertTrue(
                    "algo=$algo i=$i first=$first center=$center",
                    first <= center - radius + 1.0
                )
                assertTrue(
                    "algo=$algo i=$i last=$last center=$center",
                    last >= center + radius - 1.0
                )
            }
        }
    }

    /** 边缘索引钳制：负索引与越界索引都要夹回有效范围。 */
    @Test
    fun clampsIndexToSourceBounds() {
        assertEquals(0, ResamplePlan.clampIndex(-5, 10))
        assertEquals(0, ResamplePlan.clampIndex(0, 10))
        assertEquals(9, ResamplePlan.clampIndex(9, 10))
        assertEquals(9, ResamplePlan.clampIndex(42, 10))
    }

    /** 核函数的基本形状：峰值 1、支撑区外为 0。 */
    @Test
    fun kernelShape() {
        for (algo in algos) {
            assertEquals(1.0, ResamplePlan.kernel(algo, 0.0), 1e-6)
        }
        // 支撑区外一律为 0
        assertEquals(0.0, ResamplePlan.kernel(ResamplePlan.BILINEAR, 1.5), 1e-9)
        assertEquals(0.0, ResamplePlan.kernel(ResamplePlan.BICUBIC, 2.5), 1e-9)
        assertEquals(0.0, ResamplePlan.kernel(ResamplePlan.LANCZOS, 3.5), 1e-9)
    }
}
