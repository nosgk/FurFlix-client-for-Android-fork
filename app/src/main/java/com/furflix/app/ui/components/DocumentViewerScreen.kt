package com.furflix.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.furflix.app.R
import com.furflix.app.data.repository.FurRepository
import com.furflix.app.translate.data.StoryGranularity
import com.furflix.app.translate.data.TranslationCategory
import com.furflix.app.translate.data.TranslationSettings
import com.furflix.app.translate.ui.TranslationEntry
import com.furflix.app.translate.ui.rememberTranslationPrefs
import com.furflix.app.utils.DocumentTextExtractor
import com.furflix.app.ui.theme.BorderDark
import com.furflix.app.ui.theme.CardBackground
import com.furflix.app.ui.theme.MutedText
import com.furflix.app.ui.theme.SubtleGray
import kotlinx.coroutines.launch

/**
 * 自研全屏文档查看器（FA 文档/store 类作品：txt/docx/odt/rtf）。
 * - 正文段落式排版（长文档走 LazyColumn），复用小说阅读器的翻译链路
 *   （整段/整句粒度快切 + 全文手动翻译，逐段双语渲染）；
 * - 查找：命中段落计数 + 前后跳转 + 命中高亮；
 * - 复制：长按复制单段 / 顶栏一键复制全文；
 * - 内嵌媒体图（docx/odt）在正文尾部整节展示。
 * 内容来源：详情页已载入的正文（txt storyText）优先，未载入或不支持
 * 直读的类型按扩展名抓取原件解析；.doc 等无可靠解析的类型如实降级。
 */
