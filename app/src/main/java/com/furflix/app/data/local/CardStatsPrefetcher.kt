package com.furflix.app.data.local

import android.content.Context
import com.furflix.app.data.model.CardStats
import com.furflix.app.data.model.Submission
import com.furflix.app.data.repository.FurRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 全画廊共享的统计预热器：FA 列表页 HTML 不含 fav/view/comment 与文件类型，
 * 卡片浮层的三项统计与类型徽标靠静默抓详情页回填 [CardStatsCache]（visited=false，
 * 不产生浏览历史）点亮。任何画廊列表页（首页浏览/最新/搜索、用户页、标签搜索、
 * "我的"各栏）都把整页 id 投喂进来即可。
 *
 * 吞吐设计（旧版全局串行 + 150ms 间隔仅 ~1.4 req/s，整页 72 项要 ~47s，
 * 翻页后新条目排在积压队列尾部即用户感知的"后续项严重迟缓"）：
 *  - 进程级 attempted 去重：同一 id 一轮会话只抓一次，翻页/重组不重复请求；
 *  - 缓存命中跳过：visited 历史条目视为权威数据永不重抓；未到访条目在
 *    [PREFETCH_TTL_MS] 内直接跳过——冷启动不再整站重抓，队列只装真正缺数据的 id；
 *  - 有界并发：[CONCURRENCY] 个工作协程共享全局限额，单协程每抓一项歇
 *    [PACING_MS]（对 FA 的礼貌节流，429 由 RetryInterceptor 指数退避兜底），
 *    整页回填速度提升约一个量级而不放大请求尖峰；
 *  - 渐进批量回填：攒够 [FLUSH_MIN_BATCH] 条或距上次 [FLUSH_INTERVAL_MS] 即
 *    整批写入缓存并回调，DataStore 全量序列化与整表发射频率与并发数解耦。
 */
object CardStatsPrefetcher {

    /** 并发工作协程数（也是全局同时在途的详情页请求数上限） */
    private const val CONCURRENCY = 4

    /** 单工作协程两次抓取之间的间隔（礼貌节流） */
    private const val PACING_MS = 150L

    /** 攒批下限：待写条目达到该数量立即整批落库 */
    private const val FLUSH_MIN_BATCH = 8

    /** 攒批时限：距上次落库超过该时长也落一批（不满下限时保证 UI 持续点亮） */
    private const val FLUSH_INTERVAL_MS = 1200L

    /** 未到访预热条目的新鲜期：期内重投喂不再重抓（internal 供单测对照） */
    internal const val PREFETCH_TTL_MS = 6L * 60 * 60 * 1000

    private const val MAX_ATTEMPTED = 4096

    private val attempted = LinkedHashSet<String>()
    private val slots = Semaphore(CONCURRENCY)

    /**
     * 预热必要性判定（纯函数，供单测）：无缓存 → 抓；真实到访过的历史条目 →
     * 数据权威且新鲜，永不重抓；旧版无时间戳条目 → 视为过期补抓一轮；
     * 其余按 TTL。
     */
    fun needsPrefetch(existing: CardStats?, now: Long): Boolean {
        if (existing == null) return true
        if (existing.visited) return false
        if (existing.fetchedAt <= 0L) return true
        return now - existing.fetchedAt >= PREFETCH_TTL_MS
    }

    /**
     * 投喂整页 id。回填改为整批回调（[onBatchResolved] 每批一次），调用方据此
     * 把详情缓存整批并入内存 map，避免逐条触发热流发射。
     */
    suspend fun prefetch(
        context: Context,
        ids: List<String>,
        onBatchResolved: suspend (List<Pair<String, Submission>>) -> Unit = { _ -> }
    ) = withContext(Dispatchers.IO) {
        synchronized(attempted) {
            if (attempted.size > MAX_ATTEMPTED) attempted.clear()
        }
        val fresh = synchronized(attempted) { ids.filter { it.isNotEmpty() && attempted.add(it) } }
        if (fresh.isEmpty()) return@withContext

        val appContext = context.applicationContext
        val repository = FurRepository.getInstance(appContext)
        val cache = CardStatsCache.getInstance(appContext)

        val snapshot = cache.snapshot()
        val now = System.currentTimeMillis()
        val pendingIds = fresh.filter { needsPrefetch(snapshot[it], now) }
        if (pendingIds.isEmpty()) return@withContext

        // 统计预热不拉小说全文（省流量），真实到访详情时才加载
        val pending = ConcurrentHashMap<String, Submission>()
        // worker 判攒批时限 / flush 写时间戳跨协程共享：原子读写避免 long 撕裂，
        // 竞态最坏只是多触发一次 flush（flush 内部再查空），无正确性影响
        val lastFlushAt = AtomicLong(0L)
        val flushMutex = Mutex()

        suspend fun flush() {
            flushMutex.withLock {
                if (pending.isEmpty()) return
                val batchIds = pending.keys.toList()
                val batch = batchIds.mapNotNull { id -> pending[id]?.let { id to it } }
                batchIds.forEach { pending.remove(it) }
                if (batch.isEmpty()) return
                lastFlushAt.set(System.currentTimeMillis())
                try {
                    onBatchResolved(batch)
                    cache.putAll(
                        batch.associate { (id, sub) -> id to CardStats.of(sub, visited = false) },
                        preserveVisited = true
                    )
                } catch (_: Exception) {
                }
            }
        }

        coroutineScope {
            for (id in pendingIds) {
                launch {
                    slots.withPermit {
                        // 前后台联动：后台时暂停抓取（在途请求由网络层闸门托管），
                        // 回前台自动续跑——预热不产生后台隐性流量
                        com.furflix.app.data.lifecycle.AppLifecycleTracker.suspendUntilForeground()
                        try {
                            val sub = repository.getSubmission(id, loadStoryText = false)
                            if (sub != null) {
                                pending[id] = sub
                                if (pending.size >= FLUSH_MIN_BATCH ||
                                    System.currentTimeMillis() - lastFlushAt.get() >= FLUSH_INTERVAL_MS
                                ) {
                                    flush()
                                }
                            }
                        } catch (_: Exception) {
                        }
                        delay(PACING_MS)
                    }
                }
            }
        }
        flush()
    }
}
