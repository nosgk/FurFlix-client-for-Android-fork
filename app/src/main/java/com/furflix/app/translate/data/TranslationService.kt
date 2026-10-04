package com.furflix.app.translate.data

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.parser.Parser
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** probe 的完整往返记录（M2 测试面板消费；translate 内部仅取 ok/parsed/错误三件）。 */
data class TranslationProbeResult(
    val ok: Boolean,
    val parsed: String? = null,
    val errorMessage: String? = null,
    val errorCode: Int? = null,
    val errorDetail: String? = null,
    val httpStatus: Int? = null,
    val elapsedMs: Long = 0,
    val method: String? = null,
    val url: String? = null,
    val requestHeaders: List<Pair<String, String>> = emptyList(),
    val requestBody: String? = null,
    val responseHeaders: List<Pair<String, String>> = emptyList(),
    val responseBody: String? = null,
)

/**
 * 通用翻译执行器（蓝本 §6）。不认识任何具体服务商：渲染 Profile 模板 → 发请求 →
 * 按解析规则取译文。缓存查询、in-flight 合并、分块、限流、重试均在此层。
 *
 * 独立 OkHttpClient（仓库第 6 个实例，刻意为之）：绝不携带 FA 会话 Cookie 与站点
 * Referer——向 Google/微软/OpenAI 泄漏站点身份是硬性禁止项；超时/重试语义亦与抓取栈解耦。
 */
