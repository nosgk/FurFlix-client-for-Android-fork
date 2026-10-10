package com.furflix.app.translate.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.furflix.app.FurFlixApp
import com.furflix.app.R
import com.furflix.app.translate.data.TranslationCategory
import com.furflix.app.translate.data.TranslationDisplayMode
import com.furflix.app.translate.data.TranslationErrors
import com.furflix.app.translate.data.TranslationSettings
import com.furflix.app.translate.data.TranslationUiPrefs
import com.furflix.app.ui.theme.MutedText

/**
 * 页面级标签翻译开关（蓝本 TagTranslationScope 的 CompositionLocal 版）。
 * 提供处完全接管该页开关；未提供时全局 translateTagsAuto 生效。
 * State 初始值来自全局设置，用户翻动只影响本页。
 */
val LocalTagTranslationEnabled = staticCompositionLocalOf<MutableState<Boolean>?> { null }

/** 挂点统一入口：收集翻译偏好快照。 */
@Composable
fun rememberTranslationPrefs(): TranslationUiPrefs {
    val settings = remember { TranslationSettings.getInstance(FurFlixApp.instance) }
    return settings.uiPrefs.collectAsState(initial = TranslationUiPrefs.DEFAULT).value
}

/** 组合内创建 entry，并在离开组合时取消状态更新（在飞请求照常入缓存）。 */
@Composable
fun rememberTranslationEntry(
    key: Any?,
    text: String,
    category: TranslationCategory,
    singleRequest: Boolean = false,
    contentIsHtml: Boolean = false,
): TranslationEntry {
    val entry = remember(key, text, category, singleRequest, contentIsHtml) {
        TranslationEntry(text, category, singleRequest, contentIsHtml)
    }
    DisposableEffect(entry) {
        onDispose { entry.cancel() }
    }
    return entry
}

/** 自动翻译效果：该类别的自动开关打开且未尝试过时触发一次（中文原文会被跳过）。 */
@Composable
fun TranslatableAutoEffect(entry: TranslationEntry, prefs: TranslationUiPrefs) {
    val autoForCategory = prefs.autoFor(entry.category)
    LaunchedEffect(prefs.enabled, autoForCategory) {
        if (prefs.enabled && autoForCategory) {
            val s = entry.state.value
            if (s is TranslationUiState.Idle && !s.autoAttempted) entry.translateAuto()
        }
    }
}

/** 紧凑翻译图标按钮：loading 转小圈、error 变重试图标；Success 态点击切换显示。 */
@Composable
fun TranslationIconButton(
    entry: TranslationEntry,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    iconSize: Int = 16,
) {
    if (!enabled) return // 功能关闭：整颗隐藏（蓝本同）
    val state by entry.state.collectAsStateWithLifecycle()
    Box(
        modifier = modifier
            .size((iconSize + 16).dp)
            .clip(CircleShape)
            .clickable { entry.translate() },
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is TranslationUiState.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(iconSize.dp),
                strokeWidth = 1.5.dp,
                color = tint,
            )
            is TranslationUiState.Failed -> Icon(
                Icons.Default.Refresh,
                contentDescription = stringResource(R.string.translation_retry),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(iconSize.dp),
            )
            else -> Icon(
                Icons.Default.Translate,
                contentDescription = stringResource(R.string.settings_translation),
                tint = tint,
                modifier = Modifier.size(iconSize.dp),
            )
        }
    }
}

/**
 * 纯文本类别的译文展示（评论/简介）：双语 = 译文在原文下方；仅译文 = 原地替换。
 * 失败显示本地化消息 + HTTP 摘录，原文保持可见。
 */
@Composable
fun TranslatableText(
    entry: TranslationEntry,
    prefs: TranslationUiPrefs,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    val state by entry.state.collectAsStateWithLifecycle()
    val mode = prefs.displayMode(entry.category)
    Column(modifier.animateContentSize()) {
        when (val s = state) {
            is TranslationUiState.Success -> {
                if (mode == TranslationDisplayMode.BILINGUAL || !s.showing) {
                    Text(
                        text = entry.text,
                        style = style,
                        maxLines = maxLines,
                        overflow = overflow,
                        onTextLayout = onTextLayout,
                    )
                }
                if (s.showing) {
                    if (mode == TranslationDisplayMode.BILINGUAL) {
                        Spacer(Modifier.height(6.dp))
                    }
                    // K-007:译文走 Compose Text,不经 HtmlCompat,上游转义的
                    // 实体(&lt; 3 等)会原样暴露给用户。展示前统一解码。
                    val decoded = remember(s.translated) {
                        com.furflix.app.translate.data.HtmlEntities.decode(s.translated)
                    }
                    Text(text = decoded, style = style)
                    TranslationCaption(entry, s, mode)
                }
            }
            is TranslationUiState.Failed -> {
                Text(
                    text = entry.text,
                    style = style,
                    maxLines = maxLines,
                    overflow = overflow,
                    onTextLayout = onTextLayout,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = translationErrorText(s),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            else -> Text(
                text = entry.text,
                style = style,
                maxLines = maxLines,
                overflow = overflow,
                onTextLayout = onTextLayout,
            )
        }
    }
}

/** "Translated by …" 署名 + 显示切换链接。 */
@Composable
fun TranslationCaption(
    entry: TranslationEntry,
    success: TranslationUiState.Success,
    mode: TranslationDisplayMode,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 4.dp)
            .clickable { entry.translate() }, // Success 态：切换显示
    ) {
        Text(
            text = stringResource(R.string.translated_by, success.providerLabel),
            fontSize = 10.sp,
            fontStyle = FontStyle.Italic,
            color = MutedText,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = when {
                !success.showing -> stringResource(R.string.translation_show_translation)
                mode == TranslationDisplayMode.TRANSLATION_ONLY ->
                    stringResource(R.string.translation_show_original)
                else -> stringResource(R.string.translation_collapse)
            },
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 本地化错误文案（不含 HTTP 摘录行；标签 chip 内用）。 */
@Composable
fun translationErrorBase(message: String, code: Int?): String = when (message) {
    TranslationErrors.TIMEOUT -> stringResource(R.string.translation_error_timeout)
    TranslationErrors.CONNECTION_FAILED -> stringResource(R.string.translation_error_connection)
    TranslationErrors.INVALID_KEY -> stringResource(R.string.translation_error_invalid_key)
    TranslationErrors.RATE_LIMITED -> stringResource(R.string.translation_error_rate_limited)
    TranslationErrors.NETWORK -> stringResource(R.string.translation_error_network)
    TranslationErrors.EMPTY -> stringResource(R.string.translation_error_empty)
    TranslationErrors.BAD_FORMAT -> stringResource(R.string.translation_error_bad_response)
    TranslationErrors.NO_KEY -> stringResource(R.string.translation_error_no_key)
    else -> if (message.startsWith("server error") && code != null) {
        stringResource(R.string.translation_error_server, code)
    } else {
        stringResource(R.string.translation_error_bad_response)
    }
}

/** 完整错误展示：本地化消息 + "HTTP %d · 服务器响应摘录"（排障用）。 */
@Composable
fun translationErrorText(failure: TranslationUiState.Failed): String {
    val base = translationErrorBase(failure.message, failure.code)
    val detail = if (failure.code != null && !failure.detail.isNullOrBlank()) {
        "\n" + stringResource(R.string.translation_error_http_detail, failure.code, failure.detail.take(120))
    } else ""
    return base + detail
}
