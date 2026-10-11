package com.furflix.app.data.model

import com.furflix.app.data.FA_ART_TYPES
import com.furflix.app.data.FA_CATEGORIES
import com.furflix.app.data.FA_SPECIES
import com.furflix.app.data.FA_SPECIES_OPTIONS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 筛选入参合法性契约：任何来源（手改链接、旧版本存档、面板越界组合）的取值
 * 都必须被 [SearchFilters.normalized] 收敛到站点词表内的合法集合。这些断言
 * 直接对应"非法值进请求 → 站点静默按默认筛选"这一类难排查故障。
 */
class SearchFiltersNormalizationTest {

    /** 取一个必定不在词表里的 id（词表最大值之外），避免写死数字将来失效。 */
    private fun bogusId(ids: List<Int>): Int = (ids.maxOrNull() ?: 0) + 1000

    @Test
    fun `unknown enum ids fall back to no-filter`() {
        val n = SearchFilters(
            category = bogusId(FA_CATEGORIES.map { it.first }),
            artType = bogusId(FA_ART_TYPES.map { it.first }),
            species = bogusId(FA_SPECIES.map { it.first })
        ).normalized()
        assertEquals(0, n.category)
        assertEquals(0, n.artType)
        assertEquals(0, n.species)
    }

    @Test
    fun `legal enum ids survive`() {
        val category = FA_CATEGORIES.first { it.first != 0 }.first
        val artType = FA_ART_TYPES.first { it.first != 0 }.first
        val n = SearchFilters(category = category, artType = artType).normalized()
        assertEquals(category, n.category)
        assertEquals(artType, n.artType)
    }

    /**
     * 物种 id 必须认两张表的并集：全量表（搜索面板用）与精简选项表（行内下拉用）。
     * 只认一张就会把另一张产出的合法 id 判成非法——共享层改动的典型多米诺位点。
     */
    @Test
    fun `species accepts ids from both site tables`() {
        val fromFullTable = FA_SPECIES.first { it.first != 0 }.first
        val fromOptionTable = FA_SPECIES_OPTIONS.first { it.first != 0 }.first
        assertEquals(fromFullTable, SearchFilters(species = fromFullTable).normalized().species)
        assertEquals(fromOptionTable, SearchFilters(species = fromOptionTable).normalized().species)
    }

    @Test
    fun `perpage snaps to the nearest site tier`() {
        assertEquals(24, SearchFilters(perpage = 30).normalized().perpage)
        assertEquals(48, SearchFilters(perpage = 40).normalized().perpage)
        assertEquals(72, SearchFilters(perpage = 999).normalized().perpage)
        assertEquals(24, SearchFilters(perpage = -5).normalized().perpage)
        assertEquals(48, SearchFilters(perpage = 48).normalized().perpage)
    }

    @Test
    fun `sort mode and range are whitelisted`() {
        val bad = SearchFilters(
            orderBy = "cheapest",
            orderDirection = "sideways",
            mode = "fuzzy",
            range = "2weeks"
        ).normalized()
        assertEquals("date", bad.orderBy)
        assertEquals("desc", bad.orderDirection)
        assertEquals("extended", bad.mode)
        assertEquals("all", bad.range)

        // 大小写不敏感：站点枚举是小写，用户手打的大小写不该变成非法值
        val cased = SearchFilters(
            orderBy = "RELEVANCY",
            orderDirection = "ASC",
            mode = "ALL",
            range = "7DAYS"
        ).normalized()
        assertEquals("relevancy", cased.orderBy)
        assertEquals("asc", cased.orderDirection)
        assertEquals("all", cased.mode)
        assertEquals("7days", cased.range)
    }

    /** 旧版本存过 1week / 1month 这类非站点枚举，收敛时要映射到原生值。 */
    @Test
    fun `legacy range values migrate to native enums`() {
        assertEquals("7days", SearchFilters(range = "1week").normalized().range)
        assertEquals("30days", SearchFilters(range = "1month").normalized().range)
    }

    @Test
    fun `dates only survive on a well-formed manual range`() {
        val manual = SearchFilters(
            range = "manual",
            rangeFrom = "2026-01-01",
            rangeTo = "2026-06-30"
        ).normalized()
        assertEquals("manual", manual.range)
        assertEquals("2026-01-01", manual.rangeFrom)
        assertEquals("2026-06-30", manual.rangeTo)

        // 非 manual：日期一并清掉，避免把过期日期带上请求
        val nonManual = SearchFilters(range = "7days", rangeFrom = "2026-01-01", rangeTo = "2026-06-30")
            .normalized()
        assertEquals("", nonManual.rangeFrom)
        assertEquals("", nonManual.rangeTo)

        // manual 但日期格式非法：只丢非法的那一端
        val malformed = SearchFilters(range = "manual", rangeFrom = "01/01/2026", rangeTo = "2026-06-30")
            .normalized()
        assertEquals("", malformed.rangeFrom)
        assertEquals("2026-06-30", malformed.rangeTo)
    }

    @Test
    fun `genders are filtered by the site vocabulary and deduped`() {
        val n = SearchFilters(genders = listOf("male", "alien", "male", "non_binary")).normalized()
        assertEquals(listOf("male", "non_binary"), n.genders)
    }

    @Test
    fun `keywords are trimmed blank-dropped and deduped`() {
        val n = SearchFilters(keywords = listOf(" fox ", "", "  ", "fox", "wolf")).normalized()
        assertEquals(listOf("fox", "wolf"), n.keywords)
    }

    /** 幂等：收敛过的值再收敛一次必须一模一样，否则会在读→写循环里来回漂移。 */
    @Test
    fun `normalization is idempotent`() {
        val dirty = SearchFilters(
            orderBy = "RELEVANCY",
            range = "1month",
            rangeFrom = "2026-01-01",
            category = 9999,
            species = 123456,
            perpage = 999,
            genders = listOf("male", "alien"),
            keywords = listOf(" fox ", "fox", "")
        )
        val once = dirty.normalized()
        assertEquals(once, once.normalized())
    }

    /** 收敛结果本身必须是可安全投递的：每个枚举都在站点白名单内。 */
    @Test
    fun `normalized output is always a legal site input`() {
        val n = SearchFilters(
            orderBy = "junk", orderDirection = "junk", mode = "junk", range = "junk",
            category = bogusId(FA_CATEGORIES.map { it.first }),
            artType = bogusId(FA_ART_TYPES.map { it.first }),
            species = bogusId(FA_SPECIES.map { it.first }),
            perpage = 1000
        ).normalized()
        assertTrue(n.orderBy in setOf("date", "relevancy", "popularity"))
        assertTrue(n.orderDirection in setOf("asc", "desc"))
        assertTrue(n.mode in setOf("all", "any", "extended"))
        assertTrue(n.range in setOf("all", "manual", "1day", "3days", "7days", "30days", "90days", "1year", "3years", "5years"))
        assertTrue(n.perpage in SearchFilters.PERPAGE_CHOICES)
        assertEquals(0, n.category)
        assertEquals(0, n.artType)
        assertEquals(0, n.species)
    }
}
