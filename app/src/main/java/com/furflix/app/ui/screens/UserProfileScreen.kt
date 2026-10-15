package com.furflix.app.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import com.furflix.app.R

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.furflix.app.data.model.ContactLink
import com.furflix.app.data.model.GalleryFolder
import com.furflix.app.data.model.GalleryViewMode
import com.furflix.app.data.model.UserProfile
import com.furflix.app.data.model.WatchlistUser
import com.furflix.app.data.local.UserBannerStore
import com.furflix.app.data.repository.FurRepository
import com.furflix.app.player.PlaybackController
import com.furflix.app.player.PlaylistBuilder
import com.furflix.app.ui.components.*
import com.furflix.app.ui.theme.*
import com.furflix.app.viewmodel.MainViewModel
import com.furflix.app.viewmodel.UserViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.furflix.app.translate.data.TranslationCategory
import com.furflix.app.translate.data.TranslationDisplayMode
import com.furflix.app.translate.ui.TranslatableAutoEffect
import com.furflix.app.translate.ui.TranslatableText
import com.furflix.app.translate.ui.TranslationCaption
import com.furflix.app.translate.ui.TranslationIconButton
import com.furflix.app.translate.ui.TranslationUiState
import com.furflix.app.translate.ui.rememberTranslationEntry
import com.furflix.app.translate.ui.rememberTranslationPrefs
import com.furflix.app.translate.ui.translationErrorText

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun UserProfileScreen(
    username: String,
    viewModel: UserViewModel,
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToPost: (String) -> Unit,
    onNavigateToUser: (String) -> Unit,
    onNavigateToJournal: (String) -> Unit = {}
) {
    val context = LocalContext.current

    val submissions by viewModel.submissions.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val isTogglingWatch by viewModel.isTogglingWatch.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val watchingList by viewModel.watchingList.collectAsState()
    val watchersList by viewModel.watchersList.collectAsState()
    val journals by viewModel.journals.collectAsState()

    // 多选批量（与首页画廊同一 SelectionStore/组件；source 与详情页导航的 user_ 前缀对齐）
    val selection by com.furflix.app.data.selection.SelectionStore.state.collectAsState()
    val pageSource = "user_$username"
    val selectionActive = selection.active && selection.source == pageSource
    androidx.activity.compose.BackHandler(enabled = selectionActive) {
        com.furflix.app.data.selection.SelectionStore.exit()
    }
    DisposableEffect(username) {
        onDispose {
            // 离开用户页时清掉本页的选择，避免带入别的列表
            if (com.furflix.app.data.selection.SelectionStore.state.value.source == pageSource) {
                com.furflix.app.data.selection.SelectionStore.exit()
            }
        }
    }

    val selectedTab by viewModel.currentTab.collectAsState()
    val activeFolder by viewModel.activeFolder.collectAsState()
    val gallerySection by viewModel.gallerySection.collectAsState()
    // “别人的收藏夹”：本用户是否已被收藏（用户页收藏 tab 的收藏按钮状态源）
    val collectedFavStore = remember { com.furflix.app.data.local.CollectedFavoritesStore.getInstance(context) }
    val collectedFavorites by collectedFavStore.flow.collectAsState(initial = emptyList())
    val isFavCollected = collectedFavorites.any { it.username.equals(username, ignoreCase = true) }
    val repository = remember { FurRepository.getInstance(context) }
    val bannerStore = remember { UserBannerStore.getInstance(repository) }
    val userBanners by bannerStore.banners.collectAsState()
    // 画廊卡片的统计浮层与首页同源（CardStatsCache 详情回填实时推送）
    val cardStats by remember {
        com.furflix.app.data.local.CardStatsCache.getInstance(context).flow
    }.collectAsState(initial = emptyMap())
    // 画廊卡片统计浮层预热：列表新增条目静默回填三项统计与文件类型徽标
    // （共享预热器进程级去重+限速；顺带落详情缓存，点进详情免二次抓取）
    LaunchedEffect(submissions) {
        if (submissions.isNotEmpty()) {
            mainViewModel.prefetchSubmissionStats(submissions.map { it.id })
        }
    }
    val watchlistBlur by repository.watchlistBannerBlurFlow.collectAsState(initial = 0f)
    val bannerSource by repository.watchlistBannerSourceFlow.collectAsState(
        initial = FurRepository.WATCHLIST_BANNER_SOURCE_BANNER
    )
    val userNotes by repository.userNotesFlow.collectAsState(initial = emptyMap())
    var noteEditingUser by remember { mutableStateOf<WatchlistUser?>(null) }
    val screenScope = rememberCoroutineScope()
    // "播放该画师 / 该文件夹全部音乐"：扫描期间显示进度圈
    val activeFolder by viewModel.activeFolder.collectAsState()
    var playlistScanning by remember { mutableStateOf(false) }
    val noMusicLabel = stringResource(R.string.playlist_no_music)
    // 画廊三视图与列数偏好与画廊全局共用（一处设置全局生效）
    val viewMode by repository.viewModeFlow.collectAsState(initial = GalleryViewMode.GRID)
    val gridColumnsPref by repository.gridColumnsFlow.collectAsState(initial = 2)
    val hazeState = remember { HazeState() }

    val tabGallery = stringResource(R.string.user_gallery)
    val tabFavorites = stringResource(R.string.profile_favorites)
    val tabWatching = stringResource(R.string.user_watching)
    val tabWatchers = stringResource(R.string.user_watchers)
    val tabJournals = stringResource(R.string.user_journals)
    // 收藏按钮的 Toast 文案：组合层先解析成值，回调只捕获结果（lint LocalContextGetResourceValueCall）
    val favAddedText = stringResource(R.string.toast_others_fav_added)
    val favRemovedText = stringResource(R.string.toast_others_fav_removed)

    val bottomBarItems = remember(tabGallery, tabFavorites, tabWatching, tabWatchers, tabJournals) {
        listOf(
            GlassBarTab(tabGallery, Icons.Default.PhotoLibrary, Color(0xFF00E5FF)),
            GlassBarTab(tabFavorites, Icons.Default.Favorite, Color(0xFFFF007F)),
            GlassBarTab(tabWatching, Icons.Default.Group, Color(0xFF00FF7F)),
            GlassBarTab(tabWatchers, Icons.Default.Group, Color(0xFFFFD700)),
            GlassBarTab(tabJournals, Icons.AutoMirrored.Filled.MenuBook, Color(0xFFB388FF))
        )
    }

    val listState = rememberLazyStaggeredGridState()
    val autoHideBars by repository.hideBarsOnScrollFlow.collectAsState(initial = true)
    val barsVisibility = rememberBarsVisibility(selectedTab) {
        listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
    }
    val barsVisible = !autoHideBars || barsVisibility.visible
    var scrollTarget by remember { mutableIntStateOf(0) }
    var scrollKey by remember { mutableIntStateOf(0) }

    fun contentFirstIndex(p: UserProfile?): Int {
        if (p == null) return 2
        var idx = 2
        if (p.galleryCount > 0 || p.favoritesCount > 0 ||
            p.watchersCount > 0 || p.watchingCount > 0) idx++
        idx++ // 画廊导航卡(主画廊/Scraps 分区切换+文件夹树)始终显示
        if (p.profileText.isNotBlank()) idx++
        if (p.species.isNotBlank() || p.acceptingTrades.isNotBlank() ||
            p.acceptingCommissions.isNotBlank() || p.views > 0 || p.journalsCount > 0) idx++
        if (p.contactLinks.isNotEmpty()) idx++
        return idx
    }

    fun selectTab(index: Int, fromBottom: Boolean) {
        val isSameTab = selectedTab == index
        if (!isSameTab) {
            when (index) {
                UserViewModel.TAB_GALLERY -> viewModel.loadGallery()
                UserViewModel.TAB_FAVORITES -> viewModel.loadFavorites()
                UserViewModel.TAB_WATCHING -> viewModel.loadWatching()
                UserViewModel.TAB_WATCHERS -> viewModel.loadWatchers()
                UserViewModel.TAB_JOURNALS -> viewModel.loadJournals()
            }
        }
        scrollTarget = when {
            isSameTab && fromBottom -> contentFirstIndex(userProfile)
            fromBottom -> 0
            else -> contentFirstIndex(userProfile)
        }
        scrollKey++
    }

    // Scroll to content section when target changes
    LaunchedEffect(scrollKey) {
        if (scrollKey == 0) return@LaunchedEffect
        kotlinx.coroutines.delay(100)
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0) {
            listState.animateScrollToItem(scrollTarget.coerceIn(0, total - 1))
        }
    }

    // Show the artist's name in the top bar once the hero header is scrolled away
    val showBarTitle by remember { derivedStateOf { listState.firstVisibleItemIndex >= 2 } }

    // 时间线滚动条 + 最后浏览位置：句柄按懒状态 remember，保证下面保存协程不被重组重启
    val timelineHandle = remember(listState) {
        com.furflix.app.ui.components.lazyStaggeredHandle(listState)
    }
    val galleryTimelineNodes = remember(submissions, userProfile) {
        com.furflix.app.ui.components.buildTimelineNodes(
            items = submissions,
            // 作品条目之前有横幅/资料卡等固定头部，节点下标要加上它们
            indexOffset = contentFirstIndex(userProfile)
        )
    }
    val rememberTimelinePosition by repository.rememberTimelinePositionFlow
        .collectAsState(initial = true)
    val savedScrollPositions by repository.scrollPositionsFlow.collectAsState(initial = emptyMap())
    val galleryScrollKey = "user_${username.lowercase()}_$selectedTab"
    com.furflix.app.ui.components.RememberGalleryScrollPosition(
        galleryKey = galleryScrollKey,
        handle = timelineHandle,
        savedIndex = savedScrollPositions[galleryScrollKey]?.toIntOrNull(),
        enabled = rememberTimelinePosition &&
            (selectedTab == UserViewModel.TAB_GALLERY || selectedTab == UserViewModel.TAB_FAVORITES),
        onSave = { index -> screenScope.launch { repository.saveScrollPosition(galleryScrollKey, index) } }
    )

    // Infinite scroll
    val hasContent by rememberUpdatedState(
        when (selectedTab) {
            UserViewModel.TAB_GALLERY, UserViewModel.TAB_FAVORITES -> submissions.isNotEmpty()
            UserViewModel.TAB_WATCHING -> watchingList.isNotEmpty()
            UserViewModel.TAB_WATCHERS -> watchersList.isNotEmpty()
            else -> journals.isNotEmpty()
        }
    )

    // 关注/粉丝卡片的背景预热：与“我的-关注列表”共用 UserBannerStore，滚到谁就补谁的资料页横幅；
    // bannerSource 入 key：来源切换后重置本轮去重，可见卡片按新来源重建
    val watchKeys = remember(watchingList) { watchingList.mapTo(mutableSetOf()) { it.username } }
    val watcherKeys = remember(watchersList) { watchersList.mapTo(mutableSetOf()) { it.username } }
    val warmedBannerUsers = remember(bannerSource) { mutableSetOf<String>() }
    LaunchedEffect(selectedTab, watchKeys, watcherKeys, bannerSource) {
        if (selectedTab != UserViewModel.TAB_WATCHING && selectedTab != UserViewModel.TAB_WATCHERS) {
            return@LaunchedEffect
        }
        val keys = if (selectedTab == UserViewModel.TAB_WATCHING) watchKeys else watcherKeys
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .mapNotNull { info -> (info.key as? String)?.takeIf { it in keys } }
        }.collect { visible ->
            val fresh = visible.filter { warmedBannerUsers.add(it) }
            if (fresh.isNotEmpty()) bannerStore.prefetch(fresh)
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 5
        }.collect { shouldLoadMore ->
            if (shouldLoadMore && hasContent) viewModel.loadMore()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .pointerInput(selectedTab) {
                var accumulatedDrag = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (accumulatedDrag > 60f) {
                            selectTab((selectedTab - 1).coerceAtLeast(0), fromBottom = false)
                        } else if (accumulatedDrag < -60f) {
                            selectTab((selectedTab + 1).coerceAtMost(4), fromBottom = false)
                        }
                        accumulatedDrag = 0f
                    },
                    onDragCancel = { accumulatedDrag = 0f }
                ) { _, dragAmount ->
                    accumulatedDrag += dragAmount
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
        ) {
            AnimatedVisibility(
                visible = barsVisible || selectionActive,
                enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it }
            ) {
            HazeTopAppBar(
                title = {
                    AnimatedVisibility(
                        visible = showBarTitle && userProfile != null,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(200))
                    ) {
                        Text(
                            text = userProfile?.displayName ?: "",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                hazeState = hazeState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // 一键把该画师（或当前打开的文件夹）里的全部音频作品聚合成播放列表
                    if (!selectionActive) {
                        IconButton(
                            enabled = !playlistScanning,
                            onClick = {
                                playlistScanning = true
                                screenScope.launch {
                                    val folder = activeFolder
                                    val list = if (folder != null) {
                                        PlaylistBuilder.folderMusic(context, username, folder)
                                    } else {
                                        PlaylistBuilder.artistMusic(context, username)
                                    }
                                    if (list.items.isEmpty()) {
                                        android.widget.Toast.makeText(
                                            context,
                                            noMusicLabel,
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        PlaybackController.playPlaylist(context, list, 0)
                                    }
                                    playlistScanning = false
                                }
                            }
                        ) {
                            if (playlistScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.QueueMusic,
                                    contentDescription = stringResource(R.string.playlist_play_artist)
                                )
                            }
                        }
                    }
                    if (selectionActive) {
                        val batchQueuedText = stringResource(R.string.toast_batch_download_queued, selection.ids.size)
                        // 与首页多选同一套动作：全选 / 批量收藏 / 批量下载 / 退出
                        IconButton(onClick = {
                            com.furflix.app.data.selection.SelectionStore.selectAll(pageSource, submissions.map { it.id })
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.DoneAll, contentDescription = stringResource(R.string.action_select_all), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { mainViewModel.batchFavorite(selection.ids.toList()) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Favorite, contentDescription = stringResource(R.string.action_batch_favorite), tint = Color(0xFFFF007F), modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = {
                            selection.ids.forEach { com.furflix.app.data.download.DownloadManager.enqueueDownload(context, it) }
                            android.widget.Toast.makeText(context, batchQueuedText, android.widget.Toast.LENGTH_SHORT).show()
                            com.furflix.app.data.selection.SelectionStore.exit()
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Download, contentDescription = stringResource(R.string.action_batch_download), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { com.furflix.app.data.selection.SelectionStore.exit() }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    } else {
                        IconButton(onClick = {
                            openUrl(context, "https://www.furaffinity.net/user/$username/")
                        }) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = stringResource(R.string.cd_open_in_browser))
                        }
                    }
                }
            )
            }

            Crossfade(
                targetState = viewMode,
                animationSpec = tween(300),
                label = "viewModeCrossfade"
            ) { mode ->
                PawPullRefreshBox(
                    isRefreshing = isLoading,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.weight(1f)
                ) {
                Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalStaggeredGrid(
                    columns = if (mode == GalleryViewMode.LIST) {
                        StaggeredGridCells.Fixed(1)
                    } else {
                        masonryGridCells(gridColumnsPref)
                    },
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    verticalItemSpacing = 10.dp,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "header", span = StaggeredGridItemSpan.FullLine) {
                        val profile = userProfile
                        if (profile != null) {
                            // 收藏 tab 下顶部背景默认改用该用户收藏夹里**最新一部作品**的
                            // 缩略图：列表本身即"最近收藏在前"，首条就是最新，无需额外请求。
                            // 尚未加载到或收藏为空时回落资料页横幅。
                            val favThumb = if (selectedTab == UserViewModel.TAB_FAVORITES) {
                                submissions.firstOrNull()?.thumbnailUrl?.takeIf { it.isNotEmpty() }
                            } else {
                                null
                            }
                            ProfileHeader(profile, bannerOverride = favThumb)
                        } else {
                            HeaderSkeleton()
                        }
                    }
                    userProfile?.let { profile ->
                        item(key = "actions", span = StaggeredGridItemSpan.FullLine) {
                            ProfileActions(
                                profile = profile,
                                isToggling = isTogglingWatch,
                                onToggleWatch = { viewModel.toggleWatch() },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        if (profile.galleryCount > 0 || profile.favoritesCount > 0 ||
                            profile.watchersCount > 0 || profile.watchingCount > 0
                        ) {
                            item(key = "stats", span = StaggeredGridItemSpan.FullLine) {
                                StatsCard(
                                    profile = profile,
                                    selectedTab = selectedTab,
                                    onSelect = { selectTab(it, fromBottom = false) },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                        item(key = "gallery_nav", span = StaggeredGridItemSpan.FullLine) {
                            GalleryNavCard(
                                folders = profile.folders,
                                section = gallerySection,
                                activeFolder = activeFolder,
                                onSelectSection = { viewModel.selectGallerySection(it) },
                                onOpen = { viewModel.openFolder(it) },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                        if (profile.profileText.isNotBlank()) {
                            item(key = "bio", span = StaggeredGridItemSpan.FullLine) {
                                BioCard(
                                    text = profile.profileText,
                                    html = profile.profileTextHtml,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    onAuthorClick = onNavigateToUser,
                                    onNavigateToPost = onNavigateToPost
                                )
                            }
                        }
                        val hasChips = profile.species.isNotBlank() ||
                                profile.acceptingTrades.isNotBlank() ||
                                profile.acceptingCommissions.isNotBlank() ||
                                profile.views > 0 || profile.journalsCount > 0
                        if (hasChips) {
                            item(key = "chips", span = StaggeredGridItemSpan.FullLine) {
                                InfoChips(
                                    profile = profile,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    onJournalsClick = { selectTab(UserViewModel.TAB_JOURNALS, fromBottom = false) }
                                )
                            }
                        }
                        if (profile.contactLinks.isNotEmpty()) {
                            item(key = "contacts", span = StaggeredGridItemSpan.FullLine) {
                                ContactLinksRow(links = profile.contactLinks, onOpen = { openUrl(context, it) })
                            }
                        }
                    }

                    when (selectedTab) {
                        UserViewModel.TAB_GALLERY, UserViewModel.TAB_FAVORITES -> {
                            if (selectedTab == UserViewModel.TAB_FAVORITES) {
                                item(key = "fav_collect", span = StaggeredGridItemSpan.FullLine) {
                                    CollectFavoritesCard(
                                        collected = isFavCollected,
                                        onClick = {
                                            if (isFavCollected) {
                                                screenScope.launch { collectedFavStore.remove(username) }
                                                android.widget.Toast.makeText(context, favRemovedText, android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                val profile = userProfile
                                                screenScope.launch {
                                                    collectedFavStore.add(
                                                        username,
                                                        profile?.displayName ?: "",
                                                        profile?.avatarUrl ?: ""
                                                    )
                                                }
                                                android.widget.Toast.makeText(context, favAddedText, android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }
                            if (selectedTab == UserViewModel.TAB_GALLERY &&
                                gallerySection == UserViewModel.GALLERY_SECTION_MAIN &&
                                activeFolder != null
                            ) {
                                item(key = "folder_header", span = StaggeredGridItemSpan.FullLine) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = { viewModel.closeFolder() }) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = stringResource(R.string.action_back)
                                            )
                                        }
                                        // 与文件夹树一致:名称不截断,过长自然换行
                                        Text(
                                            activeFolder?.name ?: "",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            if (errorMessage != null && submissions.isEmpty()) {
                                item(key = "error", span = StaggeredGridItemSpan.FullLine) { ErrorState(message = errorMessage ?: "") }
                            }
                            if (submissions.isEmpty() && isLoading && errorMessage == null) {
                                // 骨架占位与其他页面同构:骨架卡直接铺进当前网格(不嵌套 Lazy 容器),
                                // 比例池与整体骨架共用;列表模式整行短块+文字行,网格模式多变比例
                                if (mode == GalleryViewMode.LIST) {
                                    items(
                                        count = 4,
                                        key = { "skeleton_$it" },
                                        span = { StaggeredGridItemSpan.FullLine },
                                        contentType = { "skeleton" }
                                    ) { index ->
                                        SkeletonCard(
                                            ratio = 1.33f,
                                            showText = true,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                    }
                                } else {
                                    items(
                                        count = 12,
                                        key = { "skeleton_$it" },
                                        contentType = { "skeleton" }
                                    ) { index ->
                                        SkeletonCard(
                                            ratio = SKELETON_RATIOS[index % SKELETON_RATIOS.size],
                                            modifier = Modifier.padding(2.dp)
                                        )
                                    }
                                }
                            }
                            if (submissions.isEmpty() && !isLoading && errorMessage == null) {
                                item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                                    EmptyState(
                                        message = stringResource(
                                            if (gallerySection == UserViewModel.GALLERY_SECTION_SCRAPS) {
                                                R.string.empty_scraps
                                            } else {
                                                R.string.empty_gallery
                                            }
                                        ),
                                        icon = Icons.Default.PhotoLibrary
                                    )
                                }
                            }
                            if (submissions.isNotEmpty()) {
                                if (mode == GalleryViewMode.LIST) {
                                    items(
                                        items = submissions,
                                        key = { it.id },
                                        span = { StaggeredGridItemSpan.FullLine }
                                    ) { submission ->
                                        Box(modifier = Modifier.animateItem().padding(horizontal = 8.dp)) {
                                            SubmissionCard(
                                                submission = submission,
                                                onClick = {
                                                    if (selectionActive) {
                                                        com.furflix.app.data.selection.SelectionStore.toggle(pageSource, submission.id)
                                                    } else {
                                                        onNavigateToPost(submission.id)
                                                    }
                                                },
                                                selectionActive = selectionActive,
                                                isSelected = selectionActive && selection.ids.contains(submission.id),
                                                onLongClick = { com.furflix.app.data.selection.SelectionStore.toggle(pageSource, submission.id) },
                                                onAuthorClick = { onNavigateToUser(it) },
                                                stats = cardStats[submission.id]
                                            )
                                        }
                                    }
                                } else {
                                    items(
                                        items = submissions,
                                        key = { it.id },
                                        contentType = { "submission" }
                                    ) { submission ->
                                        SubmissionCard(
                                            modifier = Modifier.animateItem(),
                                            submission = submission,
                                            onClick = {
                                                if (selectionActive) {
                                                    com.furflix.app.data.selection.SelectionStore.toggle(pageSource, submission.id)
                                                } else {
                                                    onNavigateToPost(submission.id)
                                                }
                                            },
                                            selectionActive = selectionActive,
                                            isSelected = selectionActive && selection.ids.contains(submission.id),
                                            onLongClick = { com.furflix.app.data.selection.SelectionStore.toggle(pageSource, submission.id) },
                                            showTitle = mode == GalleryViewMode.GRID,
                                            compact = mode == GalleryViewMode.GRID,
                                            ratio = if (mode == GalleryViewMode.MASONRY) submission.thumbnailWidthOnHeightRatio else 0f,
                                            stats = cardStats[submission.id],
                                            onAuthorClick = { onNavigateToUser(it) }
                                        )
                                    }
                                }
                            }
                            if (isLoadingMore) {
                                item(key = "footer", span = StaggeredGridItemSpan.FullLine) { LoadingMoreFooter() }
                            }
                        }
                        UserViewModel.TAB_WATCHING -> {
                            if (errorMessage != null && watchingList.isEmpty()) {
                                item(key = "error", span = StaggeredGridItemSpan.FullLine) { ErrorState(message = errorMessage ?: "") }
                            }
                            if (watchingList.isEmpty() && !isLoading && errorMessage == null) {
                                item(key = "empty", span = StaggeredGridItemSpan.FullLine) { EmptyState(message = stringResource(R.string.empty_watching), icon = Icons.Default.Group) }
                            }
                            if (watchingList.isNotEmpty()) {
                                if (mode == GalleryViewMode.LIST) {
                                    items(
                                        items = watchingList,
                                        key = { it.username },
                                        span = { StaggeredGridItemSpan.FullLine }
                                    ) { user ->
                                        Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                                            WatchlistItem(
                                                user = user,
                                                note = userNotes[user.username.lowercase()],
                                                onClick = { onNavigateToUser(user.username) },
                                                onLongClick = { noteEditingUser = user }
                                            )
                                        }
                                    }
                                    } else {
                                        items(watchingList, key = { it.username }, contentType = { "watch" }) { user ->
                                            WatchlistGridItem(
                                                user = user,
                                                bannerUrl = userBanners[user.username.lowercase()],
                                                blurDp = watchlistBlur,
                                                note = userNotes[user.username.lowercase()],
                                                onClick = { onNavigateToUser(user.username) },
                                                onLongClick = { noteEditingUser = user }
                                            )
                                        }
                                    }
                            }
                            if (isLoadingMore) {
                                item(key = "footer", span = StaggeredGridItemSpan.FullLine) { LoadingMoreFooter() }
                            }
                        }
                        UserViewModel.TAB_WATCHERS -> {
                            if (errorMessage != null && watchersList.isEmpty()) {
                                item(key = "error", span = StaggeredGridItemSpan.FullLine) { ErrorState(message = errorMessage ?: "") }
                            }
                            if (watchersList.isEmpty() && !isLoading && errorMessage == null) {
                                item(key = "empty", span = StaggeredGridItemSpan.FullLine) { EmptyState(message = stringResource(R.string.empty_watchers), icon = Icons.Default.Group) }
                            }
                            if (watchersList.isNotEmpty()) {
                                if (mode == GalleryViewMode.LIST) {
                                    items(
                                        items = watchersList,
                                        key = { it.username },
                                        span = { StaggeredGridItemSpan.FullLine }
                                    ) { user ->
                                        Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                                            WatchlistItem(
                                                user = user,
                                                note = userNotes[user.username.lowercase()],
                                                onClick = { onNavigateToUser(user.username) },
                                                onLongClick = { noteEditingUser = user }
                                            )
                                        }
                                    }
                                    } else {
                                        items(watchersList, key = { it.username }, contentType = { "watch" }) { user ->
                                            WatchlistGridItem(
                                                user = user,
                                                bannerUrl = userBanners[user.username.lowercase()],
                                                blurDp = watchlistBlur,
                                                note = userNotes[user.username.lowercase()],
                                                onClick = { onNavigateToUser(user.username) },
                                                onLongClick = { noteEditingUser = user }
                                            )
                                        }
                                    }
                            }
                            if (isLoadingMore) {
                                item(key = "footer", span = StaggeredGridItemSpan.FullLine) { LoadingMoreFooter() }
                            }
                        }
                        UserViewModel.TAB_JOURNALS -> {
                            if (errorMessage != null && journals.isEmpty()) {
                                item(key = "error", span = StaggeredGridItemSpan.FullLine) { ErrorState(message = errorMessage ?: "") }
                            }
                            if (journals.isEmpty() && !isLoading && errorMessage == null) {
                                item(key = "empty", span = StaggeredGridItemSpan.FullLine) { EmptyState(message = stringResource(R.string.empty_journals), icon = Icons.AutoMirrored.Filled.MenuBook) }
                            }
                            if (journals.isNotEmpty()) {
                                items(
                                    items = journals,
                                    key = { it.id },
                                    span = { StaggeredGridItemSpan.FullLine }
                                ) { journal ->
                                    Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                                        JournalCard(
                                            journal = journal,
                                            onClick = { onNavigateToJournal(journal.id) }
                                        )
                                    }
                                }
                            }
                            if (isLoadingMore) {
                                item(key = "footer", span = StaggeredGridItemSpan.FullLine) { LoadingMoreFooter() }
                            }
                        }
                    }
                }
                    if (selectedTab == UserViewModel.TAB_GALLERY ||
                        selectedTab == UserViewModel.TAB_FAVORITES
                    ) {
                        com.furflix.app.ui.components.GalleryTimelineScrubber(
                            handle = timelineHandle,
                            nodes = galleryTimelineNodes,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                } // close scrubber overlay Box
                }
            }
        } // close Column

            // Glass bottom bar
        val bottomBarOffset by androidx.compose.animation.core.animateDpAsState(
            targetValue = if (barsVisible && !selectionActive) 0.dp else 112.dp,
            animationSpec = tween(250),
            label = "profileBottomBarOffset"
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = bottomBarOffset)
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .fillMaxWidth()
                .height(64.dp)
        ) {
            GlassmorphicBottomBar(
                tabs = bottomBarItems,
                selectedIndex = selectedTab,
                hazeState = hazeState,
                onTabSelected = { selectTab(it, fromBottom = true) }
            )
        }
    }

    noteEditingUser?.let { noteUser ->
        UserNoteDialog(
            user = noteUser,
            currentNote = userNotes[noteUser.username.lowercase()] ?: "",
            onSave = { note ->
                noteEditingUser = null
                screenScope.launch { repository.saveUserNote(noteUser.username, note) }
            },
            onDismiss = { noteEditingUser = null }
        )
    }
}

// -----------------------------------------------------------------------------------------
// 画廊导航卡：FA 用户画廊的两个顶级分区（主画廊 Main Gallery / Scraps）+ 文件夹树。
// 结构对齐站点画廊页侧栏：组标题（h4）→ 组内文件夹，无组文件夹（default-group）挂根级；
// 组可折叠；文件夹名不截断（过长自然换行，FA 侧栏同样不截断）。
// -----------------------------------------------------------------------------------------

@Composable
private fun GalleryNavCard(
    folders: List<GalleryFolder>,
    section: Int,
    activeFolder: GalleryFolder?,
    onSelectSection: (Int) -> Unit,
    onOpen: (GalleryFolder) -> Unit,
    modifier: Modifier = Modifier
) {
    // 折叠分组集合；组名是用户自建内容，作 key 不参与翻译
    val collapsedGroups = remember { mutableStateMapOf<String, Boolean>() }
    // K-004:整个「画廊文件夹」区块默认折叠 —— 分组多的用户页首屏会被撑长。
    // 一旦用户手动展开过则记住其选择（foldersExpanded 持有显式意图）。
    var foldersExpanded by remember { mutableStateOf(false) }
    // 分组归并，保持站点出现顺序（FA 把 default-group 的无组文件夹排在组后面）
    val grouped = remember(folders) {
        val ordered = LinkedHashMap<String, MutableList<GalleryFolder>>()
        folders.forEach { folder ->
            ordered.getOrPut(folder.group) { mutableListOf() }.add(folder)
        }
        ordered
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurface)
            .border(1.dp, BorderDark, RoundedCornerShape(20.dp))
            .padding(10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            GallerySectionEntry(
                label = stringResource(R.string.gallery_section_main),
                icon = Icons.Default.PhotoLibrary,
                selected = section == UserViewModel.GALLERY_SECTION_MAIN,
                onClick = { onSelectSection(UserViewModel.GALLERY_SECTION_MAIN) },
                modifier = Modifier.weight(1f)
            )
            GallerySectionEntry(
                label = stringResource(R.string.gallery_section_scraps),
                icon = Icons.Default.CollectionsBookmark,
                selected = section == UserViewModel.GALLERY_SECTION_SCRAPS,
                onClick = { onSelectSection(UserViewModel.GALLERY_SECTION_SCRAPS) },
                modifier = Modifier.weight(1f)
            )
        }
        if (folders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            // K-004:标题行即折叠开关，默认收起（箭头朝右）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { foldersExpanded = !foldersExpanded }
                    .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 4.dp)
            ) {
                Icon(
                    if (foldersExpanded) Icons.Default.KeyboardArrowDown
                    else Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = SubtleGray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    stringResource(R.string.profile_gallery_folders),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = SubtleGray,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = SubtleGray,
                    modifier = Modifier.size(16.dp)
                )
            }
            // 选中了具体文件夹时保持展开，避免用户刚点进去列表就收起
            val showFolders = foldersExpanded || activeFolder != null
            if (showFolders) {
                grouped.forEach { (group, groupFolders) ->
                    if (group.isEmpty()) {
                        // 无组文件夹：根级直列（站点的 default-group）
                        groupFolders.forEach { folder ->
                            FolderTreeRow(
                                folder = folder,
                                active = activeFolder?.id == folder.id,
                                onOpen = onOpen
                            )
                        }
                    } else {
                        val collapsed = collapsedGroups[group] == true
                        FolderGroupHeader(
                            group = group,
                            collapsed = collapsed,
                            onToggle = { collapsedGroups[group] = !collapsed }
                        )
                        if (!collapsed) {
                            groupFolders.forEach { folder ->
                                FolderTreeRow(
                                    folder = folder,
                                    active = activeFolder?.id == folder.id,
                                    onOpen = onOpen,
                                    indent = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GallerySectionEntry(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = tween(250),
        label = "sectionContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else SubtleGray,
        animationSpec = tween(250),
        label = "sectionContent"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else BorderDark,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor
        )
    }
}

@Composable
private fun FolderGroupHeader(group: String, collapsed: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 4.dp)
    ) {
        Icon(
            if (collapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = SubtleGray,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = group,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = SubtleGray
        )
    }
}

@Composable
private fun FolderTreeRow(
    folder: GalleryFolder,
    active: Boolean,
    onOpen: (GalleryFolder) -> Unit,
    indent: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (indent) Modifier.padding(start = 24.dp) else Modifier)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
            .clickable { onOpen(folder) }
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.primary else SubtleGray,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = folder.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.weight(1f)
        )
        if (folder.count > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.folder_item_count, folder.count),
                style = MaterialTheme.typography.labelSmall,
                color = SubtleGray
            )
        }
    }
}

// -----------------------------------------------------------------------------------------
// 用户页收藏 tab：收藏/取消“TA 的收藏夹”到 我的-收藏夹-别人的收藏夹（本地存储）
// -----------------------------------------------------------------------------------------

@Composable
private fun CollectFavoritesCard(collected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (collected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else DarkSurfaceVariant)
            .border(
                1.dp,
                if (collected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else BorderSubtleDark,
                RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Icon(
            if (collected) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            contentDescription = null,
            tint = if (collected) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(if (collected) R.string.fav_collection_remove else R.string.fav_collection_add),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (collected) MaterialTheme.colorScheme.primary else Color.White
        )
    }
}

// -----------------------------------------------------------------------------------------
// Hero header: banner + avatar + name + title chips
// -----------------------------------------------------------------------------------------

@Composable
private fun ProfileHeader(profile: UserProfile, bannerOverride: String? = null) {
    // 背景优先用调用方给的覆盖图（收藏 tab = 最新收藏作品的缩略图），否则资料页横幅。
    // 站点缩略图按显示尺寸向 CDN 协商更高档位，避免 180dp 高的背景被放大到发虚；
    // 资料页横幅本身不是档位化 URL，DynamicThumbnail 会原样返回。
    val rawBanner = bannerOverride?.takeIf { it.isNotEmpty() } ?: profile.bannerUrl
    val bannerModel = remember(rawBanner) {
        if (rawBanner.isEmpty()) {
            rawBanner
        } else {
            com.furflix.app.data.remote.DynamicThumbnail(rawBanner).bestThumbnailUrl(1200f, 400f)
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                if (bannerModel.isNotEmpty()) {
                    AsyncImage(
                        model = bannerModel,
                        contentDescription = stringResource(R.string.cd_banner),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Cyan.copy(alpha = 0.25f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                        Pink.copy(alpha = 0.20f)
                                    )
                                )
                            )
                    )
                }
                // Fade into the background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    DarkBackground.copy(alpha = 0.55f),
                                    DarkBackground
                                ),
                                startY = 120f
                            )
                        )
                )
            }

            // Avatar overlapping the banner bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp)
                    .offset(y = 36.dp)
                    .size(96.dp),
                contentAlignment = Alignment.Center
            ) {
                // Neon accent ring
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(Cyan, Pink)
                            ),
                            shape = CircleShape
                        )
                )
                AsyncImage(
                    model = profile.avatarUrl,
                    contentDescription = stringResource(R.string.cd_avatar),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .border(3.dp, DarkBackground, CircleShape)
                        .background(DarkSurfaceVariant)
                )
            }
        }

        // Name block (below avatar overlap)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 44.dp)
        ) {
            Text(
                text = profile.displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "@${profile.username}",
                style = MaterialTheme.typography.bodyMedium,
                color = SubtleGray
            )

            if (profile.userTitle.isNotBlank() || profile.registeredText.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (profile.userTitle.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                Icons.Default.Badge,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = profile.userTitle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (profile.registeredText.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(ChipBackground)
                                .border(1.dp, ChipBorder, RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = SubtleGray,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = stringResource(R.string.profile_joined, profile.registeredText),
                                fontSize = 12.sp,
                                color = SubtleGray
                            )
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// Action row: big Watch button + PM + Share
// -----------------------------------------------------------------------------------------

@Composable
private fun ProfileActions(
    profile: UserProfile,
    isToggling: Boolean,
    onToggleWatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showUnwatchDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (profile.watchUrl.isNotEmpty()) {
            WatchActionButton(
                isWatching = profile.isWatching,
                isToggling = isToggling,
                onClick = {
                    if (profile.isWatching) showUnwatchDialog = true
                    else onToggleWatch()
                },
                modifier = Modifier.weight(1f)
            )
        }
        if (profile.noteUrl.isNotEmpty()) {
            ActionIconButton(
                icon = Icons.Default.Mail,
                contentDescription = stringResource(R.string.cd_send_note),
                onClick = { openUrl(context, profile.noteUrl) }
            )
        }
        ActionIconButton(
            icon = Icons.Default.Share,
            contentDescription = stringResource(R.string.cd_share_profile),
            onClick = {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "https://www.furaffinity.net/user/${profile.username}/"
                    )
                }
                context.startActivity(Intent.createChooser(sendIntent, null))
            }
        )
    }

    if (showUnwatchDialog) {
        AlertDialog(
            onDismissRequest = { showUnwatchDialog = false },
            containerColor = DarkSurface,
            titleContentColor = Color.White,
            textContentColor = SubtleGray,
            title = { Text(stringResource(R.string.user_unfollow)) },
            text = { Text(stringResource(R.string.user_unfollow_confirm, profile.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    showUnwatchDialog = false
                    onToggleWatch()
                }) {
                    Text(stringResource(R.string.user_unfollow), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnwatchDialog = false }) {
                    Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}

/**
 * Big prominent watch/unwatch button with a particle burst when subscribing.
 */
@Composable
private fun WatchActionButton(
    isWatching: Boolean,
    isToggling: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val explosionAnim = remember { Animatable(0f) }
    var wasWatching by remember { mutableStateOf(isWatching) }
    val primaryColor = MaterialTheme.colorScheme.primary

    val particles = remember(primaryColor) {
        List(18) { i ->
            val angle = (i * 20 + Random.nextInt(-8, 8)) * (Math.PI / 180f).toFloat()
            Particle(
                id = i,
                angle = angle,
                distance = Random.nextFloat() * 70f + 50f,
                size = Random.nextFloat() * 4f + 2f,
                color = listOf(primaryColor, Cyan, Pink, Color.White).random()
            )
        }
    }

    LaunchedEffect(isWatching) {
        if (!wasWatching && isWatching) {
            explosionAnim.snapTo(0f)
            explosionAnim.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        }
        wasWatching = isWatching
    }

    val buttonScale by animateFloatAsState(
        targetValue = if (isToggling) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "watchScale"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isWatching) DarkSurfaceVariant else MaterialTheme.colorScheme.primary,
        animationSpec = tween(300),
        label = "watchContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isWatching) MaterialTheme.colorScheme.primary else Color(0xFF0A0A0A),
        animationSpec = tween(300),
        label = "watchContent"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Particle burst behind the button
        if (explosionAnim.value > 0f && explosionAnim.value < 1f) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val progress = explosionAnim.value
                val alpha = if (progress > 0.5f) (1f - progress) * 2f else 1f
                particles.forEach { particle ->
                    val currentDist = particle.distance * (1f - (1f - progress) * (1f - progress))
                    drawCircle(
                        color = particle.color.copy(alpha = alpha),
                        radius = particle.size * (1f - progress * 0.3f),
                        center = Offset(
                            x = center.x + cos(particle.angle) * currentDist,
                            y = center.y + sin(particle.angle) * currentDist
                        ),
                        style = Fill
                    )
                }
            }
        }

        Button(
            onClick = onClick,
            enabled = !isToggling,
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = containerColor.copy(alpha = 0.6f),
                disabledContentColor = contentColor.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .scale(buttonScale)
                .then(
                    if (isWatching) Modifier.border(
                        1.5.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        RoundedCornerShape(24.dp)
                    ) else Modifier
                )
        ) {
            if (isToggling) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = contentColor,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    if (isWatching) Icons.Default.Check else Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isWatching) stringResource(R.string.user_watching) else stringResource(R.string.user_watch),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(DarkSurfaceVariant)
            .border(1.dp, BorderSubtleDark, RoundedCornerShape(23.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

// -----------------------------------------------------------------------------------------
// Stats card — each block is tappable and switches the corresponding tab
// -----------------------------------------------------------------------------------------

@Composable
private fun StatsCard(
    profile: UserProfile,
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurface)
            .border(1.dp, BorderDark, RoundedCornerShape(20.dp))
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatBlock(
            count = profile.galleryCount,
            label = stringResource(R.string.user_gallery),
            selected = selectedTab == UserViewModel.TAB_GALLERY,
            onClick = { onSelect(UserViewModel.TAB_GALLERY) },
            modifier = Modifier.weight(1f)
        )
        StatDivider()
        StatBlock(
            count = profile.favoritesCount,
            label = stringResource(R.string.profile_favorites),
            selected = selectedTab == UserViewModel.TAB_FAVORITES,
            onClick = { onSelect(UserViewModel.TAB_FAVORITES) },
            modifier = Modifier.weight(1f)
        )
        StatDivider()
        StatBlock(
            count = profile.watchingCount,
            label = stringResource(R.string.user_watching),
            selected = selectedTab == UserViewModel.TAB_WATCHING,
            onClick = { onSelect(UserViewModel.TAB_WATCHING) },
            modifier = Modifier.weight(1f)
        )
        StatDivider()
        StatBlock(
            count = profile.watchersCount,
            label = stringResource(R.string.user_watchers),
            selected = selectedTab == UserViewModel.TAB_WATCHERS,
            onClick = { onSelect(UserViewModel.TAB_WATCHERS) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatBlock(
    count: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val valueColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.White,
        animationSpec = tween(250),
        label = "statValueColor"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = formatCount(count),
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = valueColor
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else SubtleGray
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(30.dp)
            .background(DividerDark)
    )
}

// -----------------------------------------------------------------------------------------
// Bio card — expandable
// -----------------------------------------------------------------------------------------

@Composable
private fun BioCard(
    text: String,
    html: String = "",
    modifier: Modifier = Modifier,
    onAuthorClick: (String) -> Unit = {},
    onNavigateToPost: (String) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    var translatedOverflow by remember { mutableStateOf(false) }
    val translationPrefs = rememberTranslationPrefs()
    val entry = rememberTranslationEntry(text, text, TranslationCategory.USER_PROFILE)
    TranslatableAutoEffect(entry, translationPrefs)
    val state by entry.state.collectAsStateWithLifecycle()
    val mode = translationPrefs.displayMode(TranslationCategory.USER_PROFILE)
    // 富文本（HtmlTextView）给不出"已溢出"信号：按长度阈值近似判断折叠需求
    val htmlOverflow = remember(html, text) { html.length > 240 || text.length > 240 }
    // 纯文本回落：无富文本 HTML 时把换行转 <br>
    val bioHtml = html.ifBlank {
        text.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurface)
            .border(1.dp, BorderDark, RoundedCornerShape(20.dp))
            .animateContentSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = stringResource(R.string.profile_about),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.weight(1f))
            TranslationIconButton(entry = entry, enabled = translationPrefs.enabled)
        }
        Spacer(modifier = Modifier.height(8.dp))
        // 原文富文本：iconusername 内联头像 + 站内链接路由（TranslationEntry 不动，
        // 自动/手动翻译仍按纯文本进行）；仅译文模式收起原文
        val success = state as? TranslationUiState.Success
        val translationVisible = success != null && success.showing
        if (!translationVisible || mode == TranslationDisplayMode.BILINGUAL) {
            HtmlTextView(
                html = bioHtml,
                onAuthorClick = onAuthorClick,
                onNavigateToPost = onNavigateToPost,
                maxLines = if (expanded) Int.MAX_VALUE else 4
            )
        }
        if (translationVisible) {
            if (mode == TranslationDisplayMode.BILINGUAL) {
                Spacer(modifier = Modifier.height(6.dp))
            }
            Text(
                text = success.translated,
                style = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, color = DarkOnSurface),
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result ->
                    if (!expanded) translatedOverflow = result.hasVisualOverflow
                }
            )
            TranslationCaption(entry, success, mode)
        }
        val failure = state as? TranslationUiState.Failed
        if (failure != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = translationErrorText(failure),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (htmlOverflow || translatedOverflow || expanded) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (expanded) stringResource(R.string.profile_show_less) else stringResource(R.string.profile_show_more),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { expanded = !expanded }
            )
        }
    }
}

// -----------------------------------------------------------------------------------------
// Info chips: species, trades/commissions status, views, journals
// -----------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoChips(
    profile: UserProfile,
    modifier: Modifier = Modifier,
    onJournalsClick: (() -> Unit)? = null
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (profile.species.isNotBlank()) {
            InfoChip(icon = Icons.Default.Pets, text = profile.species)
        }
        if (profile.acceptingTrades.isNotBlank()) {
            StatusChip(label = stringResource(R.string.profile_trades), value = profile.acceptingTrades)
        }
        if (profile.acceptingCommissions.isNotBlank()) {
            StatusChip(label = stringResource(R.string.profile_commissions), value = profile.acceptingCommissions)
        }
        if (profile.views > 0) {
            InfoChip(icon = Icons.Default.Visibility, text = stringResource(R.string.profile_views_count, formatCount(profile.views)))
        }
        if (profile.journalsCount > 0) {
            InfoChip(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                text = stringResource(R.string.profile_journals_count, formatCount(profile.journalsCount)),
                onClick = onJournalsClick
            )
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ChipBackground)
            .border(1.dp, ChipBorder, RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, fontSize = 12.sp, color = DarkOnSurface)
    }
}

