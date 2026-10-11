package com.furflix.app.utils

import android.graphics.Bitmap

/**
 * 位图重采样（可选插值算法）的 Android 落地：把 [src] 上的一块区域按
 * Bilinear / Bicubic / Lanczos 重采样到目标尺寸。
 *
 * 为什么需要它：android.graphics 的 Canvas/Paint 只提供双线性（filterBitmap）
 * 与最近邻两档，双三次与兰索斯在 Java 层拿不到（Skia 的 SkSamplingOptions
 * 未开放）。要在放大时真正获得"锐利无损"的重采样，只能自己按核函数卷积。
 *
 * 本类只负责位图读写与区域裁剪，像素运算全在纯 Kotlin 的 [PixelResampler] 里——
 * 那边能在 JVM 单测里逐像素断言"确实在插值"，而依赖 android.graphics 的部分
 * 无法单测。两趟可分离卷积、预乘 alpha 等细节见 [PixelResampler] 的注释。
 *
 * 线程：纯函数式，不持有状态，调用方负责放到后台线程。
 */
object BitmapResampler {

    /**
     * 从 [src] 的 [srcLeft],[srcTop],[srcWidth],[srcHeight] 区域重采样出
     * [outWidth]×[outHeight] 的新位图。算法 [algo] 取 [ResamplePlan.BILINEAR] /
     * [ResamplePlan.BICUBIC] / [ResamplePlan.LANCZOS]。
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

        // 区域整体读出（一次 getPixels），后续全在数组上算，避免逐像素 JNI 往返。
        // 注意：硬件位图读不回像素（getPixels 抛异常），调用方必须用
        // allowHardware(false) 请求解码——ZoomableImageViewer 就是这么做的。
        val srcPixels = IntArray(w * h)
        runCatching { src.getPixels(srcPixels, 0, w, left, top, w, h) }.getOrNull() ?: return null

        val out = PixelResampler.resample(srcPixels, w, h, outWidth, outHeight, algo) ?: return null

        return runCatching {
            Bitmap.createBitmap(out, outWidth, outHeight, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }
}
