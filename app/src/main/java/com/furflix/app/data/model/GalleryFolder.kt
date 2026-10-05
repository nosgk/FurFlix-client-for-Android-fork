package com.furflix.app.data.model

/**
 * 用户主页的画廊文件夹（FA /gallery/{user}/folder/{id}/{slug}）。
 * [href] 为站内相对链接（page 1 直达）；选择器按真实 DOM 校准前保持容错——
 * 区块缺失/不可见时 folders 为空列表，不影响其他功能。
 */
data class GalleryFolder(
    val id: String,
    val name: String,
    val href: String,
    val thumbUrl: String = "",
    val count: Int = 0
)