@Composable
private fun StatusChip(label: String, value: String) {
    val isOpen = value.equals("yes", ignoreCase = true)
    val isClosed = value.equals("no", ignoreCase = true)
    val dotColor = when {
        isOpen -> Rating_General
        isClosed -> Rating_Adult
        else -> SubtleGray
    }
    val stateText = when {
        isOpen -> stringResource(R.string.profile_status_open)
        isClosed -> stringResource(R.string.profile_status_closed)
        else -> value
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ChipBackground)
            .border(1.dp, ChipBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(text = "$label: $stateText", fontSize = 12.sp, color = DarkOnSurface)
    }
}

// -----------------------------------------------------------------------------------------
// Contact links — horizontally scrollable chips
// -----------------------------------------------------------------------------------------

@Composable
private fun ContactLinksRow(
    links: List<ContactLink>,
    onOpen: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.profile_links),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            links.forEach { link ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(ChipBackground)
                        .border(1.dp, ChipBorder, RoundedCornerShape(20.dp))
                        .clickable { onOpen(link.url) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        contactIcon(link.type),
                        contentDescription = link.type,
                        tint = Cyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = link.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = DarkOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun contactIcon(type: String): ImageVector = when (type.lowercase()) {
    "website" -> Icons.Default.Language
    "youtube" -> Icons.Default.PlayArrow
    "twitter" -> Icons.Default.AlternateEmail
    "twitch" -> Icons.Default.VideogameAsset
    "facebook" -> Icons.Default.Public
    "telegram" -> Icons.AutoMirrored.Filled.Send
    "instagram" -> Icons.Default.PhotoCamera
    else -> Icons.Default.Link
}

// -----------------------------------------------------------------------------------------
// Loading skeleton for the hero header
// -----------------------------------------------------------------------------------------

@Composable
private fun HeaderSkeleton() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(DarkSurfaceVariant.copy(alpha = alpha))
        )
        Row(
            modifier = Modifier
                .padding(start = 16.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant.copy(alpha = alpha))
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurfaceVariant.copy(alpha = alpha))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(13.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurfaceVariant.copy(alpha = alpha))
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(DarkSurfaceVariant.copy(alpha = alpha))
        )
    }
}

