package com.furflix.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FA 筛选taxonomy完整性 —— 守护 2026-10-10 补齐的分组结构。
 *
 * 背景:FA `/search/` 侧栏用 `<select>` + `<optgroup>` 呈现 Category / Type。
 * 项目原 FA_ART_TYPES 只有 19 项(缺 Specialty 大半与整个 Music 组),
 * 本批次按 FA 表单实测补到 52 项,并新增 [FA_CATEGORY_GROUPS] /
 * [FA_ART_TYPE_GROUPS] 的分组映射。
 *
 * 这些映射是"展示层按 FA 原貌分组"的唯一来源,一旦漏项/重项,
 * 面板会静默丢掉可选值 —— 故用测试锁定不变量。
 */
class FurFilterTaxonomyTest {

    private fun flatIds(groups: List<Pair<String, List<Int>>>): List<Int> =
        groups.flatMap { it.second }

    // ---------------------------------------------------------------- Category

    @Test
    fun `every non-All category id appears exactly once in groups`() {
        val tableIds = FA_CATEGORIES.map { it.first }.filter { it != 0 }
        val grouped = flatIds(FA_CATEGORY_GROUPS)

        assertEquals("分组项数应与表内非 All 项数一致", tableIds.size, grouped.size)
        assertEquals("分组内不得重复", grouped.size, grouped.toSet().size)
        assertEquals("分组应覆盖表内全部非 All 项", tableIds.toSet(), grouped.toSet())
    }

    @Test
    fun `category groups are non-empty and named`() {
        FA_CATEGORY_GROUPS.forEach { (name, ids) ->
            assertTrue("组名不得为空", name.isNotBlank())
            assertTrue("组 '$name' 不得为空", ids.isNotEmpty())
        }
    }

    // ---------------------------------------------------------------- Art Type

    @Test
    fun `every non-All art type id appears exactly once in groups`() {
        val tableIds = FA_ART_TYPES.map { it.first }.filter { it != 0 }
        val grouped = flatIds(FA_ART_TYPE_GROUPS)

        assertEquals(tableIds.size, grouped.size)
        assertEquals(grouped.size, grouped.toSet().size)
        assertEquals(tableIds.toSet(), grouped.toSet())
    }

    @Test
    fun `art type table has no duplicate ids`() {
        val ids = FA_ART_TYPES.map { it.first }
        assertEquals("id 不得重复", ids.size, ids.toSet().size)
    }

    // ---------------------------------------------------------------- 补齐校验

    @Test
    fun `art type table covers FA specialty and music groups`() {
        val ids = FA_ART_TYPES.map { it.first }.toSet()
        // Specialty 采样(FA 表单实测)
        listOf(122, 101, 105, 121, 110, 112, 114, 116, 120, 118).forEach {
            assertTrue("Specialty $it 应存在", it in ids)
        }
        // Music 组整组
        listOf(201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 200).forEach {
            assertTrue("Music $it 应存在", it in ids)
        }
    }

    @Test
    fun `art type has All sentinel at id zero`() {
        assertEquals("All", FA_ART_TYPES.first { it.first == 0 }.second)
    }

    @Test
    fun `category has All sentinel at id zero`() {
        assertEquals("All", FA_CATEGORIES.first { it.first == 0 }.second)
    }

    @Test
    fun `grouped ids all exist in their table`() {
        val catIds = FA_CATEGORIES.map { it.first }.toSet()
        flatIds(FA_CATEGORY_GROUPS).forEach { assertTrue("category $it 应在表内", it in catIds) }

        val typeIds = FA_ART_TYPES.map { it.first }.toSet()
        flatIds(FA_ART_TYPE_GROUPS).forEach { assertTrue("arttype $it 应在表内", it in typeIds) }
    }
}
