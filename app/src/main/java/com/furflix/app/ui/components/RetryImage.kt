package com.furflix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.network.HttpException
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.furflix.app.R
import com.furflix.app.data.remote.ImageLoadProgress
import com.furflix.app.ui.theme.FA_Accent
import kotlinx.coroutines.delay

/**
 * 图片加载重试状态：失败自动退避重试最多 [maxRetries] 次（300/600/1200ms），
 * 重试耗尽后 UI 呈现原始错误码与手动重试按钮。effectiveUrl 用 URL 片段
 * 区分重试轮次，天然形成不同的缓存键，且片段不会发到服务端。
 */
class RetryImageState internal constructor(
    val url: String,
    internal val maxRetries: Int
) {
    var attempt by mutableIntStateOf(0)
        private set
    var lastError by mutableStateOf<Throwable?>(null)
        private set

    val effectiveUrl: String
        get() = if (attempt == 0) url else "${url.substringBefore('#')}#r$attempt"

    val exhausted: Boolean
        get() = lastError != null && attempt >= maxRetries

    internal fun onError(t: Throwable?) {
        lastError = t ?: Throwable("Unknown")
    }

    internal fun onSuccess() {
        lastError = null
    }

    fun retry() {
        if (attempt < maxRetries) {
            attempt++
            lastError = null
        }
    }
}

@Composable
fun rememberRetryImageState(url: String, maxRetries: Int = 3): RetryImageState =
    remember(url, maxRetries) { RetryImageState(url, maxRetries) }

/** 失败后的自动退避重试循环；成功/重试耗尽时静默。 */
@Composable
fun RetryEffect(state: RetryImageState) {
    LaunchedEffect(state.lastError, state.attempt) {
        if (state.lastError == null || state.exhausted) return@LaunchedEffect
        delay(300L shl state.attempt.coerceIn(0, 2))
        state.retry()
    }
}

@Composable
fun RetrySubcomposeAsyncImage(
    state: RetryImageState,
    modifier: Modifier = Modifier,
    contentDescription: String?,
    contentScale: ContentScale = ContentScale.Crop,
    crossfadeMs: Int = 300,
    requestSize: coil3.size.Size? = null,
    onLoading: () -> Unit = {},
    onSuccess: () -> Unit = {},
    onSettledError: () -> Unit = {},
    loading: @Composable BoxScope.() -> Unit = { ProgressSpinner() },
    errorContent: (@Composable BoxScope.() -> Unit)? = null
) {
    RetryEffect(state)
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(state.effectiveUrl)
            .crossfade(crossfadeMs)
            .apply {
                // 详情页画质档位注入点：null = 默认按布局约束解码；
                // Size.ORIGINAL = 不限解码尺寸（原图档）
                if (requestSize != null) size(requestSize)
            }
            .build(),
        imageLoader = ImageLoaderFactory.getInstance(LocalContext.current),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        onLoading = {
            onLoading()
            ImageLoadProgress.clear(state.url)
        },
        onSuccess = {
            state.onSuccess()
            onSuccess()
            ImageLoadProgress.clear(state.url)
        },
        onError = { errorState ->
            state.onError(errorState.result.throwable)
            if (state.exhausted) {
                onSettledError()
                ImageLoadProgress.clear(state.url)
            }
        },
        // slot 实参带 SubcomposeAsyncImageScope（是 BoxScope 子类型）与状态参数，
        // 这里适配成无参的 BoxScope lambda
        loading = { _ -> loading() },
        error = { _ ->
            if (state.exhausted) {
                errorContent?.invoke(this)
                    ?: DefaultImageErrorContent(
                        errorText = describeError(state.lastError),
                        onRetry = { state.retry() },
                        modifier = Modifier.align(Alignment.Center)
                    )
            } else {
                // 自动重试的退避间隔里继续呈现加载态，避免错误浮层闪烁
                loading()
            }
        }
    )
}

@Composable
private fun ProgressSpinner() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = FA_Accent.copy(alpha = 0.5f),
            strokeWidth = 2.dp
        )
    }
}

@Composable
fun DefaultImageErrorContent(errorText: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = Color(0xFFFF5252),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            errorText,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = onRetry,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Text(
                stringResource(R.string.downloads_retry),
                color = FA_Accent,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/** 原始错误码字段：HTTP 状态优先，其余退回异常名/消息。 */
fun describeError(t: Throwable?): String {
    if (t == null) return "Unknown"
    var cause: Throwable? = t
    repeat(4) {
        val c = cause ?: return@repeat
        if (c is HttpException) return "HTTP ${c.response.code}"
        cause = c.cause
    }
    return t.message?.takeIf { it.isNotBlank() }?.take(80) ?: t.javaClass.simpleName
}
