package com.furflix.app.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import com.furflix.app.data.download.DownloadManager
import com.furflix.app.data.download.DownloadStatus
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import android.content.ClipData
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.furflix.app.R
import com.furflix.app.data.model.Comment
import com.furflix.app.data.model.GalleryFolder
import com.furflix.app.data.model.Submission
import com.furflix.app.data.remote.DynamicThumbnail
import com.furflix.app.ui.components.CustomTabRow
import com.furflix.app.ui.components.CommentsSection
import com.furflix.app.ui.components.DefaultHazeStyle
import com.furflix.app.ui.components.HtmlTextView
import com.furflix.app.ui.components.LocalThumbnailQuality
import com.furflix.app.ui.screens.GlassActionBar
import com.furflix.app.ui.screens.GlassActionItem
import com.furflix.app.ui.components.Particle
import com.furflix.app.ui.components.ProgressiveImageContent
import com.furflix.app.ui.components.PdfViewerScreen
import com.furflix.app.ui.components.HazeTopAppBar
import com.furflix.app.ui.components.ImageLoaderFactory
import com.furflix.app.data.local.DownloadHelper
import com.furflix.app.data.repository.FurRepository
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.furflix.app.ui.components.ZoomableImageViewer
import com.furflix.app.ui.components.StoryReader
import com.furflix.app.ui.components.FlashPlayerViewer
import com.furflix.app.ui.components.GifFrameViewer
import com.furflix.app.ui.components.AudioHeroContent
import com.furflix.app.ui.components.DocumentHeroContent
import com.furflix.app.ui.components.DocumentViewerScreen
import com.furflix.app.ui.theme.*
import com.furflix.app.viewmodel.MainViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.furflix.app.translate.data.TranslationCategory
import com.furflix.app.translate.data.TranslationDisplayMode
import com.furflix.app.translate.ui.LocalTagTranslationEnabled
import com.furflix.app.translate.ui.TranslationUiState
import com.furflix.app.translate.ui.TranslatableAutoEffect
import com.furflix.app.translate.ui.TranslatableText
import com.furflix.app.translate.ui.TranslationCaption
import com.furflix.app.translate.ui.TranslationEntry
import com.furflix.app.translate.ui.TranslationIconButton
import com.furflix.app.translate.ui.rememberTranslationEntry
import com.furflix.app.translate.ui.rememberTranslationPrefs
import com.furflix.app.translate.ui.translationErrorBase
import com.furflix.app.translate.ui.translationErrorText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    initialPostId: String,
    submissions: List<Submission>,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    onAuthorClick: (String) -> Unit = {},
    onNavigateToPost: (String) -> Unit = {},
    onTagClick: (String) -> Unit = {},
    onTagAppend: (String) -> Unit = {},
    onOpenFolder: (GalleryFolder) -> Unit = {},
    viewModel: MainViewModel
) {
    val submissionDetailsMap by viewModel.submissionDetails.collectAsState()
    val cardStats by viewModel.cardStats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // 双栏决策：偏好优先；auto 时屏宽 ≥600dp（平板/横屏）启用左图右文
    val detailRepository = remember { FurRepository.getInstance(context) }
    val detailLayoutPref by detailRepository.detailLayoutFlow.collectAsState(initial = FurRepository.DETAIL_LAYOUT_AUTO)
    val detailSplit by detailRepository.detailSplitFlow.collectAsState(initial = FurRepository.DETAIL_SPLIT_DEFAULT)
    val twoPane = when (detailLayoutPref) {
        FurRepository.DETAIL_LAYOUT_DUAL -> true
        FurRepository.DETAIL_LAYOUT_SINGLE -> false
        else -> LocalConfiguration.current.screenWidthDp >= 600
    }

    // 翻译功能：页面级标签开关（初始值来自全局设置；存在即完全接管本页）
    val translationPrefs = rememberTranslationPrefs()
    val tagTranslateToggle = remember(translationPrefs.tagsAuto) { mutableStateOf(translationPrefs.tagsAuto) }

    val foundInList = submissions.any { it.id == initialPostId }
    val effectiveSubmissions = remember(submissions, initialPostId) {
        if (foundInList) submissions else {
            listOf(Submission(id = initialPostId, title = "", author = "", thumbnailUrl = "", fullImageUrl = "", description = "", tags = emptyList()))
        }
    }

    val initialIndex = remember(effectiveSubmissions) {
        effectiveSubmissions.indexOfFirst { it.id == initialPostId }.takeIf { it >= 0 } ?: 0
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { effectiveSubmissions.size.coerceAtLeast(1) }
    )

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage >= effectiveSubmissions.size - 3) {
            onLoadMore()
        }
        // 相邻页预取：详情解析 + 原图预热，翻页即秒开
        for (p in intArrayOf(pagerState.currentPage - 1, pagerState.currentPage + 1)) {
            effectiveSubmissions.getOrNull(p)?.id?.let { viewModel.prefetchSubmission(it) }
        }
    }

    BackHandler { onBack() }

    HorizontalPager(state = pagerState) { page ->
        val currentPostId = effectiveSubmissions.getOrNull(page)?.id ?: initialPostId

        LaunchedEffect(currentPostId) {
            viewModel.loadSubmission(currentPostId)
        }

        val submission = submissionDetailsMap[currentPostId]
        // 列表条目携带的真实缩略图 URL（卡片已加载，可作详情页占位）
        val listThumbnailUrl = effectiveSubmissions.getOrNull(page)
            ?.thumbnailUrl?.takeIf { it.isNotEmpty() }
        val hazeState = remember { HazeState() }
        val scrollState = rememberScrollState()
        // 评论图标跳转评论区：滚动容器与 Tab 行的窗口坐标持续采集，
        // 点击时换算出 Tab 行在内容里的偏移再滚动（单栏/双栏各自成立）
        val pagerScope = rememberCoroutineScope()
        var scrollRootY by remember { mutableFloatStateOf(0f) }
        var tabsAnchorY by remember { mutableFloatStateOf(Float.NaN) }
        var showZoom by remember { mutableStateOf(false) }
        var showDocumentViewer by remember { mutableStateOf(false) }
        var showPdfViewer by remember { mutableStateOf(false) }
        // 顶栏开关：本页关闭/开启标题翻译（不回写全局设置）
        var titleTranslateHidden by remember { mutableStateOf(false) }
        val downloadTask by DownloadManager.tasks.collectAsState()
        val myDownloadTask = downloadTask.lastOrNull { it.submissionId == currentPostId }
        var downloadCompletedRecently by remember { mutableStateOf(false) }

        // 勾选反馈与真实结果挂钩：任务完成亮 4 秒，失败显示可重试的错误态
        LaunchedEffect(myDownloadTask?.id, myDownloadTask?.status) {
            if (myDownloadTask?.status == DownloadStatus.COMPLETED) {
                downloadCompletedRecently = true
                kotlinx.coroutines.delay(4000)
                downloadCompletedRecently = false
            }
        }

        // 点空白处切换底栏显隐：tap 不被子级 clickable 消费时冒泡到这里
        // （图片/按钮/卡片等可点元素不受影响），沉浸式看图再点一下唤回
        var bottomBarVisible by remember { mutableStateOf(true) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .pointerInput(Unit) {
                    detectTapGestures { bottomBarVisible = !bottomBarVisible }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
            ) {

                if (isLoading && submission == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (submission != null) {
                    val sub = submission
                    // 信息栏提为局部 Composable lambda，单栏/双栏共用同一份内容；
                    // 编译器会包成 rememberComposableLambda，内部 remember 状态稳定。
                    val detailBody: @Composable (Modifier) -> Unit = { bodyModifier ->
                        Column(modifier = bodyModifier) {
                            Column(
                                modifier = Modifier.padding(horizontal = 20.dp)
                            ) {
                            // Tab state lives up here so the stats bar can jump
                            // straight to the Comments tab.
                            var selectedTab by remember { mutableIntStateOf(0) }

                            Spacer(modifier = Modifier.height(16.dp))

                            // ── Title（双语翻译：原文恒显，译文按类别显示方式跟进；
                            //    顶栏开关可整页关闭本页标题翻译）──
                            if (sub.title.isNotBlank()) {
                                val titleEntry = rememberTranslationEntry(
                                    key = sub.id,
                                    text = sub.title,
                                    category = TranslationCategory.TITLE,
                                    singleRequest = true
                                )
                                if (!titleTranslateHidden) {
                                    TranslatableAutoEffect(titleEntry, translationPrefs)
                                }
                                val titleState by titleEntry.state.collectAsStateWithLifecycle()
                                val titleSuccess = titleState as? TranslationUiState.Success
                                val titleMode = translationPrefs.displayMode(TranslationCategory.TITLE)
                                val titleTranslationVisible = !titleTranslateHidden &&
                                    titleSuccess != null && titleSuccess.showing
                                Row(verticalAlignment = Alignment.Top) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (titleTranslationVisible &&
                                                titleMode == TranslationDisplayMode.TRANSLATION_ONLY
                                            ) {
                                                titleSuccess.translated
                                            } else {
                                                sub.title
                                            },
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        if (titleTranslationVisible &&
                                            titleMode == TranslationDisplayMode.BILINGUAL &&
                                            titleSuccess.translated.isNotBlank() &&
                                            titleSuccess.translated != sub.title
                                        ) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = titleSuccess.translated,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Color.White.copy(alpha = 0.72f)
                                            )
                                        }
                                    }
                                    if (translationPrefs.enabled && !titleTranslateHidden) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        TranslationIconButton(entry = titleEntry, enabled = true)
                                    }
                                }
                            }

                            // Accent underline
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .width(48.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Cyan, Pink)
                                        )
                                    )
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // ── Author card ─────────────────────────────────
                            if (sub.author.isNotEmpty()) {
                                AuthorCard(
                                    sub = sub,
                                    onAuthorClick = onAuthorClick
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // ── Info chips ──────────────────────────────────
                            InfoChipsRow(sub)

                            // ── Folders（作品所属画廊文件夹，可直达文件夹浏览）──
                            if (sub.folders.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                FoldersSection(folders = sub.folders, onOpenFolder = onOpenFolder)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // ── Tabs index plan（小说正文存在时插入为首 tab）──
                            // 评论恒为末位：统计栏跳评论区用 lastIndex，勿再硬编码下标
                            val hasStory = sub.storyText.isNotBlank()
                            val tabs = buildList {
                                if (hasStory) add(stringResource(R.string.post_story))
                                add(stringResource(R.string.post_description))
                                add(stringResource(R.string.post_meta_info))
                                add("${stringResource(R.string.post_comments)} (${sub.commentList.size})")
                            }
                            val tabIndexDescription = if (hasStory) 1 else 0
                            val tabIndexMeta = if (hasStory) 2 else 1
                            val tabIndexComments = tabs.lastIndex

                            // ── Stats bar ───────────────────────────────────
                            val commentJumpMarginPx = with(LocalDensity.current) { 12.dp.toPx() }
                            StatsRow(
                                sub = sub,
                                onToggleFavorite = { viewModel.toggleFavorite(currentPostId) },
                                onCommentsClick = {
                                    selectedTab = tabIndexComments
                                    pagerScope.launch {
                                        if (!tabsAnchorY.isNaN()) {
                                            // Tab 行内容偏移 = (窗口坐标差) + 已滚动距离；留一点余量
                                            val target = (tabsAnchorY - scrollRootY + scrollState.value - commentJumpMarginPx).roundToInt()
                                            scrollState.animateScrollTo(target.coerceAtLeast(0))
                                        }
                                    }
                                }
                            )

                            // ── Date ────────────────────────────────────────
                            if (sub.date.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = MutedText,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = sub.date,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MutedText
                                    )
                                }
                            }

                            // ── Tags ────────────────────────────────────────
                            if (sub.tags.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(24.dp))
                                CompositionLocalProvider(
                                    LocalTagTranslationEnabled provides
                                        tagTranslateToggle.takeIf { translationPrefs.enabled }
                                ) {
                                    TagsSection(
                                        tags = sub.tags,
                                        onTagClick = onTagClick,
                                        onTagAppend = onTagAppend,
                                        onTagSavePreset = {
                                            // 标签长按保存：查询词固定为该标签组，沿用当前筛选快照
                                            viewModel.addSearchPreset(
                                                "", "@keywords $it", viewModel.searchFilters.value
                                            )
                                        }
                                    )
                                }
                            }

                            // ── Author gallery（宽屏右栏专属：当前作品前后的作者作品）──
                            if (twoPane && sub.author.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(24.dp))
                                AuthorGallerySection(
                                    username = sub.authorUsername.ifEmpty { sub.author },
                                    currentPostId = currentPostId,
                                    cardStats = cardStats,
                                    onNavigateToPost = onNavigateToPost,
                                    onWarmStats = { viewModel.prefetchSubmissionStats(it) }
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // ── Tabs: [Story] / Description / Meta / Comments ──
                            CustomTabRow(
                                tabs = tabs,
                                selectedTab = selectedTab,
                                onTabSelected = { selectedTab = it },
                                modifier = Modifier.onGloballyPositioned { tabsAnchorY = it.positionInRoot().y }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            when {
                                hasStory && selectedTab == 0 -> StoryReader(sub = sub)
                                selectedTab == tabIndexDescription -> DescriptionContent(
                                    sub = sub,
                                    onAuthorClick = onAuthorClick,
                                    onNavigateToPost = onNavigateToPost
                                )
                                selectedTab == tabIndexMeta -> MetaInfoContent(sub)
                                else -> CommentsContent(
                                    comments = sub.commentList,
                                    onAuthorClick = onAuthorClick
                                )
                            }

                            Spacer(modifier = Modifier.height(150.dp))
                            }
                        }
                    }
                    if (twoPane) {
                        // 宽屏：左侧大图占比可在设置里调（默认 65%），右侧信息栏独立滚动
                        Row(modifier = Modifier.weight(1f)) {
                            TwoPaneHeroImage(
                                sub = sub,
                                placeholderUrl = listThumbnailUrl,
                                modifier = Modifier
                                    .weight(detailSplit.toFloat())
                                    .fillMaxHeight(),
                                onImageClick = { showZoom = true },
                                onOpenDocumentViewer = { showDocumentViewer = true },
                                onOpenPdfViewer = { showPdfViewer = true }
                            )
                            detailBody(
                                Modifier
                                    .weight((100 - detailSplit).toFloat())
                                    .verticalScroll(scrollState)
                                    .onGloballyPositioned { scrollRootY = it.positionInRoot().y }
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(scrollState)
                                .onGloballyPositioned { scrollRootY = it.positionInRoot().y }
                        ) {
                            // ── Hero image ──────────────────────────────────────
                            HeroImage(
                                sub,
                                placeholderUrl = listThumbnailUrl,
                                onImageClick = { showZoom = true },
                                onOpenDocumentViewer = { showDocumentViewer = true },
                                onOpenPdfViewer = { showPdfViewer = true }
                            )
                            detailBody(Modifier)
                        }
                    }
                }
            }

            // Back arrow top-left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 48.dp, start = 16.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = Color.White
                )
            }

            // 顶栏右上角：关闭/开启标题翻译（本页生效；翻译功能未启用时不显示）
            if (translationPrefs.enabled) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = 16.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { titleTranslateHidden = !titleTranslateHidden },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = stringResource(
                            if (titleTranslateHidden) R.string.title_translate_enable
                            else R.string.title_translate_disable
                        ),
                        tint = if (titleTranslateHidden) Color.White.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Bottom fade
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF0A0A0C).copy(alpha = 0.7f),
                                Color(0xFF0A0A0C).copy(alpha = 0.95f)
                            )
                        )
                    )
            )

            // Glass action bar
            val shareViaText = stringResource(R.string.share_via)
            val downloadFailed = myDownloadTask?.status == DownloadStatus.FAILED
            val downloadBusy = myDownloadTask?.status == DownloadStatus.QUEUED ||
                myDownloadTask?.status == DownloadStatus.RUNNING
            val downloadIcon = when {
                downloadCompletedRecently -> Icons.Default.Check
                downloadFailed -> Icons.Default.ErrorOutline
                else -> Icons.Default.Download
            }
            val bottomBarItems = listOf(
                GlassActionItem(androidx.compose.ui.res.stringResource(id = R.string.action_back), Icons.AutoMirrored.Filled.ArrowBack, Color(0xFF00E5FF)),
                GlassActionItem(androidx.compose.ui.res.stringResource(id = R.string.cd_share), Icons.Default.Share, Color(0xFFFFD700)),
                GlassActionItem(
                    title = androidx.compose.ui.res.stringResource(id = R.string.action_favorite),
                    icon = if (submission?.isFavorited == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    color = Color(0xFFFF007F),
                    isActive = submission?.isFavorited == true
                ),
                GlassActionItem(
                    title = androidx.compose.ui.res.stringResource(id = R.string.cd_download),
                    icon = downloadIcon,
                    color = if (downloadFailed) Color(0xFFFF5252) else Color(0xFF4CD964),
                    isActive = downloadCompletedRecently || downloadBusy
                )
            )

            AnimatedVisibility(
                visible = bottomBarVisible,
                enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { it },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    GlassActionBar(
                        items = bottomBarItems,
                        hazeState = hazeState,
                        onItemClick = { index ->
                            val sub = submission ?: return@GlassActionBar
                            when (index) {
                                0 -> onBack()
                                1 -> {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "https://www.furaffinity.net/view/${currentPostId}/")
                                        putExtra(Intent.EXTRA_SUBJECT, sub.title)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, shareViaText))
                                }
                                2 -> {
                                    viewModel.toggleFavorite(currentPostId)
                                }
                                3 -> {
                                    // 下载方式决定实际 URL（复制展示文件/下载原图），任务已按
                                    // submissionId 归属本页；失败任务直接重试，不比 URL
                                    if (sub.fullImageUrl.isNotEmpty() || sub.fileUrl.isNotEmpty()) {
                                        val failed = myDownloadTask?.takeIf { it.status == DownloadStatus.FAILED }
                                        when {
                                            failed != null -> DownloadManager.retry(failed.id)
                                            !downloadBusy -> viewModel.downloadImage(sub)
                                            else -> Unit
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }

            // Full-screen viewers: Flash → Ruffle WebView；GIF → 逐帧播放器；其余 → 缩放查看器；
            // 音频/PDF/文档类在主媒体区内处理，无全屏图片查看器
            val zoomSubmission = submission
            if (showZoom && zoomSubmission?.fullImageUrl?.isNotEmpty() == true &&
                !isAudioSubmission(zoomSubmission) && !isPdfSubmission(zoomSubmission) &&
                !isStoreDocumentSubmission(zoomSubmission)
            ) {
                when {
                    zoomSubmission.fileExt == "swf" || zoomSubmission.fileType.equals("Flash", true) -> {
                        FlashPlayerViewer(
                            swfUrl = zoomSubmission.fileUrl.ifEmpty { zoomSubmission.fullImageUrl },
                            pageUrl = "https://www.furaffinity.net/view/$currentPostId/",
                            onClose = { showZoom = false }
                        )
                    }
                    zoomSubmission.fileExt == "gif" ||
                        zoomSubmission.fullImageUrl.endsWith(".gif", ignoreCase = true) -> {
                        // 逐帧播放需要真正的 GIF 字节：内联图不是 .gif 时退回下载链接
                        val gifUrl = when {
                            zoomSubmission.fullImageUrl.endsWith(".gif", ignoreCase = true) ->
                                zoomSubmission.fullImageUrl
                            zoomSubmission.fileUrl.endsWith(".gif", ignoreCase = true) ->
                                zoomSubmission.fileUrl
                            else -> zoomSubmission.fullImageUrl
                        }
                        GifFrameViewer(
                            imageUrl = gifUrl,
                            submissionId = currentPostId,
                            onClose = { showZoom = false }
                        )
                    }
                    else -> {
                        ZoomableImageViewer(
                            imageUrl = zoomSubmission.fullImageUrl,
                            onClose = { showZoom = false }
                        )
                    }
                }
            }

            // 全屏文档查看器（txt/docx/odt/rtf）：覆盖详情页一切层级
            val docSubmission = submission
            if (showDocumentViewer && docSubmission != null && isStoreDocumentSubmission(docSubmission)) {
                DocumentViewerScreen(
                    title = docSubmission.title,
                    fileUrl = docSubmission.fileUrl.ifEmpty { docSubmission.fullImageUrl },
                    initialText = docSubmission.storyText,
                    onClose = { showDocumentViewer = false }
                )
            }

            // 全屏 PDF 查看器（缩放/跳页/持久缓存）：覆盖详情页一切层级
            val pdfSubmission = submission
            if (showPdfViewer && pdfSubmission != null && isPdfSubmission(pdfSubmission)) {
                PdfViewerScreen(
                    pdfUrl = pdfSubmission.fileUrl.ifEmpty { pdfSubmission.fullImageUrl },
                    title = pdfSubmission.title,
                    onClose = { showPdfViewer = false }
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// Two-pane hero: fills the left pane's full height, scales to fit (no unbounded height)
// -----------------------------------------------------------------------------------------

@Composable
private fun TwoPaneHeroImage(
    sub: Submission,
    placeholderUrl: String?,
    modifier: Modifier = Modifier,
    onImageClick: () -> Unit = {},
    onOpenDocumentViewer: () -> Unit = {},
    onOpenPdfViewer: () -> Unit = {}
) {
    // Flash：海报 + 播放按钮，点击进 Ruffle 全屏层
    if (sub.fileExt == "swf" || sub.fileType.equals("Flash", true)) {
        FlashHeroContent(
            posterUrl = sub.fullImageUrl.ifEmpty { placeholderUrl ?: "" },
            flashWidth = sub.flashWidth,
            flashHeight = sub.flashHeight,
            modifier = modifier,
            onPlayClick = onImageClick
        )
        return
    }
    // Music/音频：主媒体区内嵌播放器（fullImageUrl 的兜底值是音频直链，不能当图渲染）
    if (isAudioSubmission(sub)) {
        AudioHeroContent(
            audioUrl = sub.fileUrl.ifEmpty { sub.fullImageUrl },
            coverUrl = placeholderUrl,
            modifier = modifier
        )
        return
    }
    // PDF：同其它 store 文档一致——不自动载入原件，封面 + 「阅读文档」进全屏 PDF 查看器
    if (isPdfSubmission(sub)) {
        DocumentHeroContent(
            fileName = sub.fileName.ifEmpty { sub.title },
            fileSize = sub.fileSize,
            coverUrl = docCoverUrl(sub, placeholderUrl),
            onOpenViewer = onOpenPdfViewer,
            badgeLabel = "PDF",
            badgeIcon = Icons.Default.PictureAsPdf,
            modifier = modifier
        )
        return
    }
    // 文档类（doc/docx/rtf/odt/txt）：元信息卡 + 全屏文档查看器入口
    if (isStoreDocumentSubmission(sub)) {
        DocumentHeroContent(
            fileName = sub.fileName.ifEmpty { sub.title },
            fileSize = sub.fileSize,
            coverUrl = docCoverUrl(sub, placeholderUrl),
            onOpenViewer = onOpenDocumentViewer,
            modifier = modifier
        )
        return
    }
    Box(modifier = modifier.background(DarkBackground)) {
        if (sub.fullImageUrl.isNotEmpty()) {
            ProgressiveImageContent(
                fullUrl = sub.fullImageUrl,
                listThumbnailUrl = placeholderUrl,
                modifier = Modifier.fillMaxSize(),
                contentDescription = sub.title,
                contentScale = ContentScale.Fit,
                ratio = sub.thumbnailWidthOnHeightRatio,
                fixedSized = true,
                heroKey = sub.id,
                onImageClick = onImageClick
            )

            // Rating badge overlaid on image
            if (sub.rating.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .padding(14.dp)
                        .align(Alignment.TopStart),
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        sub.rating.contains("General", true) -> Rating_General.copy(alpha = 0.9f)
                        sub.rating.contains("Mature", true) -> Rating_Mature.copy(alpha = 0.9f)
                        sub.rating.contains("Adult", true) -> Rating_Adult.copy(alpha = 0.9f)
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    }
                ) {
                    Text(
                        text = sub.rating,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Zoom hint — same affordance as the single-pane hero
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = onImageClick)
                    .padding(8.dp)
            ) {
                Icon(
                    Icons.Default.ZoomIn,
                    contentDescription = stringResource(R.string.cd_zoom_artwork),
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// Hero image with gradient fade + rating badge
// -----------------------------------------------------------------------------------------

/** 音频作品（FA Music/Podcast 类）：详情页以 <audio> 直链渲染，必须走播放器而非图片渲染。 */
private val AUDIO_EXTS = setOf("mp3", "m4a", "ogg", "wav", "flac", "aac", "mid", "midi")

private fun isAudioSubmission(sub: Submission): Boolean =
    sub.fileExt.lowercase() in AUDIO_EXTS ||
        sub.fileType.equals("Music", ignoreCase = true) ||
        sub.fileType.equals("Podcast", ignoreCase = true)

/** PDF 文档（FA Story/Poetry 类可上传 pdf 原件）：与其它文档类同待遇，不自动载入。 */
private fun isPdfSubmission(sub: Submission): Boolean =
    sub.fileExt.equals("pdf", ignoreCase = true)

/** 无系统渲染器的文档类型：交给下载 + 系统应用打开。 */
private val DOCUMENT_EXTS = setOf("doc", "docx", "rtf", "odt")

private fun isDocumentSubmission(sub: Submission): Boolean =
    sub.fileExt.lowercase() in DOCUMENT_EXTS

/** 文档/store 类全集：文档类型 + 纯文本（.txt 正文走阅读器，绝不能进图片解码器）。 */
private val TEXT_DOC_EXTS = setOf("txt")

private fun isStoreDocumentSubmission(sub: Submission): Boolean =
    sub.fileExt.lowercase() in TEXT_DOC_EXTS || isDocumentSubmission(sub)

/**
 * 文档类封面：列表携带的缩略图优先；直链打开时用详情解析出的文档封面
 * 缩略图（FA 把 .submission-writing 里的封面配给 thumbnailUrl）。
 * thumbnailUrl 的兜底值可能是原件直链（PDF/RTF 字节，不能当图渲染），需排除。
 */
private fun docCoverUrl(sub: Submission, listThumbnailUrl: String?): String? =
    listThumbnailUrl?.takeIf { it.isNotEmpty() }
        ?: sub.thumbnailUrl.takeIf {
            it.isNotEmpty() && !it.equals(sub.fileUrl, ignoreCase = true)
        }

@Composable
private fun HeroImage(
    sub: Submission,
    placeholderUrl: String?,
    onImageClick: () -> Unit = {},
    onOpenDocumentViewer: () -> Unit = {},
    onOpenPdfViewer: () -> Unit = {}
) {
    // Flash：海报 + 播放按钮，点击进 Ruffle 全屏层
    if (sub.fileExt == "swf" || sub.fileType.equals("Flash", true)) {
        FlashHeroContent(
            posterUrl = sub.fullImageUrl.ifEmpty { placeholderUrl ?: "" },
            flashWidth = sub.flashWidth,
            flashHeight = sub.flashHeight,
            modifier = Modifier.fillMaxWidth(),
            onPlayClick = onImageClick
        )
        return
    }
    // Music/音频：主媒体区内嵌播放器（fullImageUrl 的兜底值是音频直链，不能当图渲染）
    if (isAudioSubmission(sub)) {
        AudioHeroContent(
            audioUrl = sub.fileUrl.ifEmpty { sub.fullImageUrl },
            coverUrl = placeholderUrl,
            modifier = Modifier.fillMaxWidth()
        )
        return
    }
    // PDF：同其它 store 文档一致——不自动载入原件，封面 + 「阅读文档」进全屏 PDF 查看器
    if (isPdfSubmission(sub)) {
        DocumentHeroContent(
            fileName = sub.fileName.ifEmpty { sub.title },
            fileSize = sub.fileSize,
            coverUrl = docCoverUrl(sub, placeholderUrl),
            onOpenViewer = onOpenPdfViewer,
            badgeLabel = "PDF",
            badgeIcon = Icons.Default.PictureAsPdf,
            modifier = Modifier.fillMaxWidth()
        )
        return
    }
    // 文档类（doc/docx/rtf/odt/txt）：元信息卡 + 全屏文档查看器入口
    if (isStoreDocumentSubmission(sub)) {
        DocumentHeroContent(
            fileName = sub.fileName.ifEmpty { sub.title },
            fileSize = sub.fileSize,
            coverUrl = docCoverUrl(sub, placeholderUrl),
            onOpenViewer = onOpenDocumentViewer,
            modifier = Modifier.fillMaxWidth()
        )
        return
    }
    if (sub.fullImageUrl.isNotEmpty()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            val knownRatio = sub.thumbnailWidthOnHeightRatio > 0f
            // ratio 已知时容器预设真实比例，加载前后高度零跳变
            val ratioModifier = if (knownRatio) {
                Modifier.aspectRatio(sub.thumbnailWidthOnHeightRatio.coerceIn(0.45f, 2.2f))
            } else {
                Modifier
            }
            // 原图就位前不压渐变遮罩：占位缩略图保持干净（用户明确要求移除加载期阴影）
            var fullImageLoaded by remember { mutableStateOf(false) }
            ProgressiveImageContent(
                fullUrl = sub.fullImageUrl,
                listThumbnailUrl = placeholderUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(ratioModifier)
                    .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)),
                contentDescription = sub.title,
                contentScale = if (knownRatio) ContentScale.Crop else ContentScale.FillWidth,
                ratio = sub.thumbnailWidthOnHeightRatio,
                fixedSized = knownRatio,
                heroKey = sub.id,
                onImageClick = onImageClick,
                onImageSuccess = { fullImageLoaded = true }
            )

            // Gradient fade into background at the bottom
            if (fullImageLoaded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    DarkBackground.copy(alpha = 0.4f),
                                    DarkBackground
                                )
                            )
                        )
                )
            }

            // Rating badge overlaid on image
            if (sub.rating.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .padding(14.dp)
                        .align(Alignment.TopStart),
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        sub.rating.contains("General", true) -> Rating_General.copy(alpha = 0.9f)
                        sub.rating.contains("Mature", true) -> Rating_Mature.copy(alpha = 0.9f)
                        sub.rating.contains("Adult", true) -> Rating_Adult.copy(alpha = 0.9f)
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    }
                ) {
                    Text(
                        text = sub.rating,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Zoom hint — translucent chip bottom-end, signals the image opens fullscreen
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = onImageClick)
                    .padding(8.dp)
            ) {
                Icon(
                    Icons.Default.ZoomIn,
                    contentDescription = stringResource(R.string.cd_zoom_artwork),
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// Flash hero: static poster + play affordance — tapping opens the Ruffle fullscreen layer
// -----------------------------------------------------------------------------------------

@Composable
private fun FlashHeroContent(
    posterUrl: String,
    flashWidth: Int,
    flashHeight: Int,
    modifier: Modifier = Modifier,
    onPlayClick: () -> Unit
) {
    // 画布比例来自 FA object#flash_embed 的 data-width/height；缺省 550×400（SWF 常见）
    val ratio = if (flashWidth > 0 && flashHeight > 0) {
        flashWidth.toFloat() / flashHeight.toFloat()
    } else {
        550f / 400f
    }
    Box(
        modifier = modifier
            .aspectRatio(ratio.coerceIn(0.45f, 2.2f))
            .clickable(onClick = onPlayClick),
        contentAlignment = Alignment.Center
    ) {
        if (posterUrl.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(posterUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                imageLoader = ImageLoaderFactory.getInstance(LocalContext.current),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(DarkSurfaceVariant))
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.flash_play),
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
        Text(
            // 格式名为内容数据（同卡片徽标），不进 i18n 资源体系
            text = "Flash" + if (flashWidth > 0 && flashHeight > 0) " · ${flashWidth}×${flashHeight}" else "",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
        )
    }
}

// -----------------------------------------------------------------------------------------
// Author card with neon ring avatar
// -----------------------------------------------------------------------------------------

@Composable
private fun AuthorCard(
    sub: Submission,
    onAuthorClick: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AuthorCardBackground)
            .border(1.dp, AuthorCardBorder, RoundedCornerShape(18.dp))
            .clickable { onAuthorClick(sub.authorUsername.ifEmpty { sub.author }) }
            .padding(14.dp)
    ) {
        // Avatar with gradient ring
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            // Neon gradient ring
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(listOf(Cyan, Pink)),
                        shape = CircleShape
                    )
            )
            if (sub.authorAvatar.isNotEmpty()) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(sub.authorAvatar)
                        .crossfade(true)
                        .build(),
                    imageLoader = ImageLoaderFactory.getInstance(LocalContext.current),
                    contentDescription = sub.author,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant),
                    error = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = SubtleGray,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = SubtleGray,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sub.author,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.post_view_profile),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = stringResource(R.string.post_view_profile),
            tint = SubtleGray,
            modifier = Modifier.size(22.dp)
        )
    }
}

