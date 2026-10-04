package com.furflix.app.translate.ui

import com.furflix.app.FurFlixApp
import com.furflix.app.translate.data.TranslationCategory
import com.furflix.app.translate.data.TranslationConfig
import com.furflix.app.translate.data.TranslationErrors
import com.furflix.app.translate.data.TranslationException
import com.furflix.app.translate.data.TranslationProvider
import com.furflix.app.translate.data.TranslationService
import com.furflix.app.translate.data.TranslationSettings
import com.furflix.app.translate.data.translationLooksChinese
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 单段文本的翻译状态机（蓝本 TranslationEntry 的 StateFlow 版）。 */
sealed interface TranslationUiState {
    /** autoAttempted：自动翻译已尝试过（含中文跳过），避免重复自动触发。 */
    data class Idle(val autoAttempted: Boolean = false) : TranslationUiState
    data object Loading : TranslationUiState
    data class Success(
        val translated: String,
        val providerLabel: String,
        /** true = 显示译文；false = 显示原文（按钮/署名行切换）。 */
        val showing: Boolean,
    ) : TranslationUiState

    data class Failed(
        val message: String,
        val code: Int?,
        val detail: String?,
    ) : TranslationUiState
}

/**
 * 挂在一段原文上的翻译状态。请求跑在 TranslationService 自有 scope 中：
 * entry 离开组合只中断状态更新与等待，在飞结果照常入缓存供其他等待者复用。
 */
class TranslationEntry(
    val text: String,
    val category: TranslationCategory,
    /** 标签等短文本：跳过分块，单条一个请求。 */
    val singleRequest: Boolean = false,
    /** 描述等 HTML 内容：htmlIn=false 的服务商会先剥标签再发送。 */
    val contentIsHtml: Boolean = false,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private val _state = MutableStateFlow<TranslationUiState>(TranslationUiState.Idle())
    val state: StateFlow<TranslationUiState> = _state.asStateFlow()

    private val service: TranslationService by lazy {
        TranslationService.getInstance(FurFlixApp.instance)
    }

    private val settings: TranslationSettings by lazy {
        TranslationSettings.getInstance(FurFlixApp.instance)
    }

    /** 手动触发（按钮点击）。Success 态下等效于切换显示/原文。 */
    fun translate() = start(auto = false)

    /** 自动触发：目标语言 zh* 且原文判中文时跳过并标记 autoAttempted。 */
    fun translateAuto() = start(auto = true)

    private fun start(auto: Boolean) {
        val current = _state.value
        if (current is TranslationUiState.Loading) return
        if (current is TranslationUiState.Success) {
            _state.value = current.copy(showing = !current.showing)
            return
        }
        if (text.isBlank()) return
        job?.cancel()
        _state.value = TranslationUiState.Loading
        job = scope.launch {
            try {
                val config = settings.config.first()
                if (auto &&
                    config.targetLanguage.startsWith("zh", ignoreCase = true) &&
                    translationLooksChinese(text)
                ) {
                    _state.value = TranslationUiState.Idle(autoAttempted = true)
                    return@launch
                }
                if (!auto) ensureKeyPresent(config)
                val translated = service.translate(text, config, singleRequest, contentIsHtml)
                _state.value = TranslationUiState.Success(
                    translated = translated,
                    providerLabel = providerLabel(config),
                    showing = true,
                )
            } catch (e: TranslationException) {
                _state.value = TranslationUiState.Failed(
                    e.message ?: TranslationErrors.NETWORK, e.code, e.detail,
                )
            } catch (_: CancellationException) {
                _state.value = TranslationUiState.Idle(autoAttempted = true)
            } catch (e: Exception) {
                _state.value = TranslationUiState.Failed(
                    TranslationErrors.NETWORK, null, e.message?.take(200),
                )
            }
        }
    }

    private fun ensureKeyPresent(config: TranslationConfig) {
        val missing = when (config.provider) {
            TranslationProvider.OPENAI -> config.apiKey.isBlank()
            TranslationProvider.AZURE -> config.azureKey.isBlank()
            else -> false
        }
        if (missing) throw TranslationException(TranslationErrors.NO_KEY)
    }

    // 署名：OpenAI 用模型名，其余用服务商短名（蓝本 §6.1）
    private fun providerLabel(config: TranslationConfig): String =
        if (config.provider == TranslationProvider.OPENAI) config.model
        else config.provider.shortLabel

    fun cancel() {
        job?.cancel()
        scope.cancel()
    }
}
