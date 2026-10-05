package com.furflix.app.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchFiltersCodecTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `round trip preserves fields`() {
        val filters = SearchFilters(
            orderBy = "relevancy",
            orderDirection = "asc",
            range = "1month",
            keywords = listOf("cute", "!feral"),
            ratingAdult = false
        )
        val encoded = json.encodeToString(SearchFilters.serializer(), filters)
        assertEquals(filters, json.decodeFromString(SearchFilters.serializer(), encoded))
    }

    @Test
    fun `unknown fields are ignored for forward compatibility`() {
        val decoded = json.decodeFromString(SearchFilters.serializer(), """{"orderBy":"date","brandNewField":1}""")
        assertEquals(SearchFilters(), decoded)
    }

    @Test
    fun `defaults apply when fields are absent`() {
        assertEquals(SearchFilters(), json.decodeFromString(SearchFilters.serializer(), "{}"))
    }
}
