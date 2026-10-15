package com.furflix.app.subtitle

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 生成字幕所需的上下文（提示词要注入标题与描述做场景判断）。 */
data class SubtitleRequest(
    val postId: String,
    val title: String,
    val description: String,
    val audioUrl: String
)

/** 字幕面板对外快照。 */
data class SubtitleUiState(
    val postId: String? = null,
    val track: SubtitleTrack = SubtitleTrack.EMPTY,
    val generating: Boolean = false,
    val errorKind: TranscriptionException.Kind? = null,
    /** 错误细节（HTTP 状态码 / 响应片段 / 异常信息），供界面与排查直接看到。 */
    val errorDetail: String? = null
)

/**
 * 字幕会话（进程级单例，与 [com.furflix.app.player.PlaybackController] 同构）：
 * 只服务"当前这一件作品"，切换作品即换会话。
 *
 * 与播放器一致地不随界面销毁而清空——离开详情页再回来，字幕与生成结果都还在。
 */
object SubtitleController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(SubtitleUiState())
    val state: StateFlow<SubtitleUiState> = _state.asStateFlow()

    /** 切换作品：先查缓存（命中即显示），未命中则清空等用户点生成。 */
    fun select(context: Context, postId: String) {
        if (_state.value.postId == postId) return
        _state.value = SubtitleUiState(postId = postId)
        val app = context.applicationContext
        scope.launch {
            val cached = runCatching { SubtitleCache.getInstance(app).get(postId) }.getOrNull()
            if (_state.value.postId != postId) return@launch
            if (cached != null) {
                _state.value = SubtitleUiState(postId = postId, track = SrtParser.parse(cached))
            }
        }
    }

    /**
     * 生成字幕：缓存优先（同一作品不再消耗 Token），否则请求模型再解析。
     * [forceRefresh] 为真时跳过缓存读（"重新生成"用），成功后仍会覆盖写回缓存。
     */
    fun generate(context: Context, request: SubtitleRequest, forceRefresh: Boolean = false) {
        if (_state.value.generating) return
        val app = context.applicationContext
        _state.value = SubtitleUiState(postId = request.postId, generating = true)
        scope.launch {
            val next = try {
                val cache = SubtitleCache.getInstance(app)
                val cached = if (forceRefresh) {
                    null
                } else {
                    runCatching { cache.get(request.postId) }.getOrNull()
                }
                val srt = cached ?: run {
                    val settings = TranscriptionSettings.getInstance(app)
                    val config = settings.config.first()
                    val prompt = TranscriptionPrompt.render(
                        template = config.template,
                        extraStyle = settings.extraStylePrompt.first(),
                        title = request.title,
                        description = request.description
                    )
                    val content = TranscriptionService.getInstance()
                        .transcribe(config, prompt, request.audioUrl)
                    val body = SrtParser.extractFencedBlock(content)
                    runCatching { cache.put(request.postId, body) }
                    body
                }
                val track = SrtParser.parse(srt)
                SubtitleUiState(
                    postId = request.postId,
                    track = track,
                    generating = false,
                    // 解析不出任何条目等同于没生成成功（模型答非所问）
                    errorKind = if (track.isEmpty) TranscriptionException.Kind.BAD_RESPONSE else null
                )
            } catch (e: TranscriptionException) {
                SubtitleUiState(
                    postId = request.postId,
                    generating = false,
                    errorKind = e.kind,
                    errorDetail = e.detail ?: e.message
                )
            } catch (e: Exception) {
                SubtitleUiState(
                    postId = request.postId,
                    generating = false,
                    errorKind = TranscriptionException.Kind.NETWORK,
                    errorDetail = "${e.javaClass.simpleName}: ${e.message}"
                )
            }
            // 请求在飞期间用户已切到别的作品：丢弃这次结果，别覆盖新会话
            if (_state.value.postId != request.postId) return@launch
            _state.value = next
        }
    }

    /** 清空当前会话（重新生成前调用即可强制重跑）。 */
    fun clear() {
        _state.value = SubtitleUiState()
    }
}
