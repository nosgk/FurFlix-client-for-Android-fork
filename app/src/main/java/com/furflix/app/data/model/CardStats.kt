package com.furflix.app.data.model

import kotlinx.serialization.Serializable

/**
 * 详情页解析出的统计快照，按帖子 id 落地供列表卡片回填。
 * FA 列表页 HTML 不含 views/favorites/comments，卡片浮层只能展示
 * 本地已看过的帖子的统计（详见 CardStatsCache）。
 */
@Serializable
data class CardStats(
    val views: Int = 0,
    val favorites: Int = 0,
    val comments: Int = 0,
    val favorited: Boolean = false
)
