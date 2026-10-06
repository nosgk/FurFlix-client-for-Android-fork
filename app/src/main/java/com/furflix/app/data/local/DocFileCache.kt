package com.furflix.app.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * 文档类资源（PDF/docx/rtf/odt/txt 等原件）的持久字节缓存：cacheDir/docs 下
 * 按 URL 哈希落盘。缓存优先——重开同一作品零下载直接出文，与图片的
 * Coil 磁盘缓存同级；由 [CacheJanitor] 按 TTL 与总量上限修剪（LRU）。
 */
object DocFileCache {
    private const val DIR_NAME = "docs"

    /** 文档缓存总量上限（字节） */
    private const val MAX_TOTAL_BYTES = 256L * 1024 * 1024

    /** 条目 TTL：30 天未触碰即视为可清理 */
    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

    fun cacheDir(context: android.content.Context): File =
        File(context.cacheDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    fun fileFor(context: android.content.Context, url: String, ext: String): File {
        val safeExt = ext.lowercase().filter { it.isLetterOrDigit() }.take(8).ifBlank { "bin" }
        return File(cacheDir(context), sha1(url) + "." + safeExt)
    }

    /**
     * 缓存优先取件：命中直接返回（并刷新 LRU 触点），未命中走 [fetch] 下载
     * 后落盘。[fetch] 返回 null（超限/网络失败）时不留半截文件。
     */
    suspend fun obtain(
        context: android.content.Context,
        url: String,
        ext: String,
        fetch: suspend () -> ByteArray?
    ): File? {
        val file = fileFor(context, url, ext)
        if (file.isFile && file.length() > 0) {
            file.setLastModified(System.currentTimeMillis())
            return file
        }
        val bytes = fetch() ?: return null
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            try {
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(file)) {
                    file.writeBytes(bytes)
                    tmp.delete()
                }
            } catch (e: Exception) {
                tmp.delete()
                throw e
            }
        }
        return file
    }

    /** 配额/TTL 修剪：过期先删，超总量从最旧开始淘汰（被 [CacheJanitor] 调用）。 */
    fun trim(context: android.content.Context) {
        val dir = cacheDir(context)
        val now = System.currentTimeMillis()
        val live = dir.listFiles()?.filter { it.isFile } ?: return
        var total = 0L
        val fresh = mutableListOf<File>()
        for (f in live) {
            if (now - f.lastModified() > MAX_AGE_MS) {
                f.delete()
            } else {
                fresh.add(f)
                total += f.length()
            }
        }
        for (f in fresh.sortedBy { it.lastModified() }) {
            if (total <= MAX_TOTAL_BYTES) break
            val len = f.length()
            if (f.delete()) total -= len
        }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
