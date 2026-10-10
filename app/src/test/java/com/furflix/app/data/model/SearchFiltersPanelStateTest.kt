package com.furflix.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索面板状态派生逻辑 —— 覆盖 K-006 重构后徽标的数据源。
 *
 * `advancedFilterCount` 驱动面板顶栏的 "[N]" 生效筛选数徽标;
 * 之前无任何测试。此处锁定其分组语义:同一维度无论取消几项都只计 1。
 */
class SearchFiltersPanelStateTest {

    // ---------------------------------------------------------------- 默认态

    @Test
    fun `default filters report zero advanced filters`() {
        assertEquals(0, SearchFilters().advancedFilterCount)
    }

    // ---------------------------------------------------------------- 各维度

    @Test
    fun `keywords count once`() {
        assertEquals(1, SearchFilters(keywords = listOf("fox")).advancedFilterCount)
        assertEquals(1, SearchFilters(keywords = listOf("fox", "wolf")).advancedFilterCount)
    }

    @Test
    fun `genders count once`() {
        assertEquals(1, SearchFilters(genders = listOf("male")).advancedFilterCount)
        assertEquals(1, SearchFilters(genders = listOf("male", "female")).advancedFilterCount)
    }

    @Test
    fun `species counts once`() {
        assertEquals(1, SearchFilters(species = 1).advancedFilterCount)
    }

    @Test
    fun `rating group counts once no matter how many are off`() {
        assertEquals(1, SearchFilters(ratingAdult = false).advancedFilterCount)
        assertEquals(
            1,
            SearchFilters(ratingAdult = false, ratingMature = false).advancedFilterCount
        )
        assertEquals(
            1,
            SearchFilters(ratingGeneral = false, ratingMature = false, ratingAdult = false)
                .advancedFilterCount
        )
    }

    @Test
    fun `content type group counts once`() {
        assertEquals(1, SearchFilters(typePhoto = false).advancedFilterCount)
        assertEquals(
            1,
            SearchFilters(typePhoto = false, typeMusic = false, typeStory = false)
                .advancedFilterCount
        )
    }

    @Test
    fun `non default range counts`() {
        assertEquals(1, SearchFilters(range = "7days").advancedFilterCount)
        assertEquals(0, SearchFilters(range = "all").advancedFilterCount)
    }

    @Test
    fun `non default mode counts`() {
        assertEquals(1, SearchFilters(mode = "any").advancedFilterCount)
        assertEquals(0, SearchFilters(mode = "extended").advancedFilterCount)
    }

    @Test
    fun `non default order counts`() {
        assertEquals(1, SearchFilters(orderBy = "relevancy").advancedFilterCount)
        assertEquals(0, SearchFilters(orderBy = "date").advancedFilterCount)
    }

    @Test
    fun `sort direction counts once and shares the sort dimension`() {
        // 方向单独改动计 1
        assertEquals(1, SearchFilters(orderDirection = "asc").advancedFilterCount)
        // order-by 与 order-direction 同属一个维度:两者都非默认仍只计 1
        assertEquals(
            1,
            SearchFilters(orderBy = "relevancy", orderDirection = "asc").advancedFilterCount
        )
        assertEquals(0, SearchFilters(orderBy = "date", orderDirection = "desc").advancedFilterCount)
    }

    @Test
    fun `category and art type count as their own dimensions`() {
        assertEquals(1, SearchFilters(category = 2).advancedFilterCount)
        assertEquals(0, SearchFilters(category = 0).advancedFilterCount)
        assertEquals(1, SearchFilters(artType = 100).advancedFilterCount)
        assertEquals(0, SearchFilters(artType = 0).advancedFilterCount)
    }

    @Test
    fun `dimensions accumulate independently`() {
        val filters = SearchFilters(
            keywords = listOf("fox"),
            genders = listOf("male"),
            category = 2,
            artType = 100,
            species = 3,
            ratingAdult = false,
            typePhoto = false,
            range = "30days",
            mode = "any",
            orderBy = "relevancy",
        )
        // 10 个维度各计 1
        assertEquals(10, filters.advancedFilterCount)
    }

    // ---------------------------------------------------------------- range 归一化

    @Test
    fun `normalizedRange maps legacy values to FA native enums`() {
        assertEquals("7days", SearchFilters(range = "1week").normalizedRange)
        assertEquals("30days", SearchFilters(range = "1month").normalizedRange)
    }

    @Test
    fun `normalizedRange passes through valid values`() {
        listOf("1day", "3days", "7days", "30days", "90days", "1year", "3years", "5years", "all", "manual")
            .forEach { assertEquals(it, SearchFilters(range = it).normalizedRange) }
    }

    // ---------------------------------------------------------------- perpage 钳制

    @Test
    fun `perpage is clamped into FA native range in search form`() {
        val tooSmall = SearchFilters(perpage = 1).buildSearchForm("q", 1)
        assertEquals(SearchFilters.PERPAGE_MIN.toString(), tooSmall.first { it.first == "perpage" }.second)

        val tooBig = SearchFilters(perpage = 999).buildSearchForm("q", 1)
        assertEquals(SearchFilters.PERPAGE_MAX.toString(), tooBig.first { it.first == "perpage" }.second)
    }

    @Test
    fun `search form always carries q page perpage`() {
        val form = SearchFilters().buildSearchForm("cat", 2).map { it.first }
        assertTrue(form.containsAll(listOf("q", "page", "perpage")))
    }

    @Test
    fun `shared url carries clamped perpage`() {
        val url = SearchFilters(perpage = 999).toFaSearchUrl("cat")
        assertTrue(url.contains("perpage=${SearchFilters.PERPAGE_MAX}"))
    }

    // ---------------------------------------------------------------- 预设

    @Test
    fun `search preset round trips through json`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val preset = SearchPreset(
            name = "my fox search",
            query = "fox",
            filters = SearchFilters(keywords = listOf("fox"), range = "30days")
        )
        val encoded = json.encodeToString(SearchPreset.serializer(), preset)
        assertEquals(preset, json.decodeFromString(SearchPreset.serializer(), encoded))
    }

    @Test
    fun `search preset defaults filters when absent`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString(SearchPreset.serializer(), """{"name":"n","query":"q"}""")
        assertEquals(SearchFilters(), decoded.filters)
    }
}
