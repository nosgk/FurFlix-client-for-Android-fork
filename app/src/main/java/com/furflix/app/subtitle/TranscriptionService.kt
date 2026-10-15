package com.furflix.app.subtitle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import android.util.Log
import com.furflix.app.data.remote.FaMediaClient
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

/** 诊断日志 tag：出问题时 `adb logcat -s Transcription` 即可看到完整往返。 */
private const val TAG = "Transcription"

/** 转录链路错误。[kind] 供 UI 映射文案，[detail] 保留响应片段/异常信息便于排查。 */
class TranscriptionException(
    val kind: Kind,
    message: String,
    val detail: String? = null,
    cause: Throwable? = null
) : Exception(message, cause) {
    enum class Kind {
        NOT_CONFIGURED,
        INVALID_URL,
        INVALID_KEY,
        RATE_LIMITED,
        SERVER,
        NETWORK,
        BAD_RESPONSE,
        AUDIO_TOO_LARGE
    }
}

/**
 * OpenAI 兼容的音频转录客户端。
 *
 * 只做两件事：拉 `/v1/models` 供用户选模型；把音频按多模态音频输入格式塞进
 * `/v1/chat/completions` 的 user 消息（content 数组里的 `input_audio`），连同系统
 * 提示词一起发给模型，取回 SRT 正文。
 *
 * 刻意用自有 OkHttpClient（不共享 App 抓取链路）：转录请求体可达数十 MB，
 * 且需要远长于抓取的读超时。
 */
class TranscriptionService private constructor() {

