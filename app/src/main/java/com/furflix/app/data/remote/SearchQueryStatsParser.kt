package com.furflix.app.data.remote

import com.furflix.app.data.model.SearchQueryStats
import org.jsoup.nodes.Element

/**
 * FA /search/ 页 #query-stats 区块的纯函数解析器（可单测）。
 * DOM 实测（2026-10-07，登录态）：
 * ```
 * <div id="query-stats">
 *   <div><h3 class="inline"><span class="font-large">vore</span></h3> found
 *     <!--<span class="font-large">1438654</span> times-->
 *     in <span class="font-large">1049646</span> submissions.</div>
 *   <div>...同构...</div>
 *   (1 - 48 of 12346), 0.064 sec.
 * </div>
 * ```
 * 逐词 div 里第一个 span.font-large 是词、最后一个是命中作品数
 * （注释里的"出现次数"是 Comment 节点，jsoup 不会当元素选中）。
 */
object SearchQueryStatsParser {

    private val RANGE_REGEX = Regex("""\((\d+)\s*-\s*(\d+)\s+of\s+([\d,]+)\)""")
    private val TIME_REGEX = Regex(""",\s*([\d.]+)\s*sec""")

    fun parse(statsEl: Element?): SearchQueryStats? {
        if (statsEl == null) return null

        val perTerm = LinkedHashMap<String, Long>()
        for (div in statsEl.select("> div")) {
            val spans = div.select("span.font-large")
            val term = spans.firstOrNull()?.text()?.trim().orEmpty()
            val count = spans.lastOrNull()?.text()?.replace(",", "")?.toLongOrNull()
            if (term.isNotEmpty() && count != null) {
                perTerm[term] = count
            }
        }

        val whole = statsEl.text()
        val range = RANGE_REGEX.find(whole)?.groupValues
        val time = TIME_REGEX.find(whole)?.groupValues?.get(1)

        if (perTerm.isEmpty() && range == null) return null
        return SearchQueryStats(
            perTerm = perTerm,
            shownFrom = range?.get(1)?.toLongOrNull(),
            shownTo = range?.get(2)?.toLongOrNull(),
            totalResults = range?.get(3)?.replace(",", "")?.toLongOrNull(),
            elapsedSeconds = time
        )
    }
}
