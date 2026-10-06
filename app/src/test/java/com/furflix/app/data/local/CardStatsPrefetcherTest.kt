package com.furflix.app.data.local

import com.furflix.app.data.model.CardStats
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 预热必要性判定（TTL/visited 语义）的纯逻辑单测。
 * 语义基准：visited 历史权威且新鲜；旧版无时间戳条目（fetchedAt=0）升级后
 * 补抓一轮；未到访条目按 PREFETCH_TTL_MS 判新鲜。
 */
class CardStatsPrefetcherTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `missing entry always needs prefetch`() {
        assertTrue(CardStatsPrefetcher.needsPrefetch(null, now))
    }

    @Test
    fun `visited history never gets re-prefetched`() {
        val stale = CardStats(views = 1, visited = true, fetchedAt = now - 10 * 60 * 60 * 1000)
        assertFalse(CardStatsPrefetcher.needsPrefetch(stale, now))
    }

    @Test
    fun `legacy entry without timestamp is refreshed once`() {
        val legacy = CardStats(views = 1, visited = false, fetchedAt = 0L)
        assertTrue(CardStatsPrefetcher.needsPrefetch(legacy, now))
    }

    @Test
    fun `fresh warm entry within ttl is skipped`() {
        val fresh = CardStats(views = 1, visited = false, fetchedAt = now - 60 * 1000)
        assertFalse(CardStatsPrefetcher.needsPrefetch(fresh, now))
    }

    @Test
    fun `warm entry beyond ttl is refreshed`() {
        val stale = CardStats(views = 1, visited = false, fetchedAt = now - CardStatsPrefetcher.PREFETCH_TTL_MS - 1)
        assertTrue(CardStatsPrefetcher.needsPrefetch(stale, now))
    }
}
