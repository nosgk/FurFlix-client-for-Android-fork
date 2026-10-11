package com.furflix.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.FilterQuality
import coil3.request.bitmapConfig
import com.furflix.app.data.repository.FurRepository

/**
 * 图像重采样插值档位（FurRepository.IMAGE_INTERPOLATION_*），由设置页写入、
 * NavGraph 在组合树根部提供。绘制期采样质量，影响放大/缩小时的重采样观感，
 * 不额外拉取字节。
 */
val LocalImageInterpolation = staticCompositionLocalOf { FurRepository.IMAGE_INTERPOLATION_AUTO }

/**
 * 高保真解码开关（FurRepository.imageFidelityFlow）：开启后静态图解码强制
 * ARGB_8888 + 精确采样，消除降采样与低色深带来的细节损失。默认关——
 * 缩略图网格的内存代价不划算，详情页/全屏查看才是它的主战场。
 */
val LocalImageFidelity = staticCompositionLocalOf { false }

/**
 * 插值档位 → Compose [FilterQuality]：GPU 绘制路径上的采样质量。
 *
 * 注意 GPU 只区分"最近邻 / 双线性"两档——双三次与兰索斯在 android.graphics
 * 里拿不到（Skia 的 SkSamplingOptions 未开放），所以这里三档真实算法统一退回
 * [FilterQuality.Low]（双线性），由 CPU 重采样（[com.furflix.app.utils.BitmapResampler]）
 * 在图像查看器里补齐真实算法。AUTO 用 Compose 的默认值（双线性），
 * 不能退回 None——那是最近邻，会比现状更糊。
 */
fun imageFilterQuality(mode: Int): FilterQuality = when (mode) {
    FurRepository.IMAGE_INTERPOLATION_AUTO -> androidx.compose.ui.graphics.DefaultFilterQuality
    else -> FilterQuality.Low
}

/** 该档位是否需要 CPU 卷积重采样（双线性以上才值得算）。 */
fun needsCpuResample(mode: Int): Boolean =
    mode == FurRepository.IMAGE_INTERPOLATION_BILINEAR ||
        mode == FurRepository.IMAGE_INTERPOLATION_BICUBIC ||
        mode == FurRepository.IMAGE_INTERPOLATION_LANCZOS

/**
 * 把高保真解码落到具体请求上：强制 ARGB_8888 色彩 + EXACT 精度（按目标尺寸
 * 精确采样，不做"够用即可"的降采样近似）。未开启保真时原样返回，零开销。
 *
 * 只作用于静态图：GIF 走动图解码器，bitmapConfig 对它无意义。
 */
fun coil3.request.ImageRequest.Builder.applyFidelity(enabled: Boolean): coil3.request.ImageRequest.Builder =
    if (!enabled) this
    else bitmapConfig(android.graphics.Bitmap.Config.ARGB_8888)
        .precision(coil3.size.Precision.EXACT)

/**
 * 图片请求统一装饰：保真解码 + 显式尺寸档位。详情页主图、全屏缩放查看器与
 * 文档封面等"值得花内存换画质"的调用点使用。
 */
fun coil3.request.ImageRequest.Builder.applyImageQuality(
    fidelity: Boolean,
    size: coil3.size.Size? = null
): coil3.request.ImageRequest.Builder {
    applyFidelity(fidelity)
    if (size != null) size(size)
    return this
}
