package com.furflix.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
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
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.furflix.app.R
import com.furflix.app.data.local.DocFileCache
import com.furflix.app.data.repository.FurRepository
import com.furflix.app.translate.data.StoryGranularity
import com.furflix.app.translate.data.TranslationCategory
import com.furflix.app.translate.data.TranslationDisplayMode
import com.furflix.app.translate.data.TranslationSettings
import com.furflix.app.translate.ui.DocumentTranslationSettingsDialog
import com.furflix.app.translate.ui.TranslationEntry
import com.furflix.app.translate.ui.TranslationUiState
import com.furflix.app.translate.ui.rememberTranslationPrefs
import com.furflix.app.utils.DocumentTextExtractor
import com.furflix.app.utils.PdfTextExtractor
import com.furflix.app.ui.theme.BorderDark
import com.furflix.app.ui.theme.CardBackground
import com.furflix.app.ui.theme.MutedText
import com.furflix.app.ui.theme.SubtleGray
import kotlinx.coroutines.launch

/**
 * 自研全屏文档查看器（FA 文档/store 类作品：txt/docx/odt/rtf，以及 PDF）。
 * - 正文段落式排版（长文档走 LazyColumn），复用小说阅读器的翻译链路
 *   （整段/整句粒度快切 + 全文手动翻译，逐段双语渲染）；
 * - 查找：命中段落计数 + 前后跳转 + 命中高亮；
 * - 复制：长按复制单段 / 顶栏一键复制全文；
 * - 下载为 .txt：正文已解析时导出到系统 Downloads/FurFlix；
 * - PDF：文本层由 PdfBox 抽取，可复制可翻译；无文本层（扫描件/纯图）如实
 *   提示"该 PDF 无文本层"，仍可用详情页的 PDF 查看器看图；
 * - 点画面空白处隐藏/唤回控制栏（顶栏 + 查找条 + 翻译条），沉浸式阅读。
 *
 * 内容来源优先级：调用方直接给的正文（initialText，含"从作品描述查看故事"）
 * > 已缓存原件解析 > 下载后解析。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentViewerScreen(
    title: String,
    fileUrl: String,
    initialText: String,
    onClose: () -> Unit,
    /** 描述来源（store 正文写在作品描述里）时隐藏"下载为 txt"以外的原件语义 */
    sourceLabel: String? = null
) {
    BackHandler { onClose() }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val prefs = rememberTranslationPrefs()
    val settings = remember { TranslationSettings.getInstance(context) }
    // 文档链路专用翻译配置（未开启独立服务时等价于全局配置）
    val documentConfig by settings.documentConfig
        .collectAsState(initial = com.furflix.app.translate.data.TranslationConfig())
    val docOverride by settings.docOverrideFlow.collectAsState(initial = false)

    var attempt by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(initialText.isBlank()) }
    var failed by remember { mutableStateOf(false) }
    // 正文段落与媒体图；unsupported = 该扩展名无法在应用内解析（如实降级）
    var paragraphs by remember { mutableStateOf<List<String>>(emptyList()) }
    var media by remember { mutableStateOf<List<ByteArray>>(emptyList()) }
    var unsupported by remember { mutableStateOf(false) }
    // PDF 专属：抽到了文本层，但整篇为空（扫描件/纯图）→ 提示而非谎报成功
    var pdfNoTextLayer by remember { mutableStateOf(false) }
    var isPdf by remember { mutableStateOf(false) }

    LaunchedEffect(fileUrl, attempt) {
        if (initialText.isNotBlank()) {
            paragraphs = DocumentTextExtractor.textToParagraphs(initialText)
            loading = false
            return@LaunchedEffect
        }
        loading = true
        failed = false
        pdfNoTextLayer = false
        val ext = fileUrl.substringBefore('?').substringBefore('#')
            .substringAfterLast('.', "").lowercase()
        isPdf = ext == "pdf"
        // 原件统一走 DocFileCache：PDF 的文本抽取与页面渲染共用同一份字节
        val file = if (isPdf || DocumentTextExtractor.isSupported(ext)) {
            DocFileCache.obtain(context, fileUrl, ext.ifBlank { "bin" }) {
                FurRepository.getInstance(context).fetchSubmissionFile(fileUrl)
            }
        } else null
        if (file == null) {
            unsupported = !isPdf && !DocumentTextExtractor.isSupported(ext)
            failed = !unsupported
            loading = false
            return@LaunchedEffect
        }
        if (isPdf) {
            val text = PdfTextExtractor.extract(context, file)
            when {
                text == null -> failed = true
                text.isEmpty -> {
                    // 打不开才叫失败；打得开但没文字 = 扫描件，如实说明
                    pdfNoTextLayer = true
                    paragraphs = emptyList()
                }
                else -> paragraphs = PdfTextExtractor.toParagraphs(text.pages)
            }
        } else {
            val parsed = runCatching {
                DocumentTextExtractor.parse(ext, file.readBytes())
            }.getOrNull()
            paragraphs = parsed?.paragraphs ?: emptyList()
            media = parsed?.media ?: emptyList()
            failed = paragraphs.isEmpty() && media.isEmpty()
        }
        loading = false
    }

    // ── 翻译：与小说阅读器同链路，逐段 Entry，随内容重建、离屏取消；
    //    文档链路经 configOverride 走（可选）独立翻译服务 ──
    // 段落与该段的送翻单元打包进同一个条目对象，列表项只认这一个来源。
    // 此前段落列表和 entriesByParagraph 是两份各自 remember 的派生列表，条目
    // 内容靠下标跨表取值（entriesByParagraph[index]）；正文段落一重建、两份
    // 列表不在同一快照时就会 IndexOutOfBoundsException: Index 0 out of bounds
    // for length 0（用户实测于「从作品描述查看故事」）。打包后段落与译文单元
    // 永远同源，结构上不可能再错位。
    val paragraphBlocks = remember(paragraphs, prefs.storyGranularity, documentConfig) {
        paragraphs.map { paragraph ->
            val units = if (prefs.storyGranularity == StoryGranularity.SENTENCE) {
                splitSentences(paragraph)
            } else {
                listOf(paragraph)
            }
            DocumentParagraphBlock(
                text = paragraph,
                entries = units.map {
                    TranslationEntry(
                        text = it,
                        category = TranslationCategory.DESCRIPTION,
                        configOverride = settings.documentConfig
                    )
                }
            )
        }
    }
    val allEntries = remember(paragraphBlocks) { paragraphBlocks.flatMap { it.entries } }
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

    // ── 控制栏显隐：点正文空白处切换（顶栏 + 查找条 + 翻译条一起收起）──
    var controlsVisible by remember { mutableStateOf(true) }

    val allCopiedToast = stringResource(R.string.toast_document_full_copied)
    var showTranslationSettings by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0A0C)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── 顶栏：返回 / 标题 / 查找 / 复制全文 / 下载 txt / 翻译服务 ──
            AnimatedVisibility(
                visible = controlsVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (sourceLabel != null) {
                        Text(
                            text = sourceLabel,
                            fontSize = 11.sp,
                            color = SubtleGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
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
                // 下载为 .txt：正文已在手，直接导出（PDF 抽出的文本同样可导）
                // 文案在组合层解析成模板（lint LocalContextGetResourceValueCall：
                // 回调里 context.getString 不是配置感知的，会取到过期的资源配置），
                // 回调只做 String.format
                val txtSavedTemplate = stringResource(R.string.toast_document_txt_saved)
                val txtFailedText = stringResource(R.string.toast_document_txt_failed)
                IconButton(
                    onClick = {
                        scope.launch {
                            val saved = exportTextToDownloads(context, title, paragraphs)
                            android.widget.Toast.makeText(
                                context,
                                if (saved != null) String.format(txtSavedTemplate, saved) else txtFailedText,
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    enabled = paragraphs.isNotEmpty()
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = stringResource(R.string.cd_document_save_txt),
                        tint = if (paragraphs.isEmpty()) MutedText else Color.White
                    )
                }
                // 文档专用翻译服务入口（设置页同款配置也可从此处打开）
                IconButton(onClick = { showTranslationSettings = true }) {
                    Icon(
                        Icons.Default.Translate,
                        contentDescription = stringResource(R.string.document_translation_service),
                        tint = if (docOverride) MaterialTheme.colorScheme.primary else Color.White
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
                }
            }

            when {
                loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                failed -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Description,
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
                pdfNoTextLayer -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = SubtleGray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.pdf_no_text_layer),
                            fontSize = 13.sp,
                            color = SubtleGray
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
                    AnimatedVisibility(
                        visible = controlsVisible,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            StoryReaderHeader(allEntries, paragraphs.size, prefs, settings, scope)
                            androidx.compose.material3.HorizontalDivider(color = BorderDark, thickness = 1.dp)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        // 用 items(count) 而不是 itemsIndexed(list)：后者的 key/content
                        // 包装会再按下标回读传入的列表，条目 lambda 里只留一次 getOrNull
                        items(count = paragraphBlocks.size, key = { idx -> "p$idx" }) { index ->
                            val block = paragraphBlocks.getOrNull(index) ?: return@items
                            DocumentParagraph(
                                paragraph = block.text,
                                highlightQuery = findQuery.trim(),
                                entries = block.entries,
                                prefs = prefs,
                                onTap = { controlsVisible = !controlsVisible }
                            )
                            if (index != paragraphBlocks.lastIndex || media.isNotEmpty()) {
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
                            items(count = media.size, key = { idx -> "m$idx" }) { index ->
                                val bytes = media.getOrNull(index) ?: return@items
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

    if (showTranslationSettings) {
        DocumentTranslationSettingsDialog(onDismiss = { showTranslationSettings = false })
    }
}

/**
 * 文档正文的一个条目：段落原文 + 该段切出的送翻单元（整段 = 1 个，整句 = n 个）。
 * 两者必须在同一个对象里成对流动——列表项按条目渲染，段落与译文单元不会错位。
 */
private data class DocumentParagraphBlock(
    val text: String,
    val entries: List<TranslationEntry>
)

/**
 * 单段：命中高亮 + 原生文本选择 + 单击切控制栏 + 翻译渲染（与小说阅读器同一 StoryParagraph）。
 *
 * 文本选择交给 [SelectionContainer]：长按出的是系统自己的选择手柄与浮动工具条
 * （复制/全选/分享，随系统语言与主题），可逐字/逐词拖选——这是 Android 用户
 * 对"能选中文字"的既有预期。此前是长按整段直接进剪贴板，粒度粗且没有选择过程，
 * 想复制一句话里的一半也做不到。
 *
 * 选择容器只包住文本本身，单击切控制栏的 clickable 留在外层：SelectionContainer
 * 只消费长按与拖拽，普通单击会冒泡到外层，两个手势互不抢占。
 * 译文（StoryParagraph）一并包进来，原文与译文可以跨行一起选中。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentParagraph(
    paragraph: String,
    highlightQuery: String,
    entries: List<TranslationEntry>,
    prefs: com.furflix.app.translate.data.TranslationUiPrefs,
    onTap: () -> Unit
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

    // 译文显示模式（翻译设置里可选双语对照 / 仅显示译文）。此前本组件恒显原文，
    // 于是"仅显示译文"在文档查看器里根本不生效——这里与 StoryParagraph 的判据对齐：
    // 只有当本段所有送翻单元都成功且正在展示译文时，才允许隐藏原文。
    val states = entries.map { it.state.collectAsStateWithLifecycle().value }
    val allSuccess = states.isNotEmpty() && states.all { it is TranslationUiState.Success }
    val translationShowing = allSuccess &&
        states.all { (it as TranslationUiState.Success).showing }
    val translationOnly = prefs.displayMode(TranslationCategory.DESCRIPTION) ==
        TranslationDisplayMode.TRANSLATION_ONLY

    Column(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            // 无涟漪：整段文本的可点区域不该在阅读时闪一下水波
            indication = null,
            onClick = onTap
        )
    ) {
        SelectionContainer {
            Column {
                if (!(translationOnly && translationShowing)) {
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 26.sp,
                            color = Color(0xFFE0E0E0)
                        )
                    )
                }
                StoryParagraph(
                    paragraph = "",
                    entries = entries,
                    prefs = prefs
                )
            }
        }
    }
}

/**
 * 正文导出为 .txt 到系统 Downloads/FurFlix（Q+ 走 MediaStore，老版本直写
 * 公共目录并触发扫描）。返回落盘文件名，失败返回 null。
 */
private suspend fun exportTextToDownloads(
    context: android.content.Context,
    title: String,
    paragraphs: List<String>
): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val safeTitle = title.trim()
        .replace(Regex("[^\\w\\u4e00-\\u9fff\\- ]"), "_")
        .trim().ifBlank { "document" }.take(60)
    val name = "FurFlix_$safeTitle.txt"
    val body = paragraphs.joinToString("\n\n")
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/FurFlix")
            }
            val uri = context.contentResolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            ) ?: return@withContext null
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(body.toByteArray(Charsets.UTF_8))
            } ?: return@withContext null
        } else {
            @Suppress("DEPRECATION")
            val dir = java.io.File(
                android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                ),
                "FurFlix"
            )
            dir.mkdirs()
            java.io.File(dir, name).writeText(body, Charsets.UTF_8)
            android.media.MediaScannerConnection.scanFile(
                context, arrayOf(java.io.File(dir, name).absolutePath),
                arrayOf("text/plain"), null
            )
        }
        name
    } catch (_: Exception) {
        null
    }
}
