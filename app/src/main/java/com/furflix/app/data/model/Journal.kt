package com.furflix.app.data.model

/**
 * FA 日志（/journal/{id}/）。
 * 列表（/journals/{user}/）携带 id/title/date/excerpt/commentCount/rating；
 * 详情解析额外填充 author/body/footer/commentList。
 * 正文为站点 HTML（BBCode 渲染产物），用户侧翻译走 translate/ 的
 * JOURNAL 类别，不进 i18n 字符串资源体系。
 */
data class Journal(
    val id: String = "",
    val title: String = "",
    val date: String = "",
    val timestamp: Long = 0,
    val rating: String = "",
    // 列表页摘要（.journal-body 纯文本截断）；详情页为空串（正文见 body）
    val excerpt: String = "",
    val commentCount: Int = 0,
    // ── 详情专属字段 ──
    val author: String = "",
    val authorUsername: String = "",
    val authorAvatar: String = "",
    val body: String = "",
    // 日志页脚（站点原文，如 commission 状态行）
    val footer: String = "",
    val commentList: List<Comment> = emptyList()
)