// -----------------------------------------------------------------------------------------
// Info chips: rating, category, species, gender — icon-based with colored accents
// -----------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoChipsRow(sub: Submission) {
    val hasInfo = sub.rating.isNotEmpty() || sub.category.isNotEmpty() ||
            sub.species.isNotEmpty() || sub.gender.isNotEmpty()
    if (!hasInfo) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (sub.rating.isNotEmpty()) {
            val ratingColor = when {
                sub.rating.contains("General", true) -> Rating_General
                sub.rating.contains("Mature", true) -> Rating_Mature
                sub.rating.contains("Adult", true) -> Rating_Adult
                else -> MaterialTheme.colorScheme.primary
            }
            InfoChipCard(Icons.Default.Shield, stringResource(R.string.filter_rating), sub.rating, ratingColor)
        }
        if (sub.category.isNotEmpty()) InfoChipCard(Icons.Default.Folder, stringResource(R.string.meta_category), sub.category, Cyan)
        if (sub.species.isNotEmpty()) InfoChipCard(Icons.Default.Pets, stringResource(R.string.meta_species), sub.species, Green)
        if (sub.gender.isNotEmpty()) InfoChipCard(Icons.Default.Person, stringResource(R.string.meta_gender), sub.gender, Gold)
    }
}

@Composable
private fun InfoChipCard(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ChipBackground)
            .border(1.dp, ChipBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = label,
                fontSize = 10.sp,
                color = SubtleGray
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// -----------------------------------------------------------------------------------------
// Folders section（作品所属画廊文件夹）：FA sidebar-lower 同构呈现，点击直达文件夹浏览
// -----------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoldersSection(
    folders: List<GalleryFolder>,
    onOpenFolder: (GalleryFolder) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = Cyan,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.post_folders),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            folders.forEach { folder ->
                val label = if (folder.group.isEmpty()) folder.name
                else "${folder.group} - ${folder.name}"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(ChipBackground)
                        .border(1.dp, ChipBorder, RoundedCornerShape(14.dp))
                        .clickable { onOpenFolder(folder) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = Cyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (folder.count > 0) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = formatCount(folder.count),
                            fontSize = 11.sp,
                            color = SubtleGray
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// Stats bar: views / favorites / comments — colored, tappable favorite
// -----------------------------------------------------------------------------------------

@Composable
private fun StatsRow(
    sub: Submission,
    onToggleFavorite: () -> Unit,
    onCommentsClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatBarItem(
            icon = Icons.Default.Visibility,
            value = formatCount(sub.views),
            color = Cyan
        )
        StatDivider()
        StatBarItem(
            icon = if (sub.isFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            value = formatCount(sub.favorites),
            color = if (sub.isFavorited) Pink else SubtleGray,
            onClick = onToggleFavorite
        )
        StatDivider()
        StatBarItem(
            icon = Icons.Default.ChatBubbleOutline,
            value = formatCount(sub.comments),
            color = Gold,
            onClick = onCommentsClick
        )
    }
}

@Composable
private fun StatBarItem(
    icon: ImageVector,
    value: String,
    color: Color,
    onClick: (() -> Unit)? = null
) {
    val modifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else Modifier

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = color
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(24.dp)
            .background(StatsDivider)
    )
}

// -----------------------------------------------------------------------------------------
// Tags section
// -----------------------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun TagsSection(
    tags: List<String>,
    onTagClick: (String) -> Unit,
    onTagAppend: (String) -> Unit,
    onTagSavePreset: (String) -> Unit
) {
    val prefs = rememberTranslationPrefs()
    val tagToggle = LocalTagTranslationEnabled.current
    // 页面开关存在时完全接管；否则读全局 translateTagsAuto（蓝本两层开关）
    val auto = tagToggle?.value ?: prefs.tagsAuto
    val entries = remember(tags) {
        tags.map { TranslationEntry(it, TranslationCategory.TAG, singleRequest = true) }
    }
    DisposableEffect(entries) {
        onDispose { entries.forEach { it.cancel() } }
    }

    // 批量翻译靠缓存 + in-flight 合并；键含 prefs.enabled：偏好晚到（首帧默认值）时补触发；
    // 页面开关再次打开时对失败条目重试
    LaunchedEffect(prefs.enabled, auto) {
        if (prefs.enabled && auto) {
            entries.forEach { entry ->
                val s = entry.state.value
                if (s is TranslationUiState.Idle && !s.autoAttempted || s is TranslationUiState.Failed) {
                    entry.translate()
                }
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            Icons.Default.LocalOffer,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.post_tags),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Spacer(modifier = Modifier.weight(1f))
        if (tagToggle != null) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable { tagToggle.value = !tagToggle.value },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Translate,
                    contentDescription = stringResource(R.string.settings_translation),
                    tint = if (tagToggle.value) MaterialTheme.colorScheme.primary else SubtleGray,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val mode = prefs.displayMode(TranslationCategory.TAG)
        tags.forEachIndexed { index, tag ->
            val entry = entries[index]
            val chipState by entry.state.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val clipboard = LocalClipboard.current
            val copyScope = rememberCoroutineScope()
            val haptic = LocalHapticFeedback.current
            val copiedToast = stringResource(R.string.toast_tag_copied)
            val savedToast = stringResource(R.string.toast_preset_saved)
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ChipBackground)
                        .border(1.dp, ChipBorder, RoundedCornerShape(10.dp))
                        .combinedClickable(
                            onClick = { onTagClick(tag) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuOpen = true
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                // loading 用静态 "…" 而非 spinner（几十个 chip 同时转圈会卡列表）
                // auto 关闭时即使已有 Success 缓存也回落原文——开关要能收起双语展示
                when (val s = chipState) {
                    is TranslationUiState.Success -> when {
                        !auto -> Text(text = "#$tag", fontSize = 12.sp, color = DarkOnSurface)
                        mode == TranslationDisplayMode.BILINGUAL -> Column {
                            Text(text = "#$tag", fontSize = 12.sp, color = DarkOnSurface)
                            Text(text = s.translated, fontSize = 10.sp, color = MutedText)
                        }
                        else ->
                            Text(text = s.translated, fontSize = 12.sp, color = DarkOnSurface)
                    }
                    is TranslationUiState.Loading ->
                        Text(text = "#$tag…", fontSize = 12.sp, color = DarkOnSurface)
                    is TranslationUiState.Failed -> if (!auto) {
                        Text(text = "#$tag", fontSize = 12.sp, color = DarkOnSurface)
                    } else {
                        Column {
                            Text(text = "#$tag", fontSize = 12.sp, color = DarkOnSurface)
                            Text(
                                text = translationErrorBase(s.message, s.code),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    else -> Text(text = "#$tag", fontSize = 12.sp, color = DarkOnSurface)
                }
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tag_action_search)) },
                        onClick = {
                            menuOpen = false
                            onTagClick(tag)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tag_action_append)) },
                        onClick = {
                            menuOpen = false
                            onTagAppend(tag)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tag_action_save_preset)) },
                        onClick = {
                            menuOpen = false
                            onTagSavePreset(tag)
                            Toast.makeText(context, savedToast, Toast.LENGTH_SHORT).show()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tag_action_copy)) },
                        onClick = {
                            menuOpen = false
                            copyScope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("furflix_tag", tag)))
                            }
                            Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// Author gallery (two-pane info column): a strip of the author's works around the
