package com.furflix.app.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SrtParserTest {

    @Test
    fun `parses MM SS comma timestamps without hours`() {
        val track = SrtParser.parse("1\n00:00,000 --> 00:02,908\nhello")
        assertEquals(1, track.cues.size)
        assertEquals(0L, track.cues[0].startMs)
        assertEquals(2908L, track.cues[0].endMs)
        assertEquals("hello", track.cues[0].text)
    }

    @Test
    fun `parses standard HH MM SS comma timestamps`() {
        val track = SrtParser.parse("1\n01:02:03,500 --> 01:02:05,000\nhi")
        assertEquals(3723500L, track.cues[0].startMs)
        assertEquals(3725000L, track.cues[0].endMs)
    }

    @Test
    fun `accepts dot as millisecond separator`() {
        val track = SrtParser.parse("1\n00:00.500 --> 00:01.000\nhi")
        assertEquals(500L, track.cues[0].startMs)
        assertEquals(1000L, track.cues[0].endMs)
    }

    @Test
    fun `extracts srt from fenced code block`() {
        val raw = "Sure!\n```srt\n1\n00:00,000 --> 00:02,908\n[smile] \"hey\" **BURP**\n```\nDone."
        val track = SrtParser.parse(raw)
        assertEquals(1, track.cues.size)
        assertEquals("[smile] \"hey\" **BURP**", track.cues[0].text)
    }

    @Test
    fun `keeps multi line cue text`() {
        val track = SrtParser.parse("1\n00:00,000 --> 00:02,000\nline one\nline two")
        assertEquals("line one\nline two", track.cues[0].text)
    }

    @Test
    fun `splits consecutive cues that omit the blank separator`() {
        val raw = "1\n00:00,000 --> 00:01,000\nfirst\n2\n00:01,000 --> 00:02,000\nsecond"
        val track = SrtParser.parse(raw)
        assertEquals(2, track.cues.size)
        assertEquals("first", track.cues[0].text)
        assertEquals("second", track.cues[1].text)
    }

    @Test
    fun `clamps inverted timeline to zero length instead of dropping the cue`() {
        val track = SrtParser.parse("1\n00:05,000 --> 00:01,000\nweird")
        assertEquals(1, track.cues.size)
        assertEquals(5000L, track.cues[0].startMs)
        assertEquals(5000L, track.cues[0].endMs)
    }

    @Test
    fun `drops entries whose timestamps cannot be parsed`() {
        assertTrue(SrtParser.parse("1\n00:00,000 --> abc\nnope").isEmpty)
    }

    @Test
    fun `sorts cues by start time`() {
        val track = SrtParser.parse("1\n00:10,000 --> 00:11,000\nlate\n2\n00:01,000 --> 00:02,000\nearly")
        assertEquals("early", track.cues[0].text)
        assertEquals("late", track.cues[1].text)
    }

    @Test
    fun `cueAt finds the active cue and returns null inside gaps`() {
        val track = SrtParser.parse("1\n00:00,000 --> 00:01,000\nA\n2\n00:02,000 --> 00:03,000\nB")
        assertEquals("A", track.cueAt(500)?.text)
        assertEquals("B", track.cueAt(2500)?.text)
        assertNull(track.cueAt(1500))
    }

    @Test
    fun `empty input yields an empty track`() {
        assertTrue(SrtParser.parse("").isEmpty)
    }
}
