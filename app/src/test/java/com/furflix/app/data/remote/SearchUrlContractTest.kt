package com.furflix.app.data.remote

import com.furflix.app.data.model.SearchFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索状态 ↔ FA 原生 URL 的双向同步契约(2026-10-10 以浏览器实测 /search/ 表单核对)。
 *
 * ## 实测基准(furaffinity.net/search/ 的 `#search-form` 侧栏)
 * | 参数 | 合法枚举 / 形态 |
 * | --- | --- |
 * | `q` | 自由文本;extended 模式下关键词组以 `@keywords ` 前缀并进 |
 * | `page` | 正整数 |
 * | `perpage` | 24 / 48 / 72 |
 * | `order-by` | relevancy / date / popularity |
 * | `order-direction` | asc / desc |
 * | `category` | 1=All + 38 项(id 见 FA_CATEGORIES) |
 * | `arttype` | 1=All + 51 项(id 见 FA_ART_TYPES) |
 * | `species` | 1=Any + 397 项 |
 * | `range` | 1day/3days/7days/30days/90days/1year/3years/5years/all/manual |
 * | `mode` | all / any / extended(默认 extended) |
 * | `rating-*` `type-*` | 勾选才发键,值恒为 "1" |
 *
 * 表单实测为 **GET**(action=/search/);上表参数经浏览器提交后服务端逐项回显,
 * 证明这些键名与取值即为接口契约。
 */
class SearchUrlContractTest {

    /** 面板能产生的 order-by 取值,必须落在 FA 的三项枚举内。 */
    private val FA_ORDER_BY = setOf("relevancy", "date", "popularity")
    private val FA_ORDER_DIRECTION = setOf("asc", "desc")
    private val FA_RANGE = setOf(
        "1day", "3days", "7days", "30days", "90days",
        "1year", "3years", "5years", "all", "manual"
    )
    private val FA_MODE = setOf("all", "any", "extended")

    // ---------------------------------------------------------------- 出参合法

    @Test
    fun `sort params are always valid FA enums`() {
        // 面板排序芯片只产生这三个值
        FA_ORDER_BY.forEach { ob ->
            FA_ORDER_DIRECTION.forEach { od ->
                val url = SearchFilters(orderBy = ob, orderDirection = od).toFaSearchUrl("fox")
                assertTrue("order-by=$ob 应出现在 URL", url.contains("order-by=$ob"))
                assertTrue("order-direction=$od 应出现在 URL", url.contains("order-direction=$od"))
            }
        }
    }

    @Test
    fun `range param is always a valid FA enum after normalization`() {
        // 客户端历史值经 normalizedRange 归一化后才发出
        assertEquals("7days", SearchFilters(range = "1week").normalizedRange)
        assertEquals("30days", SearchFilters(range = "1month").normalizedRange)
        SearchFilters(range = "1week").toFaSearchUrl("x").let {
            assertTrue("旧值 1week 不得直接发出", !it.contains("range=1week"))
            assertTrue(it.contains("range=7days"))
        }
        SearchFilters(range = "1month").toFaSearchUrl("x").let {
            assertTrue(!it.contains("range=1month"))
            assertTrue(it.contains("range=30days"))
        }
    }

    @Test
    fun `manual range carries from and to dates`() {
        val url = SearchFilters(range = "manual", rangeFrom = "2026-01-01", rangeTo = "2026-03-01")
            .toFaSearchUrl("x")
        assertTrue(url.contains("range=manual"))
        assertTrue(url.contains("range_from=2026-01-01"))
        assertTrue(url.contains("range_to=2026-03-01"))
    }

    @Test
    fun `checked only params are emitted with value 1`() {
        val url = SearchFilters(
            ratingGeneral = true, ratingMature = false, ratingAdult = false,
            typeArt = true, typePhoto = false
        ).toFaSearchUrl("x")
        assertTrue(url.contains("rating-general=1"))
        assertTrue("未勾选不得发键", !url.contains("rating-mature"))
        assertTrue(!url.contains("rating-adult"))
        assertTrue(url.contains("type-art=1"))
        assertTrue(!url.contains("type-photo"))
    }

    @Test
    fun `zero category arttype species are omitted`() {
        val url = SearchFilters(category = 0, artType = 0, species = 0).toFaSearchUrl("x")
        assertTrue(!url.contains("category="))
        assertTrue(!url.contains("arttype="))
        assertTrue(!url.contains("species="))
    }

    @Test
    fun `non-zero category arttype species are emitted`() {
        val url = SearchFilters(category = 2, artType = 100, species = 6075).toFaSearchUrl("x")
        assertTrue(url.contains("category=2"))
        assertTrue(url.contains("arttype=100"))
        assertTrue(url.contains("species=6075"))
    }

    @Test
    fun `perpage is clamped to FA native tiers`() {
        listOf(1, 23, 24, 48, 72, 100, 999).forEach { input ->
            val url = SearchFilters(perpage = input).toFaSearchUrl("x")
            val m = Regex("perpage=(\\d+)").find(url)
            assertNotNull("perpage 应存在", m)
            val v = m!!.groupValues[1].toInt()
            assertTrue("perpage=$input 应钳到 24..72,实为 $v", v in 24..72)
            assertTrue("应落在 FA 档位 24/48/72", v in setOf(24, 48, 72))
        }
    }

    @Test
    fun `extended mode groups keywords into a single @keywords group`() {
        val url = SearchFilters(
            mode = "extended",
            keywords = listOf("fox", "wolf")
        ).toFaSearchUrl("")
        // 关键词与性别必须并进同一组,否则 "!tag" 排除失效
        assertTrue("q 应含 @keywords 组", url.contains("%40keywords") || url.contains("@keywords"))
        assertTrue(url.contains("fox"))
        assertTrue(url.contains("wolf"))
    }

    @Test
    fun `mode is always a valid FA enum`() {
        FA_MODE.forEach { mode ->
            val url = SearchFilters(mode = mode).toFaSearchUrl("x")
            assertTrue("mode=$mode 应发出", url.contains("mode=$mode"))
        }
    }

    // ---------------------------------------------------------------- 入参解析

    /**
     * 入参解析走公开入口 [FaLinks.parseFaLink](URL → [FaLink.Search]),
     * 其内部再调私有的 parseSearchFilters 做枚举白名单钳制。
     */
    private fun parseFilters(url: String): SearchFilters? =
        (com.furflix.app.data.remote.FaLinks.parseFaLink(url)
            as? com.furflix.app.data.remote.FaLink.Search)?.filters

    @Test
    fun `search url round trips through FaLinks parser`() {
        val filters = SearchFilters(
            orderBy = "popularity",
            orderDirection = "asc",
            category = 2,
            artType = 100,
            species = 6075,
            range = "30days",
            mode = "extended",
            perpage = 24
        )
        val back = parseFilters(filters.toFaSearchUrl("fox"))
        assertNotNull("应能解析回筛选状态", back)
        assertEquals("orderBy", "popularity", back!!.orderBy)
        assertEquals("orderDirection", "asc", back.orderDirection)
        assertEquals("category", 2, back.category)
        assertEquals("artType", 100, back.artType)
        assertEquals("species", 6075, back.species)
        assertEquals("range", "30days", back.range)
    }

    @Test
    fun `parser rejects invalid order-by and falls back to default`() {
        val back = parseFilters(
            "https://www.furaffinity.net/search/?q=x&order-by=bogus&order-direction=sideways"
        )
        assertNotNull(back)
        // 非法枚举不得污染状态:回落到默认 date/desc
        assertEquals("date", back!!.orderBy)
        assertEquals("desc", back.orderDirection)
    }
}