// current submission. Pages are fetched via the author gallery listing (which already
// applies the NSFW/muted-words filters); loading stops at the current post or a small
// cap, then extends on demand as the strip is scrolled. Unknown positions start at
// the author's latest works.
// -----------------------------------------------------------------------------------------

private const val AUTHOR_GALLERY_INITIAL_PAGES = 4
private const val AUTHOR_GALLERY_MAX_PAGES = 30

@Composable
private fun AuthorGallerySection(
    username: String,
    currentPostId: String,
    cardStats: Map<String, com.furflix.app.data.model.CardStats>,
    onNavigateToPost: (String) -> Unit,
    onWarmStats: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { FurRepository.getInstance(context) }
    val quality = LocalThumbnailQuality.current
    val listState = rememberLazyListState()

    var galleryItems by remember(username) { mutableStateOf<List<Submission>>(emptyList()) }
    var loading by remember(username) { mutableStateOf(true) }
    var nextPage by remember(username) { mutableIntStateOf(1) }
    var exhausted by remember(username) { mutableStateOf(false) }
    var extending by remember(username) { mutableStateOf(false) }
    var currentIsListed by remember(username) { mutableStateOf(false) }

    // 初次加载：首页先出图，随后顺序翻页定位当前作品；上限内找不到就从最新排起
    LaunchedEffect(username) {
        val collected = mutableListOf<Submission>()
        var found = false
        var page = 1
        while (page <= AUTHOR_GALLERY_INITIAL_PAGES && !found) {
            val batch = runCatching { repository.getUserGallery(username, page) }
                .getOrDefault(emptyList())
            if (batch.isEmpty()) {
                exhausted = true
                break
            }
            collected.addAll(batch)
            galleryItems = collected.toList()
            found = collected.any { it.id == currentPostId }
            page++
        }
        loading = false
        currentIsListed = found
        nextPage = page
    }

    // 条带滚到尾端继续向后翻页（可连续浏览）；key 含 loading/exhausted，
    // 初次加载结束、监听尚未挂载时也要（重新）挂上
    LaunchedEffect(galleryItems, loading, exhausted) {
        if (loading || exhausted) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                if (last >= galleryItems.size - 4 && !extending && !exhausted) {
                    extending = true
                    val batch = runCatching { repository.getUserGallery(username, nextPage) }
                        .getOrDefault(emptyList())
                    if (batch.isEmpty() || nextPage >= AUTHOR_GALLERY_MAX_PAGES) {
                        exhausted = true
                    } else {
                        galleryItems = galleryItems + batch
                        nextPage++
                    }
                    extending = false
                }
            }
    }

    // 可见块统计预热：浮层不限于本地看过的帖子——滚到视野里的块就静默抓
    // 详情回填统计（VM 侧串行限速、按 id 去重）
    val warmedIds = remember { mutableSetOf<String>() }
    LaunchedEffect(galleryItems) {
        if (galleryItems.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .mapNotNull { galleryItems.getOrNull(it.index)?.id }
        }.collect { visibleIds ->
            val fresh = visibleIds.filter { warmedIds.add(it) }
            if (fresh.isNotEmpty()) onWarmStats(fresh)
        }
    }

    // 命中当前作品时把它放到条带偏左位置，左右都能看到"前后"
    LaunchedEffect(currentIsListed, galleryItems.size) {
        if (currentIsListed && galleryItems.isNotEmpty()) {
            val idx = galleryItems.indexOfFirst { it.id == currentPostId }
            if (idx > 2) listState.scrollToItem(idx - 2)
        }
    }

    if (loading || galleryItems.isNotEmpty()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Default.Collections,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.post_author_gallery),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (loading && galleryItems.isEmpty()) {
                    items(6) { AuthorGalleryTilePlaceholder() }
                } else {
                    items(
                        items = galleryItems,
                        key = { it.id },
                        contentType = { "author_gallery" }
                    ) { galleryItem ->
                        AuthorGalleryTile(
                            submission = galleryItem,
                            isCurrent = galleryItem.id == currentPostId,
                            stats = cardStats[galleryItem.id],
                            quality = quality,
                            onClick = { onNavigateToPost(galleryItem.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthorGalleryTile(
    submission: Submission,
    isCurrent: Boolean,
    stats: com.furflix.app.data.model.CardStats?,
    quality: Int,
    onClick: () -> Unit
) {
    val tilePx = with(LocalDensity.current) { 104.dp.toPx() }
    val thumbUrl = remember(submission.thumbnailUrl, tilePx, quality) {
        DynamicThumbnail(submission.thumbnailUrl).bestThumbnailUrl(tilePx, tilePx, quality)
    }
    Box(
        modifier = Modifier
            .size(104.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isCurrent) 2.dp else 1.dp,
                color = if (isCurrent) Pink else ChipBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(thumbUrl)
                .crossfade(200)
                .build(),
            imageLoader = ImageLoaderFactory.getInstance(LocalContext.current),
            contentDescription = submission.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        if (submission.rating.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopStart),
                shape = RoundedCornerShape(4.dp),
                color = when {
                    submission.rating.contains("General", true) -> Rating_General
                    submission.rating.contains("Mature", true) -> Rating_Mature
                    submission.rating.contains("Adult", true) -> Rating_Adult
                    else -> MaterialTheme.colorScheme.primary
                }
            ) {
                Text(
                    text = submission.rating.take(1),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }

        // 统计浮层：仅展示本地已知（详情页回填）的帖子；104dp 小块放不下
        // 三项一行，收成两行：心形+收藏在上，浏览/评论在下。已收藏心形标红。
        if (stats != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.55f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.55f)
                            )
                        )
                    )
            )
            val statStyle = MaterialTheme.typography.labelSmall.copy(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.8f),
                    offset = Offset(0f, 1f),
                    blurRadius = 4f
                )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = if (stats.favorited) Pink else Color.White.copy(alpha = 0.9f)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(formatCount(stats.favorites), style = statStyle, color = Color.White)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = Color.White.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(formatCount(stats.views), style = statStyle, color = Color.White)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ChatBubble,
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = Color.White.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(formatCount(stats.comments), style = statStyle, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthorGalleryTilePlaceholder() {
    Box(
        modifier = Modifier
            .size(104.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurfaceVariant)
    )
}

// -----------------------------------------------------------------------------------------
// Tab content: Description
// -----------------------------------------------------------------------------------------

@Composable
private fun DescriptionContent(
    sub: Submission,
    onAuthorClick: (String) -> Unit,
    onNavigateToPost: (String) -> Unit
) {
    if (sub.description.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardBackground)
                .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.post_no_description), color = MutedText, fontSize = 14.sp)
        }
        return
    }

    val prefs = rememberTranslationPrefs()
    val entry = rememberTranslationEntry(
        key = sub.id,
        text = sub.description,
        category = TranslationCategory.DESCRIPTION,
        contentIsHtml = true
    )
    val state by entry.state.collectAsStateWithLifecycle()
    val mode = prefs.displayMode(TranslationCategory.DESCRIPTION)
    TranslatableAutoEffect(entry, prefs)

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
                TranslationIconButton(entry = entry, enabled = true)
            }
        }

        val success = state as? TranslationUiState.Success
        // 仅译文模式：原地替换原文（htmlIn 服务商保留链接与排版，见 translation-plan）
        HtmlTextView(
            html = if (success != null && success.showing && mode == TranslationDisplayMode.TRANSLATION_ONLY) {
                success.translated
            } else {
                sub.description
            },
            onAuthorClick = onAuthorClick,
            onNavigateToPost = onNavigateToPost
        )

        if (success != null && success.showing && mode == TranslationDisplayMode.BILINGUAL) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(BorderDark)
            )
            Spacer(modifier = Modifier.height(12.dp))
            HtmlTextView(
                html = success.translated,
                onAuthorClick = onAuthorClick,
                onNavigateToPost = onNavigateToPost
            )
        }

        if (success != null && success.showing) {
            TranslationCaption(entry, success, mode)
        }

        val failure = state as? TranslationUiState.Failed
        if (failure != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = translationErrorText(failure),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

// HTML 渲染已抽至 ui/components/HtmlTextView.kt（详情页描述与日志正文共用）

// -----------------------------------------------------------------------------------------
// Tab content: Meta Info
// -----------------------------------------------------------------------------------------

@Composable
private fun MetaInfoContent(sub: Submission) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MetaInfoCard(stringResource(R.string.meta_category), sub.category)
        MetaInfoCard(stringResource(R.string.meta_theme), sub.theme)
        MetaInfoCard(stringResource(R.string.meta_species), sub.species)
        MetaInfoCard(stringResource(R.string.meta_gender), sub.gender)
        MetaInfoCard(stringResource(R.string.filter_rating), sub.rating)
        MetaInfoCard(stringResource(R.string.meta_resolution), sub.resolution)
        MetaInfoCard(stringResource(R.string.meta_file_size), sub.fileSize)
        MetaInfoCard(stringResource(R.string.meta_file_type), sub.fileType)
        // 文件实际后缀名（如 PNG/GIF/SWF），从下载链接/原图 URL 截取
        MetaInfoCard(stringResource(R.string.meta_file_format), sub.fileExt.uppercase())
        MetaInfoCard(stringResource(R.string.meta_date), sub.date)
    }
}

@Composable
private fun MetaInfoCard(label: String, value: String) {
    if (value.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = SubtleGray)
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// -----------------------------------------------------------------------------------------
// Tab content: Comments
// -----------------------------------------------------------------------------------------

@Composable
private fun CommentsContent(
    comments: List<Comment>,
    onAuthorClick: (String) -> Unit
) {
    if (comments.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardBackground)
                .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    tint = MutedText,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(stringResource(R.string.post_no_comments), color = MutedText, fontSize = 14.sp)
            }
        }
    } else {
        CommentsSection(comments = comments, onAuthorClick = onAuthorClick)
    }
}

// -----------------------------------------------------------------------------------------
// Helpers
// -----------------------------------------------------------------------------------------

