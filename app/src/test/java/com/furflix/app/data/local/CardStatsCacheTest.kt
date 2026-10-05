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
}
