package com.furflix.app.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptionPromptTest {

    @Test
    fun `default template carries all three placeholders`() {
        val template = TranscriptionPrompt.DEFAULT_TEMPLATE
        assertTrue(template.contains(TranscriptionPrompt.VAR_EXTRA_STYLE))
        assertTrue(template.contains(TranscriptionPrompt.VAR_TITLE))
        assertTrue(template.contains(TranscriptionPrompt.VAR_DESCRIPTION))
    }

    @Test
    fun `render injects every variable`() {
        val template = "s=${TranscriptionPrompt.VAR_EXTRA_STYLE} t=${TranscriptionPrompt.VAR_TITLE} d=${TranscriptionPrompt.VAR_DESCRIPTION}"
        assertEquals(
            "s=STYLE t=TITLE d=DESC",
            TranscriptionPrompt.render(template, extraStyle = "STYLE", title = "TITLE", description = "DESC")
        )
    }

    @Test
    fun `render falls back when the style prompt is blank`() {
        assertEquals(
            "（无）",
            TranscriptionPrompt.render(TranscriptionPrompt.VAR_EXTRA_STYLE, "", "t", "d")
        )
    }

    @Test
    fun `combinePresets keeps only the active ones in declaration order`() {
        val presets = listOf(
            StylePreset("a", "A", "pa"),
            StylePreset("b", "B", "pb"),
            StylePreset("c", "C", "pc")
        )
        assertEquals("pa\npc", TranscriptionPrompt.combinePresets(presets, setOf("c", "a")))
    }

    @Test
    fun `default presets ship the vore and bilingual entries`() {
        val ids = TranscriptionPrompt.DEFAULT_PRESETS.map { it.id }.toSet()
        assertTrue(TranscriptionPrompt.PRESET_ID_VORE in ids)
        assertTrue(TranscriptionPrompt.PRESET_ID_BILINGUAL in ids)
    }
}
