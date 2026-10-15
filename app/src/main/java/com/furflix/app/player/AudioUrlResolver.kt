package com.furflix.app.player

import android.content.Context
import android.util.Log
import com.furflix.app.data.repository.FurRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * 音频直链按需解析：postId → 可播放直链。
 *
 * 为什么需要它：画师/文件夹列表项来自 `figure` 解析，只有 `/view/` 链接、没有
 * 原件直链（直链只在详情页的下载锚里）。开播前逐条抓详情会让"播放全部"等很久，
 * 所以队列只存 postId，轮到哪条解析哪条。
 *
 * 结果按 postId 缓存在内存里（一次会话内不重复抓）；失败返回 null 由调用方跳过。
 */
object AudioUrlResolver {

    private const val TAG = "AudioUrlResolver"

    private val cache = ConcurrentHashMap<String, String>()

    /** 同一 postId 的并发解析折叠为一次请求。 */
    private val locks = ConcurrentHashMap<String, Mutex>()

    fun cached(postId: String): String? = cache[postId]

    suspend fun resolve(context: Context, postId: String): String? {
        if (postId.isBlank()) return null
        cache[postId]?.let { return it }
        val lock = locks.getOrPut(postId) { Mutex() }
        return lock.withLock {
            cache[postId]?.let { return@withLock it }
            val url = runCatching {
                FurRepository.getInstance(context).getSubmission(postId)
                    ?.let { it.fileUrl.ifEmpty { it.fullImageUrl } }
                    ?.takeIf { it.isNotEmpty() }
            }.onFailure {
                Log.w(TAG, "resolve failed for $postId: ${it.javaClass.simpleName}: ${it.message}")
            }.getOrNull()
            if (url != null) cache[postId] = url
            url
        }
    }

    /** 预热（播放前把下一条准备好，切歌不卡）。 */
    suspend fun prefetch(context: Context, postId: String) {
        if (cache.containsKey(postId)) return
        runCatching { resolve(context, postId) }
    }
}
