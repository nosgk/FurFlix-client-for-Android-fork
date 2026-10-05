package com.furflix.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class DynamicThumbnailTest {

    private val url = "https://t.furaffinity.net/66586891@300-1791160058.jpg"

    @Test
    fun `upgrades to the smallest tier covering the display box`() {
        assertEquals(
            "https://t.furaffinity.net/66586891@600-1791160058.jpg",
            DynamicThumbnail(url).bestThumbnailUrl(500f, 416f)
        )
    }

    @Test
    fun `picks a mid tier for a mid-sized box`() {
        assertEquals(
            "https://t.furaffinity.net/66586891@400-1791160058.jpg",
            DynamicThumbnail(url).bestThumbnailUrl(350f, 100f)
        )
    }

    @Test
    fun `keeps the tier already in the url when it covers the box`() {
        assertEquals(
            "https://t.furaffinity.net/66586891@300-1791160058.jpg",
            DynamicThumbnail(url).bestThumbnailUrl(280f, 280f)
        )
    }

    @Test
    fun `caps at the largest tier for oversized boxes`() {
        assertEquals(
            "https://t.furaffinity.net/66586891@600-1791160058.jpg",
            DynamicThumbnail(url).bestThumbnailUrl(2000f, 3000f)
        )
    }

    @Test
    fun `returns the input untouched when the tier pattern does not match`() {
        val odd = "https://a.furaffinity.net/someuser.gif"
        assertEquals(odd, DynamicThumbnail(odd).bestThumbnailUrl(500f, 500f))
    }
}
