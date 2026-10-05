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
}
