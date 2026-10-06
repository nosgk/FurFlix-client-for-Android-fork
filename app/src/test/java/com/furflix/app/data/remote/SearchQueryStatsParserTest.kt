package com.furflix.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * #query-stats 解析回归样本（2026-10-07 浏览器实测抓取的站点原样 DOM）：
 * 逐词 div 的第一个 span.font-large 是词、最后一个是命中作品数；
 * 注释里的"出现次数"是 Comment 节点，不得被当成元素选中。
 */
class SearchQueryStatsParserTest {

    private val sampleHtml = """
        <html><body>
        <div id="query-stats">
          <div>
            <h3 class="inline"><span class="font-large">vore</span></h3> found
            <!--<span class="font-large">1438654</span> times--> in
            <span class="font-large">1049646</span> submissions.
          </div>
          <div>
            <h3 class="inline"><span class="font-large">paw</span></h3> found
            <!--<span class="font-large">780480</span> times--> in
            <span class="font-large">509114</span> submissions.
          </div>
          (1 - 48 of 12346), 0.064 sec.
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `parses per-term counts range and time`() {
        val el = org.jsoup.Jsoup.parse(sampleHtml).selectFirst("#query-stats")
        val stats = SearchQueryStatsParser.parse(el)

        assertNotNull(stats)
        assertEquals(mapOf("vore" to 1049646L, "paw" to 509114L), stats!!.perTerm)
        assertEquals(1L, stats.shownFrom)
        assertEquals(48L, stats.shownTo)
        assertEquals(12346L, stats.totalResults)
        assertEquals("0.064", stats.elapsedSeconds)
    }

    @Test
    fun `missing stats element yields null`() {
        val doc = org.jsoup.Jsoup.parse("<html><body><p>no stats here</p></body></html>")
        assertNull(SearchQueryStatsParser.parse(doc.selectFirst("#query-stats")))
    }

    @Test
    fun `thousands separators in total are handled`() {
        val html = """
            <html><body><div id="query-stats">
              <div><h3 class="inline"><span class="font-large">fox</span></h3>
              found in <span class="font-large">1,204,577</span> submissions.</div>
              (1 - 24 of 9,999), 0.1 sec.
            </div></body></html>
        """.trimIndent()
        val stats = SearchQueryStatsParser.parse(org.jsoup.Jsoup.parse(html).selectFirst("#query-stats"))
        assertEquals(9_999L, stats!!.totalResults)
        assertEquals(1_204_577L, stats.perTerm["fox"])
    }
}
