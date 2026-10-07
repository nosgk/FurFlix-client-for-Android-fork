package com.furflix.app.data.remote

import com.furflix.app.data.model.SearchFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FA 链接解析回归：真实世界的复制分享形态（裸域、包裹噪声、句子内嵌链接、
 * 带 query 的 view 链接）必须全部可达——"应用内打不开 FA 链接"的根因在此。
 */
class FaLinksTest {

    private fun submission(url: String): FaLink.Submission =
        parseFaLink(url) as? FaLink.Submission ?: error("expected Submission for: $url (got ${parseFaLink(url)})")

    private fun user(url: String): FaLink.User =
        parseFaLink(url) as? FaLink.User ?: error("expected User for: $url")

    @Test
    fun `canonical https view link`() {
        assertEquals("58235654", submission("https://www.furaffinity.net/view/58235654/").id)
    }

    @Test
    fun `scheme-less bare-domain link from clipboard`() {
        assertEquals("123", submission("www.furaffinity.net/view/123").id)
        assertEquals("123", submission("furaffinity.net/view/123/").id)
    }

    @Test
    fun `full image page routes to submission`() {
        assertEquals("77", submission("https://www.furaffinity.net/full/77/").id)
    }

    @Test
    fun `view link with query key keeps numeric id`() {
        assertEquals("123", submission("https://www.furaffinity.net/view/123?key=abc").id)
    }

    @Test
    fun `trailing punctuation and zero-width noise are stripped`() {
        assertEquals("9", submission("https://www.furaffinity.net/view/9。").id)
        assertEquals("9", submission("https://www.furaffinity.net/view/9/\"").id)
        assertEquals("9", submission("https://www.furaffinity.net/view/9\u200B/").id)
    }

    @Test
    fun `link wrapped in surrounding whitespace survives`() {
        assertEquals("42", submission("  https://www.furaffinity.net/view/42/ \n").id)
    }

    @Test
    fun `embedded link inside a sentence is extracted`() {
        val link = submission("看看这个 furaffinity.net/view/9。很好看")
        assertEquals("9", link.id)
    }

    @Test
    fun `non-fa host is not a fa link`() {
        assertNull(parseFaLink("https://example.com/view/123/"))
        assertNull(parseFaLink("https://www.furaffinity.net.evil.io/view/123/"))
    }

    @Test
    fun `plain search words are not links`() {
        assertNull(parseFaLink("red fox 2024"))
        assertNull(parseFaLink("furaffinity.net"))
        assertNull(parseFaLink("furaffinity.net/forums"))
        assertNull(parseFaLink("https://www.furaffinity.net/view/abc/"))
    }

    @Test
    fun `user pages`() {
        assertEquals("foxie", user("https://www.furaffinity.net/user/foxie/").username)
        assertEquals("foxie", user("furaffinity.net/user/foxie").username)
    }

    @Test
    fun `user sub-pages fall back to user profile`() {
        assertEquals("foxie", user("https://www.furaffinity.net/gallery/foxie/").username)
        assertEquals("foxie", user("https://www.furaffinity.net/favorites/foxie/").username)
        assertEquals("foxie", user("https://www.furaffinity.net/scraps/foxie/").username)
        assertEquals("foxie", user("https://www.furaffinity.net/journals/foxie/").username)
    }

    @Test
    fun `search deep link carries query and clamped filters`() {
        val link = parseFaLink("https://www.furaffinity.net/search/?q=red+fox&order-by=date&perpage=100&rating-general=1")
            as? FaLink.Search ?: error("expected Search")
        assertEquals("red fox", link.query)
        assertEquals("date", link.filters.orderBy)
        assertEquals(SearchFilters.PERPAGE_MAX, link.filters.perpage)
        assertTrue(link.filters.ratingGeneral)
    }

    @Test
    fun `search inline-at form also parses`() {
        val link = parseFaLink("https://www.furaffinity.net/search/@q=fox&rating-general=1")
            as? FaLink.Search ?: error("expected Search")
        assertEquals("fox", link.query)
        assertTrue(link.filters.ratingGeneral)
    }

    @Test
    fun `search link with rating key present but zero means explicit uncheck`() {
        val link = parseFaLink("https://www.furaffinity.net/search/?q=fox&rating-general=0&rating-mature=0&rating-adult=0")
            as? FaLink.Search ?: error("expected Search")
        assertEquals("fox", link.query)
        assertTrue(!link.filters.ratingGeneral && !link.filters.ratingMature && !link.filters.ratingAdult)
    }

    @Test
    fun `bare search url without q is not routable`() {
        assertNull(parseFaLink("https://www.furaffinity.net/search/"))
    }
}
