package com.furflix.app.data.model

import com.furflix.app.data.remote.FaLink
import com.furflix.app.data.remote.parseFaLink
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 搜索状态 ↔ URL 双向同步契约：filters+query 经 [SearchFilters.toFaSearchUrl]
 * 生成 canonical URL，parseFaLink 必须还原出等价状态。锁定 toSearchQueryMap /
 * buildKeywordQuery / splitKeywordQuery / parseSearchFilters 四方镜像关系，
 * 防止单侧演进造成失同步（多米诺清单的"共享层双向消费"面）。
 */
class SearchFiltersRoundTripTest {

    private fun assertRoundTrip(query: String, filters: SearchFilters) {
        val url = filters.toFaSearchUrl(query)
        val link = parseFaLink(url) as? FaLink.Search ?: error("not routable: $url")
        assertEquals(query, link.query)
        assertEquals(filters.keywords, link.filters.keywords)
        assertEquals(filters.genders, link.filters.genders)
        assertEquals(filters.normalizedRange, link.filters.range)
        assertEquals(filters.rangeFrom, link.filters.rangeFrom)
        assertEquals(filters.rangeTo, link.filters.rangeTo)
        assertEquals(filters.orderBy, link.filters.orderBy)
        assertEquals(filters.orderDirection, link.filters.orderDirection)
        assertEquals(filters.mode, link.filters.mode)
        assertEquals(filters.category, link.filters.category)
        assertEquals(filters.artType, link.filters.artType)
        assertEquals(filters.species, link.filters.species)
        assertEquals(
            filters.perpage.coerceIn(SearchFilters.PERPAGE_MIN, SearchFilters.PERPAGE_MAX),
            link.filters.perpage
        )
        assertEquals(filters.ratingGeneral, link.filters.ratingGeneral)
        assertEquals(filters.ratingMature, link.filters.ratingMature)
        assertEquals(filters.ratingAdult, link.filters.ratingAdult)
        assertEquals(filters.typeArt, link.filters.typeArt)
        assertEquals(filters.typeFlash, link.filters.typeFlash)
        assertEquals(filters.typePhoto, link.filters.typePhoto)
        assertEquals(filters.typeMusic, link.filters.typeMusic)
        assertEquals(filters.typeStory, link.filters.typeStory)
        assertEquals(filters.typePoetry, link.filters.typePoetry)
    }

    @Test
    fun `default filters round trip`() = assertRoundTrip("fox", SearchFilters())

    @Test
    fun `keywords and genders fold into the query group and split back`() =
        assertRoundTrip(
            "fox",
            SearchFilters(keywords = listOf("red", "wolf"), genders = listOf("male", "non_binary"))
        )

    @Test
    fun `empty base query with keywords group`() =
        assertRoundTrip("", SearchFilters(keywords = listOf("!closed")))

    @Test
    fun `excluded keywords survive the group split`() =
        assertRoundTrip("fox", SearchFilters(keywords = listOf("!feral", "cute")))

    @Test
    fun `manual date range round trip`() =
        assertRoundTrip(
            "fox",
            SearchFilters(range = "manual", rangeFrom = "2026-01-01", rangeTo = "2026-06-30")
        )

    @Test
    fun `partial type and rating selection round trip`() =
        assertRoundTrip(
            "fox",
            SearchFilters(
                ratingGeneral = true,
                ratingMature = false,
                ratingAdult = false,
                typeArt = true,
                typeFlash = false,
                typePhoto = false,
                typeMusic = true,
                typeStory = false,
                typePoetry = false
            )
        )

    @Test
    fun `category arttype species and oversized perpage round trip`() =
        assertRoundTrip(
            "fox",
            SearchFilters(category = 2, artType = 7, species = 14080, perpage = 999)
        )

    @Test
    fun `relevancy ascending all-any mode round trip`() =
        assertRoundTrip("fox", SearchFilters(orderBy = "relevancy", orderDirection = "asc", mode = "all"))

    @Test
    fun `split ignores free text without keywords group`() {
        assertEquals(null, SearchFilters.splitKeywordQuery("red fox"))
        assertEquals(null, SearchFilters.splitKeywordQuery("@keywordsfox male"))
        assertEquals(null, SearchFilters.splitKeywordQuery(""))
    }

    @Test
    fun `split maps gender tokens by the site vocabulary`() {
        val (base, keywords, genders) = SearchFilters.splitKeywordQuery("fox @keywords red male non_binary")!!
        assertEquals("fox", base)
        assertEquals(listOf("red"), keywords)
        assertEquals(listOf("male", "non_binary"), genders)
    }

    @Test
    fun `split handles a group-only query`() {
        val (base, keywords, genders) = SearchFilters.splitKeywordQuery("@keywords female")!!
        assertEquals("", base)
        assertEquals(emptyList<String>(), keywords)
        assertEquals(listOf("female"), genders)
    }
}
