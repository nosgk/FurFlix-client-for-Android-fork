package com.furflix.app.data.local

import com.furflix.app.data.model.CardStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardStatsCacheTest {

    @Test
    fun `upsert inserts and caps at the limit, dropping the oldest`() {
        var map = emptyMap<String, CardStats>()
        for (i in 0 until 10) {
            map = CardStatsCache.upsert(map, "id$i", CardStats(views = i), limit = 4)
        }
        assertEquals(4, map.size)
        assertTrue(map.keys.containsAll(listOf("id6", "id7", "id8", "id9")))
    }

    @Test
    fun `re-putting a key moves it to the newest end`() {
        var map = mapOf("a" to CardStats(), "b" to CardStats(), "c" to CardStats())
        map = CardStatsCache.upsert(map, "a", CardStats(views = 5), limit = 3)
        assertEquals(listOf("b", "c", "a"), map.keys.toList())
    }

    @Test
    fun `upsert updates in place without growing`() {
        val map = CardStatsCache.upsert(mapOf("a" to CardStats(views = 1)), "a", CardStats(views = 9))
        assertEquals(1, map.size)
        assertEquals(9, map["a"]!!.views)
    }

    @Test
    fun `upsert evicts unvisited warm entries before visited history`() {
        // 统计预热全面铺开后条目流速高：未到访的预热条目先淘汰，保护浏览历史
        var map = mapOf(
            "old-visited" to CardStats(views = 1, visited = true),
            "warm-a" to CardStats(views = 2, visited = false),
            "warm-b" to CardStats(views = 3, visited = false)
        )
        map = CardStatsCache.upsert(map, "new", CardStats(views = 4, visited = true), limit = 3)
        assertEquals(listOf("old-visited", "warm-b", "new"), map.keys.toList())
    }

    @Test
    fun `upsert falls back to oldest eviction when everything is visited`() {
        var map = mapOf(
            "a" to CardStats(views = 1, visited = true),
            "b" to CardStats(views = 2, visited = true)
        )
        map = CardStatsCache.upsert(map, "c", CardStats(views = 3, visited = true), limit = 2)
        assertEquals(listOf("b", "c"), map.keys.toList())
    }

    @Test
    fun `upsertAll merges every entry and moves hits to the newest end`() {
        val map = mapOf(
            "a" to CardStats(views = 1),
            "b" to CardStats(views = 2),
            "c" to CardStats(views = 3)
        )
        val merged = CardStatsCache.upsertAll(map, mapOf("a" to CardStats(views = 9), "d" to CardStats(views = 4)))
        assertEquals(listOf("b", "c", "a", "d"), merged.keys.toList())
        assertEquals(9, merged["a"]!!.views)
    }

    @Test
    fun `upsertAll with preserveVisited keeps visited history when warm prefetch overwrites it`() {
        // 预热回填（visited=false）撞上真实到访历史：计数取新值，visited 标记必须保住，
        // 否则帖子会被静默挤出浏览历史
        val map = mapOf("a" to CardStats(views = 1, visited = true))
        val merged = CardStatsCache.upsertAll(
            map,
            mapOf("a" to CardStats(views = 99, visited = false)),
            preserveVisited = true
        )
        assertEquals(true, merged["a"]!!.visited)
        assertEquals(99, merged["a"]!!.views)
    }

    @Test
    fun `upsertAll default lets explicit visited=false overwrite history`() {
        // 移除浏览历史等显式写 visited=false 的调用方必须保持覆盖语义
        val map = mapOf("a" to CardStats(views = 1, visited = true))
        val merged = CardStatsCache.upsertAll(map, mapOf("a" to CardStats(views = 2, visited = false)))
        assertEquals(false, merged["a"]!!.visited)
    }

    @Test
    fun `upsertAll with preserveVisited keeps incoming visited flag for genuinely visited entries`() {
        val map = mapOf("a" to CardStats(views = 1, visited = false))
        val merged = CardStatsCache.upsertAll(
            map,
            mapOf("a" to CardStats(views = 2, visited = true)),
            preserveVisited = true
        )
        assertEquals(true, merged["a"]!!.visited)
    }

    @Test
    fun `upsertAll caps at the limit evicting warm entries before visited history`() {
        var map = mapOf(
            "old-visited" to CardStats(views = 1, visited = true),
            "warm-a" to CardStats(views = 2, visited = false)
        )
        map = CardStatsCache.upsertAll(
            map,
            mapOf("b" to CardStats(views = 3), "c" to CardStats(views = 4)),
            limit = 3
        )
        assertEquals(3, map.size)
        assertTrue(map.containsKey("old-visited"))
        assertTrue(map.keys.containsAll(listOf("b", "c")))
    }

    @Test
    fun `upsertAll with empty entries returns existing map unchanged`() {
        val map = mapOf("a" to CardStats(views = 1))
        assertEquals(map, CardStatsCache.upsertAll(map, emptyMap()))
    }
}