@Composable
fun DocumentViewerScreen(
    title: String,
    fileUrl: String,
    initialText: String,
    onClose: () -> Unit
) {
    BackHandler { onClose() }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val prefs = rememberTranslationPrefs()
    val settings = remember { TranslationSettings.getInstance(context) }

    var attempt by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(initialText.isBlank()) }
    var failed by remember { mutableStateOf(false) }
    // 正文段落与媒体图；unsupported = 该扩展名无法在应用内解析（如实降级）
    var paragraphs by remember { mutableStateOf<List<String>>(emptyList()) }
    var media by remember { mutableStateOf<List<ByteArray>>(emptyList()) }
    var unsupported by remember { mutableStateOf(false) }

    LaunchedEffect(fileUrl, attempt) {
        if (initialText.isNotBlank()) {
            paragraphs = DocumentTextExtractor.textToParagraphs(initialText)
            loading = false
            return@LaunchedEffect
        }
        loading = true
        failed = false
        val ext = fileUrl.substringBefore('?').substringBefore('#')
            .substringAfterLast('.', "").lowercase()
        if (!DocumentTextExtractor.isSupported(ext)) {
            unsupported = true
            loading = false
            return@LaunchedEffect
        }
        val bytes = FurRepository.getInstance(context).fetchSubmissionFile(fileUrl)
        if (bytes == null) {
            failed = true
            loading = false
            return@LaunchedEffect
        }
        val parsed = runCatching { DocumentTextExtractor.parse(ext, bytes) }.getOrNull()
        paragraphs = parsed?.paragraphs ?: emptyList()
        media = parsed?.media ?: emptyList()
        failed = paragraphs.isEmpty() && (media.isEmpty())
        loading = false
    }

    // ── 翻译：与小说阅读器同链路，逐段 Entry，随内容重建、离屏取消 ──
    val paragraphUnits = remember(paragraphs, prefs.storyGranularity) {
        if (prefs.storyGranularity == StoryGranularity.SENTENCE) {
            paragraphs.map { splitSentences(it) }
        } else {
            paragraphs.map { listOf(it) }
        }
    }
    val entriesByParagraph = remember(paragraphUnits) {
        paragraphUnits.map { units -> units.map { TranslationEntry(it, TranslationCategory.DESCRIPTION) } }
    }
    val allEntries = remember(entriesByParagraph) { entriesByParagraph.flatten() }
    DisposableEffect(allEntries) {
        onDispose { allEntries.forEach { it.cancel() } }
    }

    // ── 查找：命中段落索引 + 光标跳转 ──
    var findOpen by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var matchCursor by remember { mutableIntStateOf(0) }
    val matchIndices = remember(paragraphs, findQuery) {
        val q = findQuery.trim()
        if (q.isEmpty()) emptyList()
        else paragraphs.withIndex().filter { it.value.contains(q, ignoreCase = true) }.map { it.index }
    }

    fun scrollToMatch(index: Int) {
        if (matchIndices.isEmpty()) return
        matchCursor = ((index % matchIndices.size) + matchIndices.size) % matchIndices.size
        scope.launch {
            runCatching { listState.animateScrollToItem(matchIndices[matchCursor]) }
        }
    }

    val copiedToast = stringResource(R.string.toast_copied_to_clipboard)
    val allCopiedToast = stringResource(R.string.toast_document_full_copied)

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0A0C)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── 顶栏：返回 / 标题 / 查找 / 复制全文 ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 8.dp)
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = Color.White
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { findOpen = !findOpen }) {
                    Icon(
                        Icons.Default.FindInPage,
                        contentDescription = stringResource(R.string.cd_document_find),
                        tint = if (findOpen) MaterialTheme.colorScheme.primary else Color.White
                    )
                }
                IconButton(
                    onClick = {
                        clipboard.setText(
                            AnnotatedString(paragraphs.joinToString("\n\n"))
                        )
                        android.widget.Toast.makeText(context, allCopiedToast, android.widget.Toast.LENGTH_SHORT).show()
                    },
                    enabled = paragraphs.isNotEmpty()
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.cd_document_copy_all),
                        tint = if (paragraphs.isEmpty()) MutedText else Color.White
                    )
                }
            }

            // ── 查找条：输入 + 命中计数 + 前后跳转 ──
            AnimatedVisibility(
                visible = findOpen,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp)
                ) {
                    OutlinedTextField(
                        value = findQuery,
                        onValueChange = {
                            findQuery = it
                            matchCursor = 0
                        },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.document_find_hint), fontSize = 13.sp, color = MutedText) },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (findQuery.isBlank()) "" else "${matchCursor + 1}/${matchIndices.size}",
                        fontSize = 12.sp,
                        color = SubtleGray,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(onClick = { scrollToMatch(matchCursor - 1) }, enabled = matchIndices.isNotEmpty()) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.document_find_prev), tint = Color.White)
                    }
                    IconButton(onClick = { scrollToMatch(matchCursor + 1) }, enabled = matchIndices.isNotEmpty()) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.document_find_next), tint = Color.White)
                    }
                }
            }

            when {
                loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                failed -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = SubtleGray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.document_load_failed),
                            fontSize = 13.sp,
                            color = SubtleGray
                        )
                        Spacer(Modifier.height(10.dp))
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.downloads_retry),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { attempt++ }
                                .padding(4.dp)
                        )
                    }
                }
                unsupported -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = SubtleGray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.document_unsupported),
                            fontSize = 13.sp,
                            color = SubtleGray,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
                else -> {
                    // 翻译控制条（复用小说阅读器：段落数 + 粒度快切 + 全文翻译）
                    StoryReaderHeader(allEntries, paragraphs.size, prefs, settings, scope)
                    androidx.compose.material3.HorizontalDivider(color = BorderDark, thickness = 1.dp)

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        itemsIndexed(paragraphs, key = { idx, _ -> "p$idx" }) { index, paragraph ->
                            DocumentParagraph(
                                paragraph = paragraph,
                                highlightQuery = findQuery.trim(),
                                entries = entriesByParagraph[index],
                                prefs = prefs,
                                onLongPress = {
                                    clipboard.setText(AnnotatedString(paragraph))
                                    android.widget.Toast.makeText(context, copiedToast, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                            if (index != paragraphs.lastIndex || media.isNotEmpty()) {
                                Spacer(Modifier.height(14.dp))
                            }
                        }
                        if (media.isNotEmpty()) {
                            item(key = "media_header") {
                                Text(
                                    stringResource(R.string.document_media_count, media.size),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SubtleGray,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )
                            }
                            itemsIndexed(media, key = { idx, _ -> "m$idx" }) { _, bytes ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = CardBackground,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 10.dp)
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(bytes).build(),
                                        imageLoader = ImageLoaderFactory.getInstance(context),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 单段：命中高亮 + 长按复制 + 翻译渲染（与小说阅读器同一 StoryParagraph）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentParagraph(
    paragraph: String,
    highlightQuery: String,
    entries: List<TranslationEntry>,
    prefs: com.furflix.app.translate.data.TranslationUiPrefs,
    onLongPress: () -> Unit
) {
    val annotated: AnnotatedString = remember(paragraph, highlightQuery) {
        if (highlightQuery.isEmpty()) {
            AnnotatedString(paragraph)
        } else {
            buildAnnotatedString {
                append(paragraph)
                val q = highlightQuery.lowercase()
                val lower = paragraph.lowercase()
                var from = 0
                while (true) {
                    val hit = lower.indexOf(q, from)
                    if (hit < 0) break
                    addStyle(
                        SpanStyle(background = Color(0xFF3D6BFF).copy(alpha = 0.45f)),
                        hit,
                        hit + q.length
                    )
                    from = hit + q.length
                }
            }
        }
    }

    Column(
        modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress)
    ) {
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyLarge.copy(
                lineHeight = 26.sp,
                color = Color(0xFFE0E0E0)
            )
        )
        StoryParagraph(
            paragraph = "",
            entries = entries,
            prefs = prefs
        )
    }
}
