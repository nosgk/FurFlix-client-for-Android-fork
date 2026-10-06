package com.furflix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.furflix.app.R
import com.furflix.app.data.model.Submission
import com.furflix.app.translate.data.StoryGranularity
import com.furflix.app.translate.data.TranslationCategory
import com.furflix.app.translate.data.TranslationDisplayMode
import com.furflix.app.translate.data.TranslationSettings
import com.furflix.app.translate.data.TranslationUiPrefs
import com.furflix.app.translate.ui.TranslationEntry
import com.furflix.app.translate.ui.TranslationUiState
import com.furflix.app.translate.ui.rememberTranslationPrefs
import com.furflix.app.translate.ui.translationErrorText
import com.furflix.app.ui.theme.BorderDark
import com.furflix.app.ui.theme.CardBackground
import com.furflix.app.ui.theme.MutedText
import com.furflix.app.ui.theme.SubtleGray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 小说(.txt)正文阅读器：段落式排版 + 可选全文翻译。
 * 送翻单元按设置切分（整段/整句，设置→翻译可改，页内也可快切）；
 * 展示跟随"简介"类别的双语/仅译文方式逐段渲染。
 * 全文翻译为手动触发——整本小说动辄上百请求，不适合自动。
 */
@Composable
fun StoryReader(sub: Submission) {
    val context = LocalContext.current
    val prefs = rememberTranslationPrefs()
    val settings = remember { TranslationSettings.getInstance(context) }
    val scope = rememberCoroutineScope()

    // 正文 → 段落（空行分隔；归一化 CRLF）
    val paragraphs = remember(sub.id, sub.storyText) {
        sub.storyText.replace("\r\n", "\n")
            .split(Regex("\n{2,}"))
            .map { it.trim('\n', ' ') }
            .filter { it.isNotBlank() }
    }
    // 每段的送翻单元：整段 = 段落自身；整句 = 句子列表（切分粒度变化即重建）
    val paragraphUnits = remember(paragraphs, prefs.storyGranularity) {
        if (prefs.storyGranularity == StoryGranularity.SENTENCE) {
            paragraphs.map { splitSentences(it) }
        } else {
            paragraphs.map { listOf(it) }
        }
    }
    val entriesByParagraph = remember(paragraphUnits) {
        paragraphUnits.map { units ->
            units.map { TranslationEntry(it, TranslationCategory.DESCRIPTION) }
        }
    }
    val allEntries = remember(entriesByParagraph) { entriesByParagraph.flatten() }
    DisposableEffect(allEntries) {
        onDispose { allEntries.forEach { it.cancel() } }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        StoryReaderHeader(allEntries, paragraphs.size, prefs, settings, scope)
        Spacer(Modifier.height(12.dp))
        paragraphs.forEachIndexed { pIndex, paragraph ->
            StoryParagraph(
                paragraph = paragraph,
                entries = entriesByParagraph[pIndex],
                prefs = prefs
            )
            if (pIndex != paragraphs.lastIndex) Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
fun StoryReaderHeader(
    entries: List<TranslationEntry>,
    paragraphCount: Int,
    prefs: TranslationUiPrefs,
    settings: TranslationSettings,
    scope: CoroutineScope
) {
    val states = entries.map { it.state.collectAsStateWithLifecycle().value }
    val successCount = states.count { it is TranslationUiState.Success }
    val loading = states.any { it is TranslationUiState.Loading }
    val hasPending = states.any { it is TranslationUiState.Idle || it is TranslationUiState.Failed }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.story_paragraph_count, paragraphCount),
            fontSize = 13.sp,
            color = SubtleGray
        )
        Spacer(Modifier.weight(1f))
        if (prefs.enabled) {
            // 粒度快切：整段 ↔ 整句（写入全局设置，下次进页保持）
            TextButton(
                onClick = {
                    val next = if (prefs.storyGranularity == StoryGranularity.SENTENCE) {
                        StoryGranularity.PARAGRAPH
                    } else {
                        StoryGranularity.SENTENCE
                    }
                    scope.launch { settings.saveStoryGranularity(next) }
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = stringResource(
                        if (prefs.storyGranularity == StoryGranularity.SENTENCE) {
                            R.string.story_granularity_sentence
                        } else {
                            R.string.story_granularity_paragraph
                        }
                    ),
                    fontSize = 11.sp,
                    color = MutedText
                )
            }
            Spacer(Modifier.width(4.dp))
            // 全文翻译：有未译单元 → 探针首发（失败不空发剩余）；全部完成 → 整体切换显示
            TextButton(
                onClick = {
                    if (loading) return@TextButton
                    if (!hasPending) {
                        entries.forEach { it.translate() } // Success 态 = 切换显示
                    } else {
                        scope.launch {
                            val pending = entries.filter {
                                it.state.value is TranslationUiState.Idle ||
                                    it.state.value is TranslationUiState.Failed
                            }
                            if (pending.isEmpty()) return@launch
                            pending.first().translate()
                            val firstResult = pending.first().state.first { it !is TranslationUiState.Loading }
                            // 首单元失败（如未配 key）：错误已落在对应段落上，不批量空发
                            if (firstResult is TranslationUiState.Failed) return@launch
                            pending.drop(1).forEach { it.translate() }
                        }
                    }
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = when {
                        loading -> stringResource(R.string.story_translating_progress, successCount, entries.size)
                        hasPending -> stringResource(R.string.story_translate_all)
                        else -> stringResource(R.string.story_hide_translations)
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun StoryParagraph(
    paragraph: String,
    entries: List<TranslationEntry>,
    prefs: TranslationUiPrefs
) {
    val mode = prefs.displayMode(TranslationCategory.DESCRIPTION)
    val states = entries.map { it.state.collectAsStateWithLifecycle().value }
    val allSuccess = states.isNotEmpty() && states.all { it is TranslationUiState.Success }
    val anyLoading = states.any { it is TranslationUiState.Loading }
    val failure = states.filterIsInstance<TranslationUiState.Failed>().firstOrNull()
    val showing = allSuccess && states.all { (it as TranslationUiState.Success).showing }

    // 阅读排版：与详情页 HTML 正文同色系；行距放宽到 1.6 左右
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
        lineHeight = 26.sp,
        color = Color(0xFFE0E0E0)
    )

    Column {
        if (!(allSuccess && showing && mode == TranslationDisplayMode.TRANSLATION_ONLY)) {
            Text(text = paragraph, style = bodyStyle)
        }
        if (prefs.enabled) {
            when {
                anyLoading && !allSuccess -> Text("…", style = bodyStyle.copy(color = MutedText))
                allSuccess && showing -> {
                    val translated = entries.mapIndexed { i, _ ->
                        (states[i] as TranslationUiState.Success).translated
                    }.joinToString(" ").trim()
                    if (translated.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(text = translated, style = bodyStyle.copy(color = SubtleGray))
                    }
                }
                failure != null -> {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = translationErrorText(failure),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/** 句子切分：句末标点（含中文标点）后的空白处断开；无标点长段保持单句。 */
fun splitSentences(text: String): List<String> =
    text.split(Regex("(?<=[.!?…。！？])\\s+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
