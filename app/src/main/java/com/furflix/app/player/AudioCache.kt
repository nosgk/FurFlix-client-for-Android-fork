package com.furflix.app.player

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * 音频作品的本地磁盘缓存（media3 SimpleCache）。
 *
 * 解决两件事：一是"重复进入详情页重复下载"——音频直链按 URL 建索引，首次播放
 * 边下边播并落盘，再次播放直接读本地（零网络）；二是"听了半截退出的流量白花"
 * ——media3 的缓存支持断点续传，重播从中断处续拉。
 *
 * 配额由 LRU 淘汰器自管（[MAX_BYTES]），所以不需要 [com.furflix.app.data.local.CacheJanitor]
 * 再修剪一次（它与文档缓存那种"按 TTL 手动 trim"的模型不同）。
 *
 * SimpleCache 要求同一目录在进程内只有一个实例（目录里含索引与锁文件，重复打开
 * 会抛异常），故这里做进程级单例。
 *
 * SimpleCache / StandaloneDatabaseProvider 等均标了 media3 的 `@UnstableApi`，
 * 本对象整体接受该 opt-in。
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object AudioCache {

    private const val DIR_NAME = "audio_cache"

    /** 音频缓存上限 512MB：无损单曲约 30–60MB，够装十几张专辑，超限按 LRU 淘汰。 */
    private const val MAX_BYTES = 512L * 1024 * 1024

    @Volatile
    private var instance: SimpleCache? = null

    /** 取（必要时创建）缓存实例，可跨线程调用。 */
    fun get(context: Context): SimpleCache {
        instance?.let { return it }
        synchronized(this) {
            instance?.let { return it }
            val app = context.applicationContext
            val created = SimpleCache(
                File(app.cacheDir, DIR_NAME),
                LeastRecentlyUsedCacheEvictor(MAX_BYTES),
                StandaloneDatabaseProvider(app)
            )
            instance = created
            return created
        }
    }

    /**
     * 读取已缓存的音频字节；未缓存/只有半截返回 null。
     *
     * 转录要**再读一遍整条音频**：本地已有（播放时就缓存了）就直接读盘——既不重复
     * 消耗流量，也避开对 CDN 的第二次请求（那条链路可能再次被 CF 挑战，用户侧表现
     * 就是"音频能播但转录报网络错误"）。
     */
    fun readCached(context: Context, url: String): ByteArray? = runCatching {
        val cache = get(context)
        val span = androidx.media3.datasource.cache.CacheUtil.getCached(
            cache,
            url,
            androidx.media3.datasource.DataSpec.Builder().setUri(url).build()
        ) ?: return null
        val file = span.file ?: return null
        if (span.length <= 0L) return null
        java.io.RandomAccessFile(file, "r").use { raf ->
            raf.seek(span.offset.coerceAtLeast(0L))
            val out = ByteArray(span.length.toInt())
            raf.readFully(out)
            out
        }
    }.getOrNull()

    /**
     * 清空缓存：先释放实例（否则文件被占用删不掉），再删目录，下次访问自动重建。
     * **调用前必须先停止播放**（[PlaybackController.stop]）——播放器仍持有该缓存时
     * 释放会打断读取。
     */
    fun clear(context: Context) {
        val app = context.applicationContext
        synchronized(this) {
            instance?.release()
            instance = null
        }
        runCatching { File(app.cacheDir, DIR_NAME).deleteRecursively() }
    }
}