    companion object {
        /**
         * 单次随请求上传的音频上限。base64 后体积约 ×1.37，20MB 原件已接近多数
         * 服务端请求体上限；超限直接给明确错误，避免白等一次失败的长请求。
         */
        const val MAX_AUDIO_BYTES = 20L * 1024 * 1024

        @Volatile
        private var instance: TranscriptionService? = null

        fun getInstance(): TranscriptionService =
            instance ?: synchronized(this) {
                instance ?: TranscriptionService().also { instance = it }
            }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        // 转录是长任务：读超时按用户配置派生（见 clientFor）
        .build()

    private val timeoutClients = java.util.concurrent.ConcurrentHashMap<Int, OkHttpClient>()

    private fun clientFor(config: TranscriptionConfig): OkHttpClient {
        val seconds = config.timeoutSeconds.coerceIn(30, 600)
        return timeoutClients.getOrPut(seconds) {
            client.newBuilder()
                .readTimeout(seconds.toLong(), TimeUnit.SECONDS)
                .build()
        }
    }

    /**
     * 拉取可用模型 id 列表（供设置页下拉选择）。
     *
     * 只要求 Base URL + API Key：模型正是要被"拉"出来的东西，若把模型也算进前置
     * 条件就会变成死锁（拉取按钮永远不可用）。
     */
    suspend fun listModels(config: TranscriptionConfig): List<String> = withContext(Dispatchers.IO) {
        requireApiAccess(config)
        val request = buildRequest(
            url = endpointFor(config.baseUrl, "/v1/models"),
            apiKey = config.apiKey,
            body = null
        )
        parseModelIds(execute(clientFor(config), request))
    }

    /**
     * 音频转录：先取音频字节（走本地缓存优先的下载），再连同系统提示词发给模型，
     * 返回模型原始回复（调用方用 [SrtParser] 抽字幕）。
     */
    suspend fun transcribe(
        config: TranscriptionConfig,
        systemPrompt: String,
        audioUrl: String
    ): String = withContext(Dispatchers.IO) {
        requireApiAccess(config)
        if (config.model.isBlank()) {
            throw TranscriptionException(
                TranscriptionException.Kind.NOT_CONFIGURED,
                "model is not selected"
            )
        }
        val http = clientFor(config)
        val endpoint = endpointFor(config.baseUrl, "/v1/chat/completions")
        Log.i(TAG, "start endpoint=$endpoint model=${config.model} apiKeyChars=${config.apiKey.length}")
        Log.i(TAG, "prompt chars=${systemPrompt.length} head=${systemPrompt.take(400).replace('\n', ' ')}")
        val (bytes, format) = fetchAudio(audioUrl)
        Log.i(TAG, "audio bytes=${bytes.size} format=$format")
        val payload = buildChatPayload(config.model, systemPrompt, bytes, format)
        Log.i(TAG, "request body chars=${payload.length}")
        val request = buildRequest(
            url = endpoint,
            apiKey = config.apiKey,
            body = payload.toRequestBody("application/json".toMediaTypeOrNull())
        )
        val raw = execute(http, request)
        Log.i(TAG, "response chars=${raw.length} head=${raw.take(400).replace('\n', ' ')}")
        parseChatContent(raw)
    }

    /** 两次调用（拉模型 / 转录）共用的最低前置条件：Base URL + API Key。 */
    private fun requireApiAccess(config: TranscriptionConfig) {
        if (config.baseUrl.isBlank() || config.apiKey.isBlank()) {
            throw TranscriptionException(
                TranscriptionException.Kind.NOT_CONFIGURED,
                "base url and api key are required"
            )
        }
        // 明文 http 是**允许**的：自建/本地服务（Ollama、LM Studio 等）常只提供
        // http，network_security_config 的 base-config 已放开明文。这里只拦
        // 无法构成合法 URL 的输入（由 buildRequest 捕获 IllegalArgumentException）。
    }

    /** 组请求：URL 非法时给 INVALID_URL 而不是让它冒泡成"网络错误"。 */
    private fun buildRequest(url: String, apiKey: String, body: okhttp3.RequestBody?): Request =
        try {
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .apply { if (body == null) get() else post(body) }
                .build()
        } catch (e: IllegalArgumentException) {
            throw TranscriptionException(
                TranscriptionException.Kind.INVALID_URL,
                e.message ?: "invalid url",
                url,
                e
            )
        }

    private fun fetchAudio(audioUrl: String): Pair<ByteArray, String> {
        // 走 FA 媒体专用客户端：d.furaffinity.net 直链对无会话请求返回 403，
        // 必须带会话 Cookie + 真实 UA + Referer。发给大模型的 /v1/* 请求走另一套
        // 客户端，不带任何站点头。
        val bytes = try {
            FaMediaClient.newCall(audioUrl).execute().use { response ->
                Log.i(TAG, "audio GET HTTP ${response.code} url=$audioUrl")
                if (!response.isSuccessful) {
                    throw TranscriptionException(
                        TranscriptionException.Kind.NETWORK,
                        "audio fetch failed",
                        "HTTP ${response.code}"
                    )
                }
                response.body.bytes()
            }
        } catch (e: IllegalArgumentException) {
            throw TranscriptionException(
                TranscriptionException.Kind.INVALID_URL,
                e.message ?: "invalid audio url",
                audioUrl,
                e
            )
        } catch (e: IOException) {
            Log.w(TAG, "audio GET failed: ${e.javaClass.simpleName}: ${e.message}", e)
            throw TranscriptionException(
                TranscriptionException.Kind.NETWORK,
                e.message ?: "audio fetch failed",
                "${e.javaClass.simpleName}: ${e.message}",
                e
            )
        }
        requireUploadable(bytes)
        return bytes to audioFormat(audioUrl)
    }

    private fun requireUploadable(bytes: ByteArray) {
        if (bytes.size > MAX_AUDIO_BYTES) {
            throw TranscriptionException(
                TranscriptionException.Kind.AUDIO_TOO_LARGE,
                "audio exceeds upload limit",
                "${bytes.size / (1024 * 1024)}MB > ${MAX_AUDIO_BYTES / (1024 * 1024)}MB"
            )
        }
    }

    private fun execute(http: OkHttpClient, request: Request): String {
        val response = try {
            http.newCall(request).execute()
        } catch (e: IllegalArgumentException) {
            throw TranscriptionException(
                TranscriptionException.Kind.INVALID_URL,
                e.message ?: "invalid url",
                request.url.toString(),
                e
            )
        } catch (e: IOException) {
            Log.w(TAG, "request failed: ${e.javaClass.simpleName}: ${e.message} url=${request.url}", e)
            throw TranscriptionException(
                TranscriptionException.Kind.NETWORK,
                e.message ?: "network error",
                "${e.javaClass.simpleName}: ${e.message}",
                e
            )
        }
        return response.use { resp ->
            val text = runCatching { resp.body.string() }.getOrDefault("")
            Log.i(TAG, "HTTP ${resp.code} chars=${text.length} url=${request.url}")
            if (resp.isSuccessful) {
                text
            } else {
                val detail = text.take(300).ifBlank { null }
                throw when (resp.code) {
                    401, 403 -> TranscriptionException(
                        TranscriptionException.Kind.INVALID_KEY, "invalid api key", detail
                    )
                    429 -> TranscriptionException(
                        TranscriptionException.Kind.RATE_LIMITED, "rate limited", detail
                    )
                    else -> TranscriptionException(
                        TranscriptionException.Kind.SERVER, "HTTP ${resp.code}", detail
                    )
                }
            }
        }
    }
}

// ── 纯函数（无 Android 依赖，可直接单测）────────────────────────────────────

private val pureJson = Json { ignoreUnknownKeys = true }

/** `{"data":[{"id":"whisper-1"},…]}` → ["whisper-1", …]；坏 JSON / 无 data 返回空表。 */
fun parseModelIds(raw: String): List<String> {
    val root = runCatching { pureJson.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyList()
    val data = root["data"] as? JsonArray ?: return emptyList()
    return data.mapNotNull { element ->
        (element as? JsonObject)?.get("id")?.let { (it as? JsonPrimitive)?.contentOrNull }
    }
}

/**
 * Base URL 规范化：用户常只填主机名（`api.openai.com`）或带尾斜杠。缺协议头时
 * OkHttp 的 `Request.Builder().url()` 会直接抛 IllegalArgumentException——此前它
 * 冒泡到 UI 被笼统报成"网络错误"，这是"拉取模型总是网络错误"的主因之一。
 */
fun normalizeBaseUrl(raw: String): String {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isEmpty()) return ""
    val lower = trimmed.lowercase()
    return if (lower.startsWith("http://") || lower.startsWith("https://")) trimmed else "https://$trimmed"
}

/**
 * 该地址是否为 https。**仅供参考/展示**，不再作为发起请求的门槛——明文 http 允许
 * （本地大模型服务常只有 http，见 [requireApiAccess]）。
 */
fun isSecureBaseUrl(raw: String): Boolean =
    normalizeBaseUrl(raw).startsWith("https://", ignoreCase = true)

/**
 * 端点拼接：`https://host`、`https://host/`、`https://host/v1` 都收敛到同一结果，
 * 不会拼出 `/v1/v1/models`。
 */
fun endpointFor(baseUrl: String, path: String): String {
    val base = normalizeBaseUrl(baseUrl)
    if (base.isEmpty()) return path
    val suffix = if (base.endsWith("/v1") && path.startsWith("/v1/")) path.removePrefix("/v1") else path
    return base + suffix
}

/** 从音频直链推断 OpenAI `input_audio.format`；未知扩展名回落 mp3。 */
fun audioFormat(url: String): String {
    val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase()
    return when (ext) {
        "mp3", "m4a", "wav", "flac", "ogg", "opus", "aac", "webm", "mp4", "mpeg", "mpga" -> ext
        else -> "mp3"
    }
}

/**
 * 构造 `/v1/chat/completions` 请求体：system 消息放长提示词，user 消息的 content
 * 数组同时带一段指令文本与 `input_audio`（base64 音频）。温度固定 0，转录要的是
 * 可复现而非创造性。
 */
fun buildChatPayload(model: String, systemPrompt: String, audio: ByteArray, format: String): String {
    val base64 = Base64.getEncoder().encodeToString(audio)
    val root = buildJsonObject {
        put("model", model)
        put("temperature", 0)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "system")
                put("content", systemPrompt)
            }
            addJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "text")
                        put(
                            "text",
                            "Transcribe this audio and return timestamped SRT subtitles exactly as instructed."
                        )
                    }
                    addJsonObject {
                        put("type", "input_audio")
                        putJsonObject("input_audio") {
                            put("data", base64)
                            put("format", format)
                        }
                    }
                }
            }
        }
    }
    return root.toString()
}

/** 取 `choices[0].message.content`；结构不符 / 内容为空抛 BAD_RESPONSE。 */
fun parseChatContent(raw: String): String {
    val root = runCatching { pureJson.parseToJsonElement(raw).jsonObject }.getOrNull()
        ?: throw TranscriptionException(
            TranscriptionException.Kind.BAD_RESPONSE, "malformed response", raw.take(200)
        )
    val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
    val message = choice?.get("message") as? JsonObject
    val content = (message?.get("content") as? JsonPrimitive)?.contentOrNull
    if (content.isNullOrBlank()) {
        throw TranscriptionException(
            TranscriptionException.Kind.BAD_RESPONSE, "empty transcription", raw.take(200)
        )
    }
    return content
}
