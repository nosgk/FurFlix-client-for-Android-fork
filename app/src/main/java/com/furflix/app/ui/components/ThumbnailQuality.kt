package com.furflix.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.furflix.app.data.remote.DynamicThumbnail

/**
 * 缩略图档位偏好（DynamicThumbnail.TIER_AUTO = 自动按显示尺寸协商；
 * 其余值 = 强制使用该像素档位）。卡片、画廊块与预取都在组合树里读它，
 * 由 FurFlixNavGraph 统一提供，避免逐屏传参。
 */
val LocalThumbnailQuality = staticCompositionLocalOf { DynamicThumbnail.TIER_AUTO }