class TranslationService private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: TranslationService? = null

        fun getInstance(context: Context): TranslationService =
            instance ?: synchronized(this) {
                instance ?: TranslationService(context.applicationContext).also { instance = it }
            }

        private val THINK_REGEX = Regex("<think>[\\s\\S]*?</think>")
        private val FENCE_REGEX = Regex("^```[a-zA-Z]*\\n([\\s\\S]*)\\n```$")
        private val BR_REGEX = Regex("(?i)<br\\s*/?>")
        private val BLOCK_END_REGEX = Regex("(?i)</(p|div)>")
        private val TAG_REGEX = Regex("<[^>]+>")
    }

    private val settings = TranslationSettings.getInstance(context)
    private val cache = TranslationCache.getInstance(context)

    // 请求在自有 scope 中执行：调用方（UI entry）取消不影响在飞请求，结果照常入缓存
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // 超时按用户配置派生 client（newBuilder 共享连接池/调度器，开销可忽略）
    private val timeoutClients = ConcurrentHashMap<Int, OkHttpClient>()

    private fun clientFor(timeoutSeconds: Int): OkHttpClient {
        val t = timeoutSeconds.coerceIn(5, 120)
        return timeoutClients.getOrPut(t) {
            baseClient.newBuilder()
                .connectTimeout(t.toLong(), TimeUnit.SECONDS)
                .readTimeout(t.toLong(), TimeUnit.SECONDS)
                .writeTimeout(t.toLong(), TimeUnit.SECONDS)
                .build()
        }
    }

    // ---- 全局并发槽位（maxConcurrency 服务级生效，公平队列）----

    private val slotMutex = Mutex()
    private var activeSlots = 0
    private val slotWaiters = ArrayDeque<CompletableDeferred<Unit>>()

    private suspend fun acquireSlot(max: Int) {
        val waiter = slotMutex.withLock {
            if (activeSlots < max.coerceAtLeast(1)) {
                activeSlots++
                null
            } else {
                CompletableDeferred<Unit>().also { slotWaiters.add(it) }
            }
        }
        waiter?.await() // 由 release 直接移交槽位，不再触碰 activeSlots
    }

    private suspend fun releaseSlot() {
        slotMutex.withLock {
            val next = slotWaiters.removeFirstOrNull()
            if (next != null) next.complete(Unit)
            else activeSlots--
        }
    }

    // ---- 两次请求"发起"之间的最小间隔 ----

    private val intervalMutex = Mutex()

    @Volatile
    private var lastStartAt = 0L

    private suspend fun paceInterval(intervalMs: Int) {
        if (intervalMs <= 0) return
        intervalMutex.withLock {
            val wait = lastStartAt + intervalMs - SystemClock.elapsedRealtime()
            if (wait > 0) delay(wait)
            lastStartAt = SystemClock.elapsedRealtime()
        }
    }

    // ---- in-flight 合并（key 与缓存 key 同构；批量标签翻译收益巨大）----

    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<String>>()

    suspend fun translate(
        text: String,
        config: TranslationConfig,
        singleRequest: Boolean = false,
        contentIsHtml: Boolean = false,
    ): String {
        if (text.isBlank()) throw TranslationException(TranslationErrors.NOTHING)
        val key = cacheKey(config, text)
        cache.get(key)?.let { return it }

        inFlight[key]?.let { return it.await() }

        val deferred = CompletableDeferred<String>()
        val winner = inFlight.putIfAbsent(key, deferred)
        if (winner != null) return winner.await()

        scope.launch {
            try {
                val translated = runPipeline(text, config, singleRequest, contentIsHtml)
                cache.put(key, translated, config.cacheLimit)
                deferred.complete(translated)
            } catch (e: Throwable) {
                deferred.completeExceptionally(
                    if (e is CancellationException) {
                        TranslationException(TranslationErrors.CANCELLED)
                    } else {
                        e
                    }
                )
            } finally {
                inFlight.remove(key, deferred)
            }
        }
        return deferred.await() // 调用方取消只中断等待，管道照常完成并写缓存
    }

    /** 缓存 key：provider|model|目标语言|原文（model 仅 openai 参与，防换模型串结果）。 */
    fun cacheKey(config: TranslationConfig, text: String): String = listOf(
        config.provider.id,
        if (config.provider == TranslationProvider.OPENAI) config.model else "",
        config.targetLanguage,
        text,
    ).joinToString("|")

    private suspend fun runPipeline(
        text: String,
        config: TranslationConfig,
        singleRequest: Boolean,
        contentIsHtml: Boolean,
    ): String {
        when (config.provider) {
            TranslationProvider.OPENAI -> if (config.apiKey.isBlank()) {
                throw TranslationException(TranslationErrors.NO_KEY)
            }
            TranslationProvider.AZURE -> if (config.azureKey.isBlank()) {
                throw TranslationException(TranslationErrors.NO_KEY)
            }
            else -> Unit
        }

        val profile = builtInProfile(config.provider)
        val chunks = if (singleRequest) {
            listOf(text)
        } else {
            splitText(
                text = text,
                maxChars = config.maxTextLength,
                maxParagraphs = config.maxParagraphs,
                maxEncoded = if (profile.method.equals("GET", ignoreCase = true)) MAX_ENCODED_GET else 0,
            )
        }
        val parts = ArrayList<String>(chunks.size)
        for (chunk in chunks) {
            if (chunk.isBlank()) {
                parts.add(chunk) // 空白块原样保留
                continue
            }
            // [FurFlix 增补] htmlIn=false 的服务商面对 HTML 内容先剥标签（<br>→换行 + 反转义）
            val payload = if (contentIsHtml && !profile.htmlIn) htmlToText(chunk) else chunk
            parts.add(executeChunk(payload, profile, config))
        }
        return parts.joinToString("")
    }

    private suspend fun executeChunk(
        content: String,
        profile: TranslationRequestProfile,
        config: TranslationConfig,
    ): String {
        var last: TranslationException? = null
        for (attempt in 0..config.retryCount) {
            acquireSlot(config.maxConcurrency)
            try {
                paceInterval(config.requestIntervalMs)
                val record = executeRecord(content, profile, config)
                if (record.ok) return record.parsed.orEmpty()
                val e = TranslationException(
                    record.errorMessage ?: TranslationErrors.NETWORK,
                    record.errorCode,
                    record.errorDetail,
                )
                last = e
                if (!e.isRetryable) throw e
                if (attempt < config.retryCount) delay(minOf(500L * (attempt + 1), 3000L))
            } finally {
                releaseSlot()
            }
        }
        throw last ?: TranslationException(TranslationErrors.NETWORK)
    }

    /**
     * 测试面板专用（M2 消费）：与 translate 走完全相同的执行路径，但永不重试、
     * 永不抛异常，返回完整往返记录。
     */
    suspend fun probe(
        text: String,
        config: TranslationConfig,
        contentIsHtml: Boolean = false,
    ): TranslationProbeResult {
        if (text.isBlank()) {
            return TranslationProbeResult(ok = false, errorMessage = TranslationErrors.NOTHING)
        }
        val profile = builtInProfile(config.provider)
        val payload = if (contentIsHtml && !profile.htmlIn) htmlToText(text) else text
        return executeRecord(payload, profile, config)
    }

    /** 单块执行；probe 与 translate 共用，永不重试、永不抛异常。 */
    private suspend fun executeRecord(
        content: String,
        profile: TranslationRequestProfile,
        config: TranslationConfig,
    ): TranslationProbeResult {
        val startedAt = SystemClock.elapsedRealtime()
        val vars = translationTemplateVars(config, content)
        val method = profile.method.uppercase()
        val url = try {
            buildRequestUrl(profile, vars)
        } catch (_: Exception) {
            return failRecord(startedAt, TranslationErrors.BAD_FORMAT, null, null)
        }
        val headerPairs = profile.headers.map { h ->
            renderPlainTemplate(h.getOrElse(0) { "" }, vars) to
                renderPlainTemplate(h.getOrElse(1) { "" }, vars)
        }
        val bodyText = if (method == "POST") renderJsonTemplate(profile.body, vars) else ""
        // 显式 Content-Type 头优先于 contentType 字段；实际由 requestBody 携带避免重复头
        val explicitContentType = headerPairs
            .firstOrNull { it.first.equals("Content-Type", ignoreCase = true) }?.second
        val mediaType = (explicitContentType ?: profile.contentType).toMediaTypeOrNull()

        val builder = Request.Builder().url(url)
        if (method == "POST") {
            builder.post(bodyText.toRequestBody(mediaType))
        } else {
            builder.get()
        }
        for ((k, v) in headerPairs) {
            if (k.equals("Content-Type", ignoreCase = true)) continue
            builder.header(k, v)
        }

        val requestHeaders = headerPairs
            .filter { !it.first.equals("Content-Type", ignoreCase = true) }
            .toMutableList()
        if (method == "POST" && explicitContentType == null) {
            requestHeaders.add("Content-Type" to profile.contentType)
        }

        return try {
            withContext(Dispatchers.IO) {
                clientFor(config.timeoutSeconds).newCall(builder.build()).execute().use { resp ->
                    val status = resp.code
                    val respBody = resp.body?.string().orEmpty()
                    val respHeaders = resp.headers.names().map { it to resp.header(it).orEmpty() }
                    val base = TranslationProbeResult(
                        ok = false,
                        elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                        httpStatus = status,
                        method = method,
                        url = url,
                        requestHeaders = requestHeaders,
                        requestBody = bodyText.ifEmpty { null },
                        responseHeaders = respHeaders,
                        responseBody = respBody,
                    )
                    when {
                        status == 401 || status == 403 -> base.copy(
                            ok = false,
                            errorMessage = TranslationErrors.INVALID_KEY,
                            errorCode = status,
                            errorDetail = respBody.take(200),
                        )
                        status == 429 -> base.copy(
                            ok = false,
                            errorMessage = TranslationErrors.RATE_LIMITED,
                            errorCode = status,
                            errorDetail = respBody.take(200),
                        )
                        status !in 200..299 -> base.copy(
                            ok = false,
                            errorMessage = TranslationErrors.serverError(status),
                            errorCode = status,
                            errorDetail = respBody.take(200),
                        )
                        else -> {
                            val parsed = try {
                                extractTranslation(respBody, profile)
                            } catch (e: TranslationException) {
                                return@use base.copy(
                                    ok = false,
                                    errorMessage = e.message,
                                    errorCode = status,
                                    errorDetail = e.detail,
                                )
                            }
                            base.copy(ok = true, parsed = parsed)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            failRecord(startedAt, mapIOExceptionMessage(e), null, null)
        }
    }

    private fun failRecord(startedAt: Long, message: String, code: Int?, detail: String?) =
        TranslationProbeResult(
            ok = false,
            errorMessage = message,
            errorCode = code,
            errorDetail = detail,
            elapsedMs = SystemClock.elapsedRealtime() - startedAt,
        )

    private fun mapIOExceptionMessage(e: IOException): String = when (e) {
        is SocketTimeoutException -> TranslationErrors.TIMEOUT
        is UnknownHostException, is ConnectException -> TranslationErrors.CONNECTION_FAILED
        else -> TranslationErrors.NETWORK
    }

    private fun extractTranslation(body: String, profile: TranslationRequestProfile): String {
        val path = profile.parsePath.trim()
        var out = if (path.isEmpty()) {
            body // 规则为空 = 整个响应体即译文
        } else {
            val el = try {
                Json.parseToJsonElement(body)
            } catch (_: Exception) {
                throw TranslationException(TranslationErrors.BAD_FORMAT, detail = body.take(200))
            }
            try {
                resolveJsonPath(el, path)
            } catch (_: Exception) {
                throw TranslationException(TranslationErrors.BAD_FORMAT, detail = body.take(200))
            }
        }
        if (profile.stripThink) out = stripThink(out)
        if (out.isBlank()) throw TranslationException(TranslationErrors.EMPTY)
        return out
    }

    // [FurFlix 增补] htmlIn=false 的服务商面对 HTML 内容：剥标签保换行 + 反转义
    private fun htmlToText(html: String): String {
        var s = BR_REGEX.replace(html, "\n")
        s = BLOCK_END_REGEX.replace(s, "\n")
        s = TAG_REGEX.replace(s, "")
        return Parser.unescapeEntities(s, false).trim()
    }
}
