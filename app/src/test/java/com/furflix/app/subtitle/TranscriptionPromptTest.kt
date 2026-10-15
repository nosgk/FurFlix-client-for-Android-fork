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

    @Test
    fun `render also accepts full width braces`() {
        // 中文输入法下极易打出全角花括号，只认半角会把变量原样漏给模型
        assertEquals(
            "T/d",
            TranscriptionPrompt.render("｛@作品标题｝/｛@作品描述｝", "", "T", "d")
        )
    }

    @Test
    fun `plainDescription strips tags entities and collapses whitespace`() {
        val html = "<p>Hello &amp; welcome</p>\n\n<div>line <b>two</b></div>"
        assertEquals("Hello & welcome line two", TranscriptionPrompt.plainDescription(html))
    }

    @Test
    fun `plainDescription passes through plain text`() {
        assertEquals("just words", TranscriptionPrompt.plainDescription("just words"))
    }
}
