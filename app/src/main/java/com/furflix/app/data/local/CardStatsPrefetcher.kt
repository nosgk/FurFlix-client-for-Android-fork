package com.furflix.app.data.local

import android.content.Context
import com.furflix.app.data.model.CardStats
import com.furflix.app.data.model.Submission
import com.furflix.app.data.repository.FurRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 全画廊共享的统计预热器：FA 列表页 HTML 不含 fav/view/comment 与文件类型，
 * 卡片浮层的三项统计与类型徽标靠静默抓详情页回填 [CardStatsCache]（visited=false，
 * 不产生浏览历史）点亮。任何画廊列表页（首页浏览/最新/搜索、用户页、标签搜索、
 * "我的"各栏）都把整页 id 投喂进来即可：
 *  - 进程级 attempted 去重：同一 id 一轮会话只抓一次，翻页/重组不重复请求；
 *  - 全局串行 + 150ms 间隔，容错 FA 速率；
 *  - onResolved 附加回调供 MainViewModel 顺带落详情缓存，避免二次抓取。
 */
object CardStatsPrefetcher {
    private const val INTERVAL_MS = 150L
    private const val MAX_ATTEMPTED = 4096

    private val mutex = Mutex()
    private val attempted = LinkedHashSet<String>()

    suspend fun prefetch(
        context: Context,
        ids: List<String>,
        onResolved: suspend (String, Submission) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        if (attempted.size > MAX_ATTEMPTED) attempted.clear()
        val fresh = ids.filter { it.isNotEmpty() && attempted.add(it) }
        if (fresh.isEmpty()) return@withContext
        val appContext = context.applicationContext
        val repository = FurRepository.getInstance(appContext)
        val cache = CardStatsCache.getInstance(appContext)
        mutex.withLock {
            for (id in fresh) {
                try {
                    val sub = repository.getSubmission(id)
                    if (sub != null) {
                        onResolved(id, sub)
                        cache.put(id, CardStats.of(sub, visited = false))
                    }
                } catch (_: Exception) {
                }
                delay(INTERVAL_MS)
            }
        }
    }
}
