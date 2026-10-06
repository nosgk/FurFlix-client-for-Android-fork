package com.furflix.app.data.model

import kotlinx.serialization.Serializable

/**
 * FA 搜索页 #query-stats 的服务端统计（2026-10-07 实测 DOM）：
 * - 逐词命中：`<div><h3 class="inline"><span class="font-large">vore</span></h3>
 *   found in <span class="font-large">1049646</span> submissions.</div>`
 * - 结果区间：`(1 - 48 of 12346), 0.064 sec.`
 * perTerm 键 = 站点分词后的搜索词，值 = 命中作品数（作品数口径，非出现次数）。
 */
@Serializable
data class SearchQueryStats(
    val perTerm: Map<String, Long> = emptyMap(),
    val shownFrom: Long? = null,
    val shownTo: Long? = null,
    val totalResults: Long? = null,
    val elapsedSeconds: String? = null
) {
    val hasTotal: Boolean
        get() = totalResults != null && shownFrom != null && shownTo != null
}

/** 一次搜索的完整回包：结果列表 + 服务端统计（统计可能缺失，不阻塞结果渲染）。 */
data class SearchResult(
    val submissions: List<Submission>,
    val stats: SearchQueryStats? = null
)
