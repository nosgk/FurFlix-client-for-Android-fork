package com.furflix.app.data.model

import kotlinx.serialization.Serializable

/**
 * 详情页解析出的统计快照，按帖子 id 落地：
 *  - 列表卡片/画廊块的统计浮层（views/favorites/comments/favorited）；
 *  - “浏览历史”栏目的展示字段（标题/作者/缩略图等）。
 * FA 列表页 HTML 不含这些数字，只能由详情页加载时回填。
 * 展示字段对旧缓存条目缺省（缺字段 = 空串），历史栏会跳过无标题条目。
 */
@Serializable
data class CardStats(
    val views: Int = 0,
    val favorites: Int = 0,
    val comments: Int = 0,
    val favorited: Boolean = false,
    val title: String = "",
    val author: String = "",
    val authorUsername: String = "",
    val thumbnailUrl: String = "",
    val rating: String = "",
    val ratio: Float = 0f
)
