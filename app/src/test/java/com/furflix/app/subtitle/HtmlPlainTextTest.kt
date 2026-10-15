package com.furflix.app.subtitle

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlPlainTextTest {

    @Test
    fun `strips inline tags but keeps their text`() {
        assertEquals("Hello world", HtmlPlainText.toPlainText("<p>Hello <b>world</b></p>"))
    }

    @Test
    fun `turns block tags into line breaks instead of gluing paragraphs`() {
        assertEquals("first\nsecond", HtmlPlainText.toPlainText("<p>first</p><p>second</p>"))
        assertEquals("a\nb", HtmlPlainText.toPlainText("a<br>b"))
        assertEquals("a\nb", HtmlPlainText.toPlainText("a<br/>b"))
    }

    @Test
    fun `decodes text entities`() {
        assertEquals("<3", HtmlPlainText.toPlainText("&lt;3"))
        assertEquals("Tom & Jerry", HtmlPlainText.toPlainText("Tom &amp; Jerry"))
    }

    @Test
    fun `drops anchors and their hrefs`() {
        assertEquals(
            "see the source",
            HtmlPlainText.toPlainText("""see <a href="https://example.com/x">the source</a>""")
        )
    }

    @Test
    fun `collapses runs of blank lines and trims`() {
        assertEquals("a\n\nb", HtmlPlainText.toPlainText("<div>a</div><div></div><div></div><div>b</div>"))
    }

    @Test
    fun `blank input yields empty text`() {
        assertEquals("", HtmlPlainText.toPlainText(""))
        assertEquals("", HtmlPlainText.toPlainText("   \n  "))
    }
}
