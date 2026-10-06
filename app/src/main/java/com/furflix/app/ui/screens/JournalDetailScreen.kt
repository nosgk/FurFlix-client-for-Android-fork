package com.furflix.app.ui.screens

import android.app.Application
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.furflix.app.R
import com.furflix.app.data.model.Journal
import com.furflix.app.translate.data.TranslationCategory
import com.furflix.app.translate.data.TranslationDisplayMode
import com.furflix.app.translate.ui.TranslatableAutoEffect
import com.furflix.app.translate.ui.TranslationCaption
import com.furflix.app.translate.ui.TranslationIconButton
import com.furflix.app.translate.ui.TranslationUiState
import com.furflix.app.translate.ui.rememberTranslationEntry
import com.furflix.app.translate.ui.rememberTranslationPrefs
import com.furflix.app.translate.ui.translationErrorText
import com.furflix.app.ui.components.CommentsSection
import com.furflix.app.ui.components.ErrorState
import com.furflix.app.ui.components.HtmlTextView
import com.furflix.app.ui.components.HazeTopAppBar
import com.furflix.app.ui.theme.BorderDark
import com.furflix.app.ui.theme.CardBackground
import com.furflix.app.ui.theme.DarkBackground
import com.furflix.app.ui.theme.DarkSurface
import com.furflix.app.ui.theme.MutedText
import com.furflix.app.ui.theme.SubtleGray
import com.furflix.app.viewmodel.JournalDetailViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun JournalDetailScreen(
    journalId: String,
    viewModel: JournalDetailViewModel,
    onBack: () -> Unit,
    onAuthorClick: (String) -> Unit = {}
) {
    BackHandler { onBack() }
    val detail by viewModel.detail.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val hazeState = remember { HazeState() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .hazeSource(state = hazeState)
    ) {
        HazeTopAppBar(
            title = {
                Text(
                    text = detail?.author?.ifBlank { detail?.authorUsername ?: "" } ?: "",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            hazeState = hazeState,
            navigationIcon = {
                androidx.compose.material3.IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            }
        )

        when {
            isLoading && detail == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            errorMessage != null && detail == null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(40.dp))
                    ErrorState(message = errorMessage ?: "")
                    TextButton(onClick = { viewModel.load() }) {
                        Text(stringResource(R.string.downloads_retry))
                    }
                }
            }
            detail != null -> JournalDetailContent(
                journal = detail!!,
                onAuthorClick = onAuthorClick
            )
        }
    }
}

@Composable
private fun JournalDetailContent(
    journal: Journal,
    onAuthorClick: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // ── 标题（双语翻译：原文恒显，译文按类别显示方式跟进）──
        val prefs = rememberTranslationPrefs()
        val titleEntry = rememberTranslationEntry(
            key = journal.id,
            text = journal.title,
            category = TranslationCategory.TITLE,
            singleRequest = true
        )
        TranslatableAutoEffect(titleEntry, prefs)
        val titleState by titleEntry.state.collectAsStateWithLifecycle()
        val titleMode = prefs.displayMode(TranslationCategory.TITLE)
        val titleSuccess = titleState as? TranslationUiState.Success

        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = if (titleSuccess != null && titleSuccess.showing &&
                    titleMode == TranslationDisplayMode.TRANSLATION_ONLY
                ) {
                    titleSuccess.translated
                } else {
                    journal.title
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            if (prefs.enabled) {
                Spacer(modifier = Modifier.width(8.dp))
                TranslationIconButton(entry = titleEntry, enabled = true)
            }
        }
        if (titleSuccess != null && titleSuccess.showing &&
            titleMode == TranslationDisplayMode.BILINGUAL &&
            titleSuccess.translated.isNotBlank() &&
            titleSuccess.translated != journal.title
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = titleSuccess.translated,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.72f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── 作者行 ──
        if (journal.authorUsername.isNotEmpty() || journal.author.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        val username = journal.authorUsername.ifEmpty { journal.author }
                        if (username.isNotEmpty()) onAuthorClick(username)
                    }
                    .padding(vertical = 4.dp)
            ) {
                AsyncImage(
                    model = journal.authorAvatar,
                    contentDescription = journal.authorUsername,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(DarkSurface)
                        .border(1.dp, BorderDark, CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = journal.author.ifBlank { journal.authorUsername },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Text(
                        text = "@${journal.authorUsername}",
                        fontSize = 12.sp,
                        color = SubtleGray
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // ── 日期 + 分级 ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (journal.date.isNotEmpty()) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MutedText,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = journal.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
            if (journal.rating.isNotEmpty()) {
                Text(
                    text = journal.rating,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 正文（HTML + JOURNAL 类别翻译，双语对照/仅译文由设置决定）──
        if (journal.body.isNotBlank()) {
            val bodyEntry = rememberTranslationEntry(
                key = journal.id,
                text = journal.body,
                category = TranslationCategory.JOURNAL,
                contentIsHtml = true
            )
            val bodyState by bodyEntry.state.collectAsStateWithLifecycle()
            val bodyMode = prefs.displayMode(TranslationCategory.JOURNAL)
            TranslatableAutoEffect(bodyEntry, prefs)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBackground)
                    .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
                    .padding(16.dp)
                    .animateContentSize()
            ) {
                if (prefs.enabled) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TranslationIconButton(entry = bodyEntry, enabled = true)
                    }
                }
                val bodySuccess = bodyState as? TranslationUiState.Success
                // 仅译文模式：原地替换原文（htmlIn 服务商保留链接与排版）
                HtmlTextView(
                    html = if (bodySuccess != null && bodySuccess.showing &&
                        bodyMode == TranslationDisplayMode.TRANSLATION_ONLY
                    ) {
                        bodySuccess.translated
                    } else {
                        journal.body
                    },
                    onAuthorClick = onAuthorClick
                )
                if (bodySuccess != null && bodySuccess.showing &&
                    bodyMode == TranslationDisplayMode.BILINGUAL
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(BorderDark)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HtmlTextView(html = bodySuccess.translated, onAuthorClick = onAuthorClick)
                }
                if (bodySuccess != null && bodySuccess.showing) {
                    TranslationCaption(bodyEntry, bodySuccess, bodyMode)
                }
                val bodyFailure = bodyState as? TranslationUiState.Failed
                if (bodyFailure != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = translationErrorText(bodyFailure),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // ── 页脚（站点原文，如 commission 状态）──
        if (journal.footer.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = journal.footer,
                fontSize = 12.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = SubtleGray
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 评论（COMMENT 类别翻译在 CommentsSection 内建）──
        Text(
            text = "${stringResource(R.string.post_comments)} (${journal.commentList.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (journal.commentList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBackground)
                    .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.post_no_comments), color = MutedText, fontSize = 14.sp)
            }
        } else {
            CommentsSection(comments = journal.commentList, onAuthorClick = onAuthorClick)
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}
