package com.furflix.app.data.model

/**
 * FA 画廊文件夹（/gallery/{user}/folder/{id}/{slug}）。
 * [href] 为站内相对链接（page 1 直达）；解析源为 /gallery/{user}/ 侧栏的
 * folder-list 区块（现代用户页已不再内嵌文件夹列表）。
 * [group] 为站点的自定义分组名（如 "Characters"，来自分组标题 h4）；
 * 站点分组属于用户自建内容，不进入 i18n 字符串体系。
 * 无分组（default-group）时为空串。作品详情页 sidebar-lower 的 Folders
 * 区块也复用本模型（strong=分组、span=文件夹名）。
 */
data class GalleryFolder(
    val id: String,
    val name: String,
    val href: String,
    val thumbUrl: String = "",
    val count: Int = 0,
    val group: String = ""
)
