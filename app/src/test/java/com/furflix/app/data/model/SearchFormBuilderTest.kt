package com.furflix.app.data.model

import com.furflix.app.data.SEARCH_PAGE_SIZE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFormBuilderTest {

    @Test
    fun `form has fixed order with q page perpage leading`() {
        val form = SearchFilters().buildSearchForm("cat", 3)
        assertEquals(
            listOf(
                "q", "page", "perpage",
                "order-by", "order-direction",
                "rating-general", "rating-mature", "rating-adult",
                "type-art", "type-flash", "type-photo", "type-music", "type-story", "type-poetry",
                "range", "mode"
            ),
            form.map { it.first }
        )
        assertEquals("cat", form[0].second)
        assertEquals("3", form[1].second)
        assertEquals(SEARCH_PAGE_SIZE.toString(), form[2].second)
        assertEquals("extended", form.last().second)
    }

    @Test
    fun `extended mode joins keywords into a single group`() {
        val filters = SearchFilters(keywords = listOf("cute", "!feral"))
        assertEquals("@keywords cute !feral", filters.buildKeywordQuery(""))
        assertEquals("fox @keywords cute !feral", filters.buildKeywordQuery("fox"))
    }

    @Test
    fun `non-extended mode keeps bare keywords`() {
        val filters = SearchFilters(keywords = listOf("cute", "feral"), mode = "any")
        assertEquals("cute feral", filters.buildKeywordQuery(""))
    }

    @Test
    fun `disabled ratings and types are omitted from the form`() {
        val keys = SearchFilters(ratingAdult = false, typePoetry = false).buildSearchForm("q", 1).map { it.first }
        assertFalse("rating-adult" in keys)
        assertFalse("type-poetry" in keys)
        assertTrue("rating-general" in keys)
        assertTrue("type-art" in keys)
    }

    @Test
    fun `keyword-only search still sends the group as q`() {
        val form = SearchFilters(keywords = listOf("dragon")).buildSearchForm("", 1)
        assertEquals("@keywords dragon", form[0].second)
    }

    @Test
    fun `species included only when set`() {
        // species=0 是 App 的"未设置"约定：键整体省略（FA 表单无 0 项）
        val keys = SearchFilters().buildSearchForm("q", 1).map { it.first }
        assertFalse("species" in keys)
        val form = SearchFilters(species = 4000).buildSearchForm("q", 1)
        assertTrue("species" in form.map { it.first })
        assertEquals("4000", form.first { it.first == "species" }.second)
    }

    @Test
    fun `legacy range values normalize to FA native enums`() {
        assertEquals("7days", SearchFilters(range = "1week").normalizedRange)
        assertEquals("30days", SearchFilters(range = "1month").normalizedRange)
        assertEquals("all", SearchFilters(range = "all").normalizedRange)
        assertEquals(
            "7days",
            SearchFilters(range = "1week").buildSearchForm("q", 1).first { it.first == "range" }.second
        )
    }

    @Test
    fun `manual range emits range_from and range_to date pairs`() {
        // 2026-10-07 /search/ 实测：range=manual + range_from/range_to（yyyy-MM-dd）服务端生效
        val form = SearchFilters(
            range = "manual",
            rangeFrom = "2026-09-01",
            rangeTo = "2026-10-07"
        ).buildSearchForm("q", 1)
        assertEquals("manual", form.first { it.first == "range" }.second)
        assertEquals("2026-09-01", form.first { it.first == "range_from" }.second)
        assertEquals("2026-10-07", form.first { it.first == "range_to" }.second)
        // 非 manual 时不得携带日期对
        val keys = SearchFilters().buildSearchForm("q", 1).map { it.first }
        assertFalse("range_from" in keys)
        assertFalse("range_to" in keys)
    }

    @Test
    fun `genders are merged into the keywords group like FA site JS`() {
        // gender 复选框无表单参数：站点 gender_checkboxes_search.js 把所选性别
        // 并进 @keywords 组，这里保持同一语义
        val filters = SearchFilters(keywords = listOf("cute"), genders = listOf("male", "female"))
        assertEquals("@keywords cute male female", filters.buildKeywordQuery(""))
        assertEquals("fox @keywords cute male female", filters.buildKeywordQuery("fox"))
        val keys = filters.buildSearchForm("q", 1).map { it.first }
        assertFalse("gender" in keys)
    }

    @Test
    fun `genders-only search still sends the group as q`() {
        assertEquals("@keywords male", SearchFilters(genders = listOf("male")).buildSearchForm("", 1)[0].second)
    }

    @Test
    fun `perpage is clamped to FA native choices`() {
        assertEquals("24", SearchFilters(perpage = 10).buildSearchForm("q", 1)[2].second)
        assertEquals("72", SearchFilters(perpage = 999).buildSearchForm("q", 1)[2].second)
        assertEquals("48", SearchFilters(perpage = 48).buildSearchForm("q", 1)[2].second)
    }

    @Test
    fun `browse map keeps single-select gender from the first choice`() {
        val map = SearchFilters(genders = listOf("male", "female")).toBrowseQueryMap()
        assertEquals("male", map["gender"])
    }

    @Test
    fun `fa search url mirrors the canonical GET form`() {
        val url = SearchFilters().toFaSearchUrl("cat")
        assertTrue(url.startsWith("https://www.furaffinity.net/search/?"))
        val keys = url.substringAfter("?").split("&").map { it.substringBefore("=") }
        assertEquals(
            listOf(
                "q", "page", "perpage", "order-by", "order-direction",
                "rating-general", "rating-mature", "rating-adult",
                "type-art", "type-flash", "type-photo", "type-music", "type-story", "type-poetry",
                "range", "mode"
            ),
            keys
        )
        assertTrue(url.contains("perpage=72"))
        assertTrue(url.contains("q=cat"))
    }

    @Test
    fun `fa search url encodes keywords group and gender terms`() {
        // URLEncoder：空格 → "+"，"@" → "%40"
        val url = SearchFilters(genders = listOf("male")).toFaSearchUrl("cat")
        assertTrue(url.contains("q=cat+%40keywords+male"))
        // perpage 越界钳制同步生效
        val clamped = SearchFilters(perpage = 999).toFaSearchUrl("")
        assertTrue(clamped.contains("perpage=72"))
    }
}