// -----------------------------------------------------------------------------------------
// Journal card (user journals tab) — title bilingual-translatable, tap opens journal detail
// -----------------------------------------------------------------------------------------

@Composable
private fun JournalCard(
    journal: com.furflix.app.data.model.Journal,
    onClick: () -> Unit
) {
    val prefs = rememberTranslationPrefs()
    val entry = rememberTranslationEntry(
        key = journal.id,
        text = journal.title,
        category = TranslationCategory.TITLE
    )
    // 与卡片标题同款防抖：快速滑动时不打翻译服务，停稳 500ms 才送翻
    LaunchedEffect(entry, prefs.enabled, prefs.titleAuto) {
        if (prefs.enabled && prefs.titleAuto) {
            kotlinx.coroutines.delay(500)
            val s = entry.state.value
            if (s is com.furflix.app.translate.ui.TranslationUiState.Idle && !s.autoAttempted) {
                entry.translateAuto()
            }
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                TranslatableText(
                    entry = entry,
                    prefs = prefs,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (prefs.enabled) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TranslationIconButton(entry = entry, enabled = true)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (journal.rating.isNotEmpty()) {
                    Text(
                        text = journal.rating,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            journal.rating.contains("General", true) -> Rating_General
                            journal.rating.contains("Mature", true) -> Rating_Mature
                            journal.rating.contains("Adult", true) -> Rating_Adult
                            else -> SubtleGray
                        }
                    )
                }
                if (journal.date.isNotEmpty()) {
                    Text(
                        text = journal.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = SubtleGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (journal.commentCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = SubtleGray,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = formatCount(journal.commentCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = SubtleGray
                        )
                    }
                }
            }
            if (journal.excerpt.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = journal.excerpt,
                    style = MaterialTheme.typography.bodySmall,
                    color = SubtleGray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// Helpers
// -----------------------------------------------------------------------------------------

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
