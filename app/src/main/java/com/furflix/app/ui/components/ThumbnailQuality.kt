package com.furflix.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.furflix.app.data.remote.DynamicThumbnail

/**
 * 缩略图档位偏好（DynamicThumbnail.TIER_AUTO = 自动按显示尺寸协商；
 * 其余值 = 强制使用该像素档位）。卡片、画廊块与预取都在组合树里读它，
 * 由 FurFlixNavGraph 统一提供，避免逐屏传参。
 */
val LocalThumbnailQuality = staticCompositionLocalOf { DynamicThumbnail.TIER_AUTO }

/**
 * 详情页画质档位偏好（FurRepository.DETAIL_QUALITY_AUTO = 跟随布局约束解码；
 * 其余正值 = 最长边像素上限；DETAIL_QUALITY_ORIGINAL = 不限，最高档原图）。
 * 详情页主图与全屏缩放查看器经组合树读取，同由 FurFlixNavGraph 提供。
 */
val LocalDetailImageQuality = staticCompositionLocalOf {
    com.furflix.app.data.repository.FurRepository.DETAIL_QUALITY_AUTO
}
