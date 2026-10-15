package com.furflix.app.subtitle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furflix.app.R
import com.furflix.app.subtitle.MarkdownInline
import com.furflix.app.subtitle.SubtitleTrack
import com.furflix.app.subtitle.TranscriptionException

/**
 * 字幕层：按当前播放位置取命中的那条字幕渲染。
 *
 * 防截断是硬要求（截断等于漏台词）：长句 / 双语字幕整段换行，字号随长度退档，
 * 容器高度由内容决定，不设 maxLines。行内 `**粗体**` / `*斜体*` 转成样式，
 * 避免观众看到字面星号。
 */
@Composable
fun SubtitlePanel(
    track: SubtitleTrack,
    positionMs: Long,
    modifier: Modifier = Modifier
) {
    val cue = track.cueAt(positionMs) ?: return
    val annotated = remember(cue) { buildSubtitleText(cue.text) }
    val fontSize = subtitleFontSize(cue.text.length)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = annotated,
            color = Color.White,
            fontSize = fontSize,
            lineHeight = fontSize * 1.35f,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 字号随文本长度退档：双语字幕常在 100 字符以上，固定字号会撑破容器。 */
fun subtitleFontSize(length: Int): TextUnit = when {
    length > 160 -> 11.sp
    length > 90 -> 13.sp
    length > 40 -> 15.sp
    else -> 17.sp
}

/** 转录错误 → 可展示文案。 */
@Composable
fun transcriptionErrorText(kind: TranscriptionException.Kind): String = when (kind) {
    TranscriptionException.Kind.NOT_CONFIGURED -> stringResource(R.string.subtitle_error_not_configured)
    TranscriptionException.Kind.INVALID_KEY -> stringResource(R.string.subtitle_error_invalid_key)
    TranscriptionException.Kind.RATE_LIMITED -> stringResource(R.string.subtitle_error_rate_limited)
    TranscriptionException.Kind.SERVER -> stringResource(R.string.subtitle_error_server)
    TranscriptionException.Kind.NETWORK -> stringResource(R.string.subtitle_error_network)
    TranscriptionException.Kind.BAD_RESPONSE -> stringResource(R.string.subtitle_error_bad_response)
    TranscriptionException.Kind.AUDIO_TOO_LARGE -> stringResource(R.string.subtitle_error_too_large)
}

private fun buildSubtitleText(raw: String): AnnotatedString = buildAnnotatedString {
    MarkdownInline.parse(raw).forEach { span ->
        withStyle(
            SpanStyle(
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null
            )
        ) {
            append(span.text)
        }
    }
}
