package com.furflix.app.data.local

import android.content.Context
import kotlin.concurrent.thread

/**
 * 缓存碎片清理：自动清除加载中断/失败留下的无效残留与过期文档缓存。
 * 每进程启动时在后台线程跑一次；单项失败互相隔离（runCatching）。
 *
 * 覆盖面：
 *  - 旧版 PDF 内联渲染的临时整页文件（pdf_<hash>.pdf，已由 [DocFileCache]
 *    持久缓存接管，残留一律为碎片）；
 *  - cacheDir 下超过 24h 的 .tmp 半截写盘文件；
 *  - 文档持久缓存的 TTL 与总量配额修剪。
 */
object CacheJanitor {

    @Volatile
    private var ran = false

    fun cleanOnce(context: Context) {
        if (ran) return
        synchronized(this) {
            if (ran) return
            ran = true
        }
        val appContext = context.applicationContext
        thread(name = "cache-janitor") { clean(appContext) }
    }

    fun clean(context: Context) {
        runCatching {
            val cacheDir = context.cacheDir
            cacheDir.listFiles { f -> f.isFile && f.name.startsWith("pdf_") && f.name.endsWith(".pdf") }
                ?.forEach { runCatching { it.delete() } }
        }
        runCatching {
            val staleBefore = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            context.cacheDir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".tmp") && it.lastModified() < staleBefore }
                .forEach { runCatching { it.delete() } }
        }
        runCatching { DocFileCache.trim(context) }
    }
}
