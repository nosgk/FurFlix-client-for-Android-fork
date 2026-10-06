package com.furflix.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Submission(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val authorUsername: String = "",
    val authorAvatar: String = "",
    val thumbnailUrl: String = "",
    // Aspect ratio of the listing thumbnail, parsed from the img's data-width/
    // data-height attributes; 0 = unknown → UI falls back to a fixed ratio.
    val thumbnailWidthOnHeightRatio: Float = 0f,
    val fullImageUrl: String = "",
    val description: String = "",
    val rating: String = "",
    val category: String = "",
    val species: String = "",
    val gender: String = "",
    val views: Int = 0,
    val favorites: Int = 0,
    val comments: Int = 0,
    val isFavorited: Boolean = false,
    val favUrl: String = "",
    val date: String = "",
    val tags: List<String> = emptyList(),
    val fileUrl: String = "",
    val fileName: String = "",
    // 小写扩展名，从原图/下载链接截取（解析不出时为空串）；卡片类型徽标数据源之一
    val fileExt: String = "",
    // Flash 作品的原始画布尺寸（object#flash_embed 的 data-width/height）；0 = 未知
    val flashWidth: Int = 0,
    val flashHeight: Int = 0,
    // 小说(.txt)作品全文：详情加载时直接抓 Download 指向的原文（统计预热不加载）
    val storyText: String = "",
    val resolution: String = "",
    val fileSize: String = "",
    val fileType: String = "",
    // 作品所属画廊文件夹（view 页 sidebar-lower 的 Folders 区块；空=无/未解析出）
    val folders: List<GalleryFolder> = emptyList(),
    val commentList: List<Comment> = emptyList()
)

@Serializable
data class Comment(
    val id: String = "",
    val author: String = "",
    val authorUsername: String = "",
    val authorAvatar: String = "",
    val title: String = "",
    val text: String = "",
    val date: String = "",
    val timestamp: Long = 0,
    // Reply nesting level from the comment container's width style
    // (0 = top-level); 0 for rows parsed before this field existed.
    val indentation: Int = 0
)
