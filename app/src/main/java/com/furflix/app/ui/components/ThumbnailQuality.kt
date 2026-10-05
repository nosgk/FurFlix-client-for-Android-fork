package com.furflix.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.furflix.app.data.remote.DynamicThumbnail

/**
 * 缩略图清晰度偏好（DynamicThumbnail.QUALITY_*：0 自动 / 1 省流量 / 2 高清）。
 * 卡片与瀑布流预取都在组合树里读它,由 FurFlixNavGraph 统一提供,避免逐屏传参。
 */
val LocalThumbnailQuality = staticCompositionLocalOf { DynamicThumbnail.QUALITY_AUTO }
