package com.furflix.app.data.model

import kotlinx.serialization.Serializable

/**
 * 详情页解析出的统计快照，按帖子 id 落地：
 *  - 列表卡片/画廊块的统计浮层（views/favorites/comments/favorited）；
 *  - “浏览历史”栏目的展示字段（标题/作者/缩略图等）；
 *  - 卡片文件类型徽标（fileType = FA "Type" 文本如 Flash/Story/Music，
 *    fileExt = 从原图/下载链接截取的小写扩展名）。
 * FA 列表页 HTML 不含这些数字，只能由详情页加载/统计预热回填。
 * 展示字段对旧缓存条目缺省（缺字段 = 空串），历史栏会跳过无标题条目。
 *
 * visited：浏览历史只记“用户真正点进去”的帖子。统计预热（各画廊列表页）写入
 * 显式 false；旧缓存条目缺该字段时视为 true，避免升级即清空历史。
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
    val ratio: Float = 0f,
    val fileType: String = "",
    val fileExt: String = "",
    val visited: Boolean = true
) {
    companion object {
        /**
         * 详情解析结果 → 卡片统计快照的唯一映射（MainViewModel 与统计预热器共用，
         * 防止两边字段漂移）。favorited/favorites 覆盖参数供收藏乐观更新使用；
         * visited=false 的预热条目不进浏览历史。
         */
        fun of(
            sub: Submission,
            visited: Boolean = false,
            favorited: Boolean = sub.isFavorited,
            favorites: Int = sub.favorites
        ): CardStats = CardStats(
            views = sub.views,
            favorites = favorites,
            comments = sub.comments,
            favorited = favorited,
            title = sub.title,
            author = sub.author,
            authorUsername = sub.authorUsername,
            thumbnailUrl = sub.thumbnailUrl,
            rating = sub.rating,
            ratio = sub.thumbnailWidthOnHeightRatio,
            fileType = sub.fileType,
            fileExt = sub.fileExt,
            visited = visited
        )
    }
}
