package com.furflix.app.utils

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin

/**
 * 一维重采样权重预计算（纯 Kotlin，无 Android 依赖，便于 JVM 单测）。
 *
 * 放大/缩小时的插值质量由核函数决定，本类把"选哪些源像素、各占多少权重"
 * 这一步算好：输出 N 个像素，每个像素取连续的 `taps` 个源像素加权求和。
 * 采样位置一律按"像素中心"对齐（输出像素 i 的中心映射回源坐标再减 0.5），
 * 否则整幅图会偏移半个像素——缩略图与放大图之间会看到明显错位。
 *
 * 三种算法（数值与 FurRepository.IMAGE_INTERPOLATION_* 对齐）：
 * - 双线性：2 抽头，最快；放大平滑但细节软。
 * - 双三次（Catmull-Rom，a = -0.5）：4 抽头，锐度与平滑兼顾。
 * - 兰索斯（Lanczos-3，a = 3）：6 抽头，最锐利、振铃最明显，最慢。
 *
 * 缩小时核函数要按缩放比展宽（低通），否则会产生摩尔纹/锯齿——这就是
 * "多级降采样"比直接取最近邻清楚的根本原因。
 */
object ResamplePlan {

    /** 插值算法取值（与 FurRepository.IMAGE_INTERPOLATION_* 对齐）。 */
    const val BILINEAR = 1
    const val BICUBIC = 2
    const val LANCZOS = 3

    /** 是否为需要 CPU 卷积重采样的真实算法档位（AUTO/未知值走 GPU）。 */
    fun isRealAlgorithm(algo: Int): Boolean =
        algo == BILINEAR || algo == BICUBIC || algo == LANCZOS

    /** 核半径（抽头数 = 2 * support）。 */
    fun support(algo: Int): Int = when (algo) {
        BICUBIC -> 2
        LANCZOS -> 3
        else -> 1
    }

    /**
     * 一维重采样计划：`starts[i]` 是输出像素 i 的起始源索引，
     * 其后 `taps` 个权重在 `weights[i * taps + k]`，权重已归一化。
     */
    class Plan(val taps: Int, val starts: IntArray, val weights: FloatArray)

    /**
     * 生成重采样计划。
     *
     * 缩小时核函数按缩放比展宽（低通抗锯齿）——每输出像素覆盖 `scale` 个源像素，
     * 不展宽就会直接丢弃高频、产生摩尔纹；展宽后抽头数也要同步增加，否则截断的
     * 核会让权重和不为 1（亮度漂移）。
     *
     * @param srcLen 源一维长度（像素数）
     * @param srcStart 源区间起点（可含小数，对应裁剪区域）
     * @param srcSpan 源区间长度（像素）
     * @param outLen 输出像素数
     */
    fun plan(algo: Int, srcLen: Int, srcStart: Double, srcSpan: Double, outLen: Int): Plan {
        val baseSupport = support(algo)
        val scale = if (outLen > 0) srcSpan / outLen else 1.0
        // 缩小（scale > 1）才展宽核；放大时保持核宽，否则会把细节抹平
        val filterScale = if (scale > 1.0) scale else 1.0
        val radius = baseSupport * filterScale
        val taps = (2 * ceil(radius).toInt()).coerceAtLeast(2)
        val starts = IntArray(outLen)
        val weights = FloatArray(outLen * taps)
        if (srcLen <= 0 || outLen <= 0) return Plan(taps, starts, weights)

        for (i in 0 until outLen) {
            val center = srcStart + (i + 0.5) * scale - 0.5
            val first = floor(center - radius).toInt() + 1
            starts[i] = first
            var sum = 0.0
            for (k in 0 until taps) {
                val w = kernel(algo, (center - (first + k)) / filterScale) / filterScale
                weights[i * taps + k] = w.toFloat()
                sum += w
            }
            // 归一化：边缘钳制后权重和不为 1 会导致亮度漂移（暗边/亮边）
            if (sum != 0.0) {
                val inv = (1.0 / sum).toFloat()
                for (k in 0 until taps) weights[i * taps + k] *= inv
            }
        }
        return Plan(taps, starts, weights)
    }

    /** 核函数（已按 a 展开，避免每次采样重新分支）。 */
    fun kernel(algo: Int, x: Double): Double {
        val ax = abs(x)
        return when (algo) {
            BICUBIC -> {
                // Catmull-Rom（a = -0.5）
                when {
                    ax < 1.0 -> (1.5 * ax - 2.5) * ax * ax + 1.0
                    ax < 2.0 -> ((-0.5 * ax + 2.5) * ax - 4.0) * ax + 2.0
                    else -> 0.0
                }
            }
            LANCZOS -> {
                if (ax < 3.0) {
                    if (ax < 1e-8) 1.0 else {
                        val px = PI * ax
                        (3.0 * sin(px) * sin(px / 3.0)) / (px * px)
                    }
                } else 0.0
            }
            else -> if (ax < 1.0) 1.0 - ax else 0.0
        }
    }

    /** 把源索引钳到 [0, srcLen-1]（边缘像素重复延拓）。 */
    fun clampIndex(index: Int, srcLen: Int): Int = when {
        index < 0 -> 0
        index >= srcLen -> srcLen - 1
        else -> index
    }
}
