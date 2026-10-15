package com.furflix.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 已下载作品复用登记的判断逻辑。这两个函数决定"哪些本地文件能被当成某作品的
 * 本地副本"，判错会导致两种真实故障：漏判（已下载却仍联网重下）与误判
 * （把音频/文档当图片塞给解码器）。
 */
class DownloadedMediaRegistryTest {

    @Test
    fun `postId is recovered from the download file name`() {
        // 下载命名恒为 <安全标题>_<postId>.<ext>
        assertEquals("62284932", DownloadedMediaRegistry.postIdFromFileName("Sunset_over_the_bay_62284932.jpg"))
        assertEquals("123", DownloadedMediaRegistry.postIdFromFileName("FurFlix_123.png"))
        assertEquals("62284932", DownloadedMediaRegistry.postIdFromFileName("a_62284932"))
    }

    @Test
    fun `names without a trailing numeric id yield null`() {
        assertNull(DownloadedMediaRegistry.postIdFromFileName("no_id.jpg"))
        assertNull(DownloadedMediaRegistry.postIdFromFileName("file_abc.jpg"))
        assertNull(DownloadedMediaRegistry.postIdFromFileName("plain.jpg"))
        assertNull(DownloadedMediaRegistry.postIdFromFileName(""))
    }

    /** 标题里带下划线时，取的是**最后**一段数字——前缀标题不影响还原。 */
    @Test
    fun `underscores in the title do not confuse the id`() {
        assertEquals(
            "777",
            DownloadedMediaRegistry.postIdFromFileName("my_art_2024_final_777.jpeg")
        )
    }

    @Test
    fun `only image extensions are treated as reusable image copies`() {
        assertTrue(DownloadedMediaRegistry.isImageFileName("x_1.jpg"))
        assertTrue(DownloadedMediaRegistry.isImageFileName("x_1.JPEG"))
        assertTrue(DownloadedMediaRegistry.isImageFileName("x_1.png"))
        assertTrue(DownloadedMediaRegistry.isImageFileName("x_1.gif"))
        assertTrue(DownloadedMediaRegistry.isImageFileName("x_1.webp"))
        // 音频/文档类作品不走图片渲染路径，混进来只会让解码器拿到解不开的文件
        assertFalse(DownloadedMediaRegistry.isImageFileName("x_1.mp3"))
        assertFalse(DownloadedMediaRegistry.isImageFileName("x_1.pdf"))
        assertFalse(DownloadedMediaRegistry.isImageFileName("x_1.docx"))
        assertFalse(DownloadedMediaRegistry.isImageFileName("x_1"))
    }

    /** 未登记/已撤销的作品不应查到本地副本，空白 id 也不该被登记成键。 */
    @Test
    fun `lookup and unregister are blank-safe and unregister clears the entry`() {
        DownloadedMediaRegistry.clear()
        assertNull(DownloadedMediaRegistry.lookup(null))
        assertNull(DownloadedMediaRegistry.lookup(""))
        assertNull(DownloadedMediaRegistry.lookup("62284932"))
        // 撤销一个从未登记的 id / 空白 id 都不应抛异常
        DownloadedMediaRegistry.unregister("62284932")
        DownloadedMediaRegistry.unregister(null)
        DownloadedMediaRegistry.unregister("")
        assertNull(DownloadedMediaRegistry.lookup("62284932"))
    }
}
