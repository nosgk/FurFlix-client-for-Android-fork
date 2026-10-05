package com.furflix.app.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import com.furflix.app.R

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import com.furflix.app.data.selection.SelectionStore
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.furflix.app.ui.components.CustomTabRow
import com.furflix.app.ui.components.EmptyState
import com.furflix.app.ui.components.LiveScrollSourceEffect
import com.furflix.app.ui.components.LoadingMoreFooter
import com.furflix.app.ui.components.PawPullRefreshBox
import com.furflix.app.ui.components.SubmissionCard
import com.furflix.app.ui.components.galleryGridCells
import com.furflix.app.ui.components.masonryGridCells
import com.furflix.app.ui.components.UserNoteDialog
import com.furflix.app.ui.components.WatchlistGridItem
import com.furflix.app.ui.components.WatchlistItem
import com.furflix.app.ui.theme.*
import com.furflix.app.data.local.UserBannerStore
import com.furflix.app.data.model.GalleryViewMode
import com.furflix.app.data.model.WatchlistUser
import com.furflix.app.data.repository.FurRepository
import com.furflix.app.viewmodel.WatchlistViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** "我的"页多选 source：与首页 browse/latest/search 及 user_/tag_ 前缀同级隔离 */
private const val SELECTION_SOURCE_FAVORITES = "profile_fav"
private const val SELECTION_SOURCE_HISTORY = "profile_hist"

@Composable
fun ProfileScreen(
    viewMode: GalleryViewMode,
    columnsPref: Int = 2,
    cardStats: Map<String, com.furflix.app.data.model.CardStats> = emptyMap(),
    onClearHistory: () -> Unit = {},
    onWarmStats: (List<String>) -> Unit = {},
    onBatchUnfavorite: (List<String>) -> Unit = {},
    onRemoveHistory: (List<String>) -> Unit = {},
    onNavigateToUser: (String) -> Unit,
    onNavigateToPost: (String) -> Unit,
    onNavigateToFavoritesPost: (String) -> Unit = onNavigateToPost,
    scrollToTopTrigger: Int = 0,
    onRegisterScrollSource: (((() -> Pair<Int, Int>?)?)) -> Unit = {},
    viewModel: WatchlistViewModel = viewModel()
) {
    val followingList by viewModel.followingList.collectAsState()
    val followersList by viewModel.followersList.collectAsState()
    val favoritesList by viewModel.favoritesList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val followingHasMore by viewModel.followingHasMore.collectAsState()
    val followersHasMore by viewModel.followersHasMore.collectAsState()

    // 关注卡片的横幅走共享仓库：“我的-关注列表”与用户页关注/粉丝 tab 同源同缓存
    val context = LocalContext.current
    val repository = remember { FurRepository.getInstance(context) }
    val bannerStore = remember { UserBannerStore.getInstance(repository) }
    val userBanners by bannerStore.banners.collectAsState()
    val watchlistBlur by repository.watchlistBannerBlurFlow.collectAsState(initial = 0f)
    val bannerSource by repository.watchlistBannerSourceFlow.collectAsState(
        initial = FurRepository.WATCHLIST_BANNER_SOURCE_BANNER
    )
    val userNotes by repository.userNotesFlow.collectAsState(initial = emptyMap())
    // 浏览历史“用户”栏：真实到访的用户页记录（数据层与帖子历史分开，长按可删单条）
    val userVisitCache = remember { com.furflix.app.data.local.UserVisitCache.getInstance(context) }
    val userVisits by userVisitCache.flow.collectAsState(initial = emptyList())
    var noteEditingUser by remember { mutableStateOf<WatchlistUser?>(null) }

    // 多选批量（"我的"页）：收藏栏批量取消收藏，历史栏批量删除历史；
    // source 独立成键，与首页 browse/latest/search 互不串扰
    val selection by SelectionStore.state.collectAsState()
    val favSelectionActive = selection.active && selection.source == SELECTION_SOURCE_FAVORITES
    val histSelectionActive = selection.active && selection.source == SELECTION_SOURCE_HISTORY
    androidx.activity.compose.BackHandler(enabled = favSelectionActive || histSelectionActive) {
        SelectionStore.exit()
    }
    DisposableEffect(Unit) {
        onDispose {
            val current = SelectionStore.state.value
            if (current.source == SELECTION_SOURCE_FAVORITES || current.source == SELECTION_SOURCE_HISTORY) {
                SelectionStore.exit()
            }
        }
    }

    // 浏览历史（作品栏）= 统计缓存中“真实到访”的条目；缓存按最旧在前排序，倒序即最新在前。
    // ratio 必须带回：丢了它瀑布流每块都按 1:1 预设，视觉上退化成等高网格。
    // visited 由详情页真实加载写入；统计预热条目不算历史
    val historyEntries = remember(cardStats) {
        cardStats.mapNotNull { (id, stats) ->
            if (!stats.visited || stats.title.isEmpty()) return@mapNotNull null
            com.furflix.app.data.model.Submission(
                id = id,
                title = stats.title,
                author = stats.author,
                authorUsername = stats.authorUsername,
                thumbnailUrl = stats.thumbnailUrl,
                thumbnailWidthOnHeightRatio = stats.ratio,
                rating = stats.rating,
                views = stats.views,
                favorites = stats.favorites,
                comments = stats.comments,
                isFavorited = stats.favorited
            )
        }.asReversed()
    }

    // 收藏栏统计预热：新增条目投喂共享预热器（经 onWarmStats → MainViewModel），
    // 三项统计与文件类型徽标随后点亮（FA 列表页 HTML 本身不含这些数字）
    LaunchedEffect(favoritesList) {
        if (favoritesList.isNotEmpty()) onWarmStats(favoritesList.map { it.id })
    }


    val topTabs = listOf(
        stringResource(R.string.profile_favorites),
        stringResource(R.string.profile_watchlist),
        stringResource(R.string.profile_view_history)
    )
    val pagerState = rememberPagerState(pageCount = { topTabs.size })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        when (pagerState.currentPage) {
            0 -> viewModel.setTab(WatchlistViewModel.TAB_FAVORITES)
            1 -> viewModel.setTab(WatchlistViewModel.TAB_FOLLOWING)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // 多选操作条：收藏栏=批量取消收藏，历史栏=批量删除历史（与首页多选同一套交互）
        if (favSelectionActive || histSelectionActive) {
            val selectAllSource = if (favSelectionActive) SELECTION_SOURCE_FAVORITES else SELECTION_SOURCE_HISTORY
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.selection_mode_n, selection.ids.size),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        val ids = if (favSelectionActive) favoritesList.map { it.id } else historyEntries.map { it.id }
                        SelectionStore.selectAll(selectAllSource, ids)
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.DoneAll, contentDescription = stringResource(R.string.action_select_all), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = {
                        val ids = selection.ids.toList()
                        if (favSelectionActive) onBatchUnfavorite(ids) else onRemoveHistory(ids)
                        SelectionStore.exit()
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (favSelectionActive) Icons.Default.HeartBroken else Icons.Default.Delete,
                            contentDescription = if (favSelectionActive) {
                                stringResource(R.string.action_batch_unfavorite)
                            } else {
                                stringResource(R.string.action_batch_delete_history)
                            },
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { SelectionStore.exit() }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        CustomTabRow(
            tabs = topTabs,
            selectedTab = pagerState.currentPage,
            onTabSelected = { index ->
                coroutineScope.launch { pagerState.animateScrollToPage(index) }
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> FavoritesContent(
                    submissions = favoritesList,
                    isLoading = isLoading,
                    isLoadingMore = isLoadingMore,
                    errorMessage = errorMessage,
                    viewMode = viewMode,
                    columnsPref = columnsPref,
                    cardStats = cardStats,
                    selectionActive = favSelectionActive,
                    selectedIds = selection.ids,
                    onNavigateToPost = onNavigateToFavoritesPost,
                    onNavigateToUser = onNavigateToUser,
                    onLoadMore = { viewModel.loadMore() },
                    onRefresh = { viewModel.refresh() },
                    onRegisterScrollSource = onRegisterScrollSource,
                    scrollToTopTrigger = scrollToTopTrigger
                )
                1 -> WatchlistContent(
                    followingList = followingList,
                    followersList = followersList,
                    followingHasMore = followingHasMore,
                    followersHasMore = followersHasMore,
                    isLoading = isLoading,
                    isLoadingMore = isLoadingMore,
                    errorMessage = errorMessage,
                    currentTab = currentTab,
                    isGrid = viewMode != GalleryViewMode.LIST,
                    banners = userBanners,
                    bannerSource = bannerSource,
                    blurDp = watchlistBlur,
                    notes = userNotes,
                    onRefresh = { viewModel.refresh() },
                    onWarmBanners = { bannerStore.prefetch(it) },
                    onSetTab = { viewModel.setTab(it) },
                    onLoadMore = { viewModel.loadMore() },
                    onNavigateToUser = onNavigateToUser,
                    onEditNote = { noteEditingUser = it },
                    onRegisterScrollSource = onRegisterScrollSource,
                    scrollToTopTrigger = scrollToTopTrigger
                )
                2 -> HistoryContent(
                    entries = historyEntries,
                    userVisits = userVisits,
                    userNotes = userNotes,
                    viewMode = viewMode,
                    columnsPref = columnsPref,
                    cardStats = cardStats,
                    selectionActive = histSelectionActive,
                    selectedIds = selection.ids,
                    onWarmStats = onWarmStats,
                    onNavigateToPost = onNavigateToPost,
                    onNavigateToUser = onNavigateToUser,
                    onClear = onClearHistory,
                    onClearUsers = { coroutineScope.launch { userVisitCache.clear() } },
                    onRemoveVisit = { coroutineScope.launch { userVisitCache.remove(it) } },
                    onRegisterScrollSource = onRegisterScrollSource,
                    scrollToTopTrigger = scrollToTopTrigger
                )
            }
        }
    }

    noteEditingUser?.let { noteUser ->
        UserNoteDialog(
            user = noteUser,
            currentNote = userNotes[noteUser.username.lowercase()] ?: "",
            onSave = { note ->
                noteEditingUser = null
                coroutineScope.launch { repository.saveUserNote(noteUser.username, note) }
            },
            onDismiss = { noteEditingUser = null }
        )
    }
}

@Composable
private fun FavoritesContent(
    submissions: List<com.furflix.app.data.model.Submission>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    errorMessage: String?,
    viewMode: GalleryViewMode,
    columnsPref: Int,
    cardStats: Map<String, com.furflix.app.data.model.CardStats>,
    selectionActive: Boolean,
    selectedIds: Set<String>,
    onNavigateToPost: (String) -> Unit,
    onNavigateToUser: (String) -> Unit,
    onLoadMore: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onRegisterScrollSource: (((() -> Pair<Int, Int>?)?)) -> Unit = {},
    scrollToTopTrigger: Int = 0
) {
    PawPullRefreshBox(isRefreshing = isLoading, onRefresh = onRefresh) {
    when {
        isLoading && submissions.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        errorMessage != null && submissions.isEmpty() -> {
            EmptyState(message = errorMessage, icon = Icons.Default.ErrorOutline)
        }
        submissions.isEmpty() && !isLoadingMore -> {
            EmptyState(message = stringResource(R.string.profile_no_favorites), icon = Icons.Default.FavoriteBorder)
        }
        else -> {
            Crossfade(targetState = viewMode, animationSpec = tween(300), label = "favViewMode") { mode ->
                when (mode) {
                    GalleryViewMode.MASONRY -> {
                        val staggeredState = rememberLazyStaggeredGridState()
                        LiveScrollSourceEffect(register = onRegisterScrollSource) {
                            staggeredState.firstVisibleItemIndex to staggeredState.firstVisibleItemScrollOffset
                        }
                        LaunchedEffect(scrollToTopTrigger) {
                            if (scrollToTopTrigger > 0) staggeredState.animateScrollToItem(0)
                        }
                        LaunchedEffect(staggeredState) {
                            snapshotFlow {
                                val lastVisible = staggeredState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: 0
                                val total = staggeredState.layoutInfo.totalItemsCount
                                lastVisible >= total - 4
                            }.distinctUntilChanged().collect { shouldLoadMore ->
                                if (shouldLoadMore && !isLoadingMore && submissions.isNotEmpty()) {
                                    onLoadMore()
                                }
                            }
                        }
                        LazyVerticalStaggeredGrid(
                            state = staggeredState,
                            modifier = Modifier.fillMaxSize(),
                            columns = masonryGridCells(columnsPref),
                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 120.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalItemSpacing = 4.dp
                        ) {
                            staggeredItems(
                                items = submissions,
                                key = { it.id },
                                contentType = { "submission" }
                            ) { submission ->
                                SubmissionCard(
                                    submission = submission,
                                    onClick = {
                                        if (selectionActive) {
                                            SelectionStore.toggle(SELECTION_SOURCE_FAVORITES, submission.id)
                                        } else {
                                            onNavigateToPost(submission.id)
                                        }
                                    },
                                    showTitle = true,
                                    compact = true,
                                    ratio = submission.thumbnailWidthOnHeightRatio,
                                    stats = cardStats[submission.id],
                                    forceFavorited = true,
                                    selectionActive = selectionActive,
                                    isSelected = selectionActive && selectedIds.contains(submission.id),
                                    onLongClick = { SelectionStore.toggle(SELECTION_SOURCE_FAVORITES, submission.id) },
                                    onAuthorClick = { onNavigateToUser(it) }
                                )
                            }
                            if (isLoadingMore) {
                                item(
                                    span = StaggeredGridItemSpan.FullLine,
                                    key = "loading_more",
                                    contentType = "footer"
                                ) { LoadingMoreFooter() }
                            }
                        }
                    }
                    GalleryViewMode.GRID -> {
                    val gridState = rememberLazyGridState()
                    LiveScrollSourceEffect(register = onRegisterScrollSource) {
                        gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
                    }
                    LaunchedEffect(scrollToTopTrigger) {
                        if (scrollToTopTrigger > 0) gridState.animateScrollToItem(0)
                    }
                    LaunchedEffect(gridState) {
                        snapshotFlow {
                            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            val total = gridState.layoutInfo.totalItemsCount
                            lastVisible >= total - 4
                        }.distinctUntilChanged().collect { shouldLoadMore ->
                            if (shouldLoadMore && !isLoadingMore && submissions.isNotEmpty()) {
                                onLoadMore()
                            }
                        }
                    }
                    LazyVerticalGrid(
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        columns = galleryGridCells(columnsPref, isList = false),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(submissions, key = { it.id }, contentType = { "submission" }) { submission ->
                            SubmissionCard(
                                submission = submission,
                                onClick = {
                                    if (selectionActive) {
                                        SelectionStore.toggle(SELECTION_SOURCE_FAVORITES, submission.id)
                                    } else {
                                        onNavigateToPost(submission.id)
                                    }
                                },
                                showTitle = true,
                                compact = true,
                                stats = cardStats[submission.id],
                                forceFavorited = true,
                                selectionActive = selectionActive,
                                isSelected = selectionActive && selectedIds.contains(submission.id),
                                onLongClick = { SelectionStore.toggle(SELECTION_SOURCE_FAVORITES, submission.id) },
                                onAuthorClick = { onNavigateToUser(it) }
                            )
                        }
                        if (isLoadingMore) {
                            item(key = "loading_more", contentType = "footer") { LoadingMoreFooter() }
                        }
                    }
                    }
                    GalleryViewMode.LIST -> {
                    val listState = rememberLazyListState()
                    LiveScrollSourceEffect(register = onRegisterScrollSource) {
                        listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                    }
                    LaunchedEffect(scrollToTopTrigger) {
                        if (scrollToTopTrigger > 0) listState.animateScrollToItem(0)
                    }
                    LaunchedEffect(listState) {
                        snapshotFlow {
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            val total = listState.layoutInfo.totalItemsCount
                            lastVisible >= total - 4
                        }.distinctUntilChanged().collect { shouldLoadMore ->
                            if (shouldLoadMore && !isLoadingMore && submissions.isNotEmpty()) {
                                onLoadMore()
                            }
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(submissions, key = { it.id }, contentType = { "submission" }) { submission ->
                            SubmissionCard(
                                submission = submission,
                                onClick = {
                                    if (selectionActive) {
                                        SelectionStore.toggle(SELECTION_SOURCE_FAVORITES, submission.id)
                                    } else {
                                        onNavigateToPost(submission.id)
                                    }
                                },
                                stats = cardStats[submission.id],
                                forceFavorited = true,
                                selectionActive = selectionActive,
                                isSelected = selectionActive && selectedIds.contains(submission.id),
                                onLongClick = { SelectionStore.toggle(SELECTION_SOURCE_FAVORITES, submission.id) },
                                onAuthorClick = { onNavigateToUser(it) }
                            )
                        }
                        if (isLoadingMore) {
                            item(key = "loading_more", contentType = "footer") { LoadingMoreFooter() }
                        }
                    }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun HistoryContent(
    entries: List<com.furflix.app.data.model.Submission>,
    userVisits: List<com.furflix.app.data.local.UserVisitRecord>,
    userNotes: Map<String, String>,
    viewMode: GalleryViewMode,
    columnsPref: Int,
    cardStats: Map<String, com.furflix.app.data.model.CardStats>,
    selectionActive: Boolean,
    selectedIds: Set<String>,
    onWarmStats: (List<String>) -> Unit,
    onNavigateToPost: (String) -> Unit,
    onNavigateToUser: (String) -> Unit,
    onClear: () -> Unit,
    onClearUsers: () -> Unit,
    onRemoveVisit: (String) -> Unit,
    onRegisterScrollSource: (((() -> Pair<Int, Int>?)?)) -> Unit = {},
    scrollToTopTrigger: Int = 0
) {
    if (entries.isEmpty() && userVisits.isEmpty()) {
        EmptyState(message = stringResource(R.string.history_empty), icon = Icons.Default.History)
        return
    }
    // 小栏目：0 = 作品（沿用全局视图模式），1 = 用户（到访过的用户页，列表呈现）
    var historyTab by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            // 本地列表本身即时回流；刷新动作顺带把作品栏第一屏的统计重新预热，
            // 指示器按固定节奏收起，统计在后台就位后浮层自行更新
            onWarmStats(entries.take(24).map { it.id })
            delay(800)
            isRefreshing = false
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        CustomTabRow(
            tabs = listOf(
                stringResource(R.string.history_tab_submissions),
                stringResource(R.string.history_tab_users)
            ),
            selectedTab = historyTab,
            onTabSelected = { historyTab = it },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
        PawPullRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true }
        ) {
            Crossfade(targetState = historyTab, animationSpec = tween(300), label = "historySubTab") { tab ->
                when (tab) {
                    1 -> HistoryUsersContent(
                        visits = userVisits,
                        notes = userNotes,
                        onNavigateToUser = onNavigateToUser,
                        onClearUsers = onClearUsers,
                        onRemoveVisit = onRemoveVisit,
                        onRegisterScrollSource = onRegisterScrollSource,
                        scrollToTopTrigger = scrollToTopTrigger
                    )
                    else -> if (entries.isEmpty()) {
                        EmptyState(message = stringResource(R.string.history_empty), icon = Icons.Default.History)
                    } else {
                        HistorySubmissionsContent(
                            entries = entries,
                            viewMode = viewMode,
                            columnsPref = columnsPref,
                            cardStats = cardStats,
                            selectionActive = selectionActive,
                            selectedIds = selectedIds,
                            onNavigateToPost = onNavigateToPost,
                            onNavigateToUser = onNavigateToUser,
                            onClear = onClear,
                            onRegisterScrollSource = onRegisterScrollSource,
                            scrollToTopTrigger = scrollToTopTrigger
                        )
                    }
                }
            }
        }
    }
}

/** 浏览历史·作品：沿用全局网格/瀑布流/列表三视图，与画廊各页共用同一套卡片；长按多选批量删除。 */
@Composable
private fun HistorySubmissionsContent(
    entries: List<com.furflix.app.data.model.Submission>,
    viewMode: GalleryViewMode,
    columnsPref: Int,
    cardStats: Map<String, com.furflix.app.data.model.CardStats>,
    selectionActive: Boolean,
    selectedIds: Set<String>,
    onNavigateToPost: (String) -> Unit,
    onNavigateToUser: (String) -> Unit,
    onClear: () -> Unit,
    onRegisterScrollSource: (((() -> Pair<Int, Int>?)?)) -> Unit,
    scrollToTopTrigger: Int = 0
) {
    Crossfade(targetState = viewMode, animationSpec = tween(300), label = "historyViewMode") { mode ->
        when (mode) {
            GalleryViewMode.MASONRY -> {
                val staggeredState = rememberLazyStaggeredGridState()
                LiveScrollSourceEffect(register = onRegisterScrollSource) {
                    staggeredState.firstVisibleItemIndex to staggeredState.firstVisibleItemScrollOffset
                }
                LaunchedEffect(scrollToTopTrigger) {
                    if (scrollToTopTrigger > 0) staggeredState.animateScrollToItem(0)
                }
                LazyVerticalStaggeredGrid(
                    state = staggeredState,
                    modifier = Modifier.fillMaxSize(),
                    columns = masonryGridCells(columnsPref),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalItemSpacing = 4.dp
                ) {
                    item(
                        span = StaggeredGridItemSpan.FullLine,
                        key = "header",
                        contentType = "header"
                    ) { HistoryHeader(entries.size, onClear) }
                    staggeredItems(
                        items = entries,
                        key = { it.id },
                        contentType = { "submission" }
                    ) { submission ->
                        SubmissionCard(
                            submission = submission,
                            onClick = {
                                if (selectionActive) {
                                    SelectionStore.toggle(SELECTION_SOURCE_HISTORY, submission.id)
                                } else {
                                    onNavigateToPost(submission.id)
                                }
                            },
                            showTitle = true,
                            compact = true,
                            ratio = submission.thumbnailWidthOnHeightRatio,
                            stats = cardStats[submission.id],
                            forceFavorited = submission.isFavorited,
                            selectionActive = selectionActive,
                            isSelected = selectionActive && selectedIds.contains(submission.id),
                            onLongClick = { SelectionStore.toggle(SELECTION_SOURCE_HISTORY, submission.id) },
                            onAuthorClick = { onNavigateToUser(it) }
                        )
                    }
                }
            }
            GalleryViewMode.GRID -> {
                val gridState = rememberLazyGridState()
                LiveScrollSourceEffect(register = onRegisterScrollSource) {
                    gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
                }
                LaunchedEffect(scrollToTopTrigger) {
                    if (scrollToTopTrigger > 0) gridState.animateScrollToItem(0)
                }
                LazyVerticalGrid(
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    columns = galleryGridCells(columnsPref, isList = false),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item(key = "header", contentType = "header") { HistoryHeader(entries.size, onClear) }
                    items(entries, key = { it.id }, contentType = { "submission" }) { submission ->
                        SubmissionCard(
                            submission = submission,
                            onClick = {
                                if (selectionActive) {
                                    SelectionStore.toggle(SELECTION_SOURCE_HISTORY, submission.id)
                                } else {
                                    onNavigateToPost(submission.id)
                                }
                            },
                            showTitle = true,
                            compact = true,
                            stats = cardStats[submission.id],
                            forceFavorited = submission.isFavorited,
                            selectionActive = selectionActive,
                            isSelected = selectionActive && selectedIds.contains(submission.id),
                            onLongClick = { SelectionStore.toggle(SELECTION_SOURCE_HISTORY, submission.id) },
                            onAuthorClick = { onNavigateToUser(it) }
                        )
                    }
                }
            }
            GalleryViewMode.LIST -> {
                val listState = rememberLazyListState()
                LiveScrollSourceEffect(register = onRegisterScrollSource) {
                    listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                }
                LaunchedEffect(scrollToTopTrigger) {
                    if (scrollToTopTrigger > 0) listState.animateScrollToItem(0)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "header", contentType = "header") { HistoryHeader(entries.size, onClear) }
                    items(entries, key = { it.id }, contentType = { "submission" }) { submission ->
                        SubmissionCard(
                            submission = submission,
                            onClick = {
                                if (selectionActive) {
                                    SelectionStore.toggle(SELECTION_SOURCE_HISTORY, submission.id)
                                } else {
                                    onNavigateToPost(submission.id)
                                }
                            },
                            stats = cardStats[submission.id],
                            forceFavorited = submission.isFavorited,
                            selectionActive = selectionActive,
                            isSelected = selectionActive && selectedIds.contains(submission.id),
                            onLongClick = { SelectionStore.toggle(SELECTION_SOURCE_HISTORY, submission.id) },
                            onAuthorClick = { onNavigateToUser(it) }
                        )
                    }
                }
            }
        }
    }
}

/** 浏览历史·用户：到访过的用户页，复用关注列表的行卡片（头像 + 名字 + 备注）；长按删除该条。 */
@Composable
private fun HistoryUsersContent(
    visits: List<com.furflix.app.data.local.UserVisitRecord>,
    notes: Map<String, String>,
    onNavigateToUser: (String) -> Unit,
    onClearUsers: () -> Unit,
    onRemoveVisit: (String) -> Unit,
    onRegisterScrollSource: (((() -> Pair<Int, Int>?)?)) -> Unit,
    scrollToTopTrigger: Int = 0
) {
    val listState = rememberLazyListState()
    LiveScrollSourceEffect(register = onRegisterScrollSource) {
        listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
    }
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) listState.animateScrollToItem(0)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "header", contentType = "header") { HistoryHeader(visits.size, onClearUsers) }
        items(
            visits,
            key = { it.username.lowercase() },
            contentType = { "user_visit" }
        ) { visit ->
            WatchlistItem(
                user = com.furflix.app.data.model.WatchlistUser(
                    username = visit.username,
                    displayName = visit.displayName.ifEmpty { visit.username },
                    avatarUrl = visit.avatarUrl
                ),
                note = notes[visit.username.lowercase()],
                onClick = { onNavigateToUser(visit.username) },
                onLongClick = { onRemoveVisit(visit.username) }
            )
        }
    }
}

@Composable
private fun HistoryHeader(count: Int, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.storage_entries_count, count),
            style = MaterialTheme.typography.labelLarge,
            color = SubtleGray
        )
        TextButton(onClick = onClear) {
            Text(
                stringResource(R.string.search_clear_recent),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun WatchlistContent(
    followingList: List<com.furflix.app.data.model.WatchlistUser>,
    followersList: List<com.furflix.app.data.model.WatchlistUser>,
    followingHasMore: Boolean,
    followersHasMore: Boolean,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    errorMessage: String?,
    currentTab: Int,
    isGrid: Boolean,
    banners: Map<String, String>,
    bannerSource: String,
    blurDp: Float,
    notes: Map<String, String>,
    onRefresh: () -> Unit,
    onWarmBanners: (List<String>) -> Unit,
    onSetTab: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onNavigateToUser: (String) -> Unit,
    onEditNote: (WatchlistUser) -> Unit,
    onRegisterScrollSource: (((() -> Pair<Int, Int>?)?)) -> Unit = {},
    scrollToTopTrigger: Int = 0
) {
    val currentData = if (currentTab == WatchlistViewModel.TAB_FOLLOWING) followingList else followersList
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    // 本地搜索：用户名 / 显示名 / 备注任一命中（只过滤已加载条目，翻页照常）
    var searchQuery by remember { mutableStateOf("") }
    val displayData = remember(currentData, searchQuery, notes) {
        if (searchQuery.isBlank()) currentData else currentData.filter { user ->
            user.username.contains(searchQuery, ignoreCase = true) ||
                user.displayName.contains(searchQuery, ignoreCase = true) ||
                (notes[user.username.lowercase()] ?: "").contains(searchQuery, ignoreCase = true)
        }
    }

    // 可见用户卡片的横幅预热：滚到谁就补谁的资料页横幅（共享仓库串行限速、去重）；
    // bannerSource 入 key：设置切换来源后重置本轮去重，可见卡片按新来源重建
    val warmedUsers = remember(bannerSource) { mutableSetOf<String>() }
    LaunchedEffect(displayData, isGrid, bannerSource) {
        if (displayData.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            // grid/list 两种 State 类型没有公共 layoutInfo，分支放流内取值
            if (isGrid) {
                gridState.layoutInfo.visibleItemsInfo
                    .mapNotNull { displayData.getOrNull(it.index)?.username }
            } else {
                listState.layoutInfo.visibleItemsInfo
                    .mapNotNull { displayData.getOrNull(it.index)?.username }
            }
        }.collect { visible ->
            val fresh = visible.filter { warmedUsers.add(it) }
            if (fresh.isNotEmpty()) onWarmBanners(fresh)
        }
    }

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            try { gridState.animateScrollToItem(0) } catch (_: Exception) {}
            try { listState.animateScrollToItem(0) } catch (_: Exception) {}
        }
    }

    val followingCount = followingList.size.let { if (followingHasMore) "${it}+" else "$it" }
    val followersCount = followersList.size.let { if (followersHasMore) "${it}+" else "$it" }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isFollowing = currentTab == WatchlistViewModel.TAB_FOLLOWING

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSetTab(WatchlistViewModel.TAB_FOLLOWING) }
                    .background(if (isFollowing) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.profile_following_list).uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    color = if (isFollowing) MaterialTheme.colorScheme.primary else SubtleGray
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isFollowing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        followingCount,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isFollowing) MaterialTheme.colorScheme.onPrimary else SubtleGray
                    )
                }
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSetTab(WatchlistViewModel.TAB_FOLLOWERS) }
                    .background(if (!isFollowing) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.profile_followers_list).uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    color = if (!isFollowing) MaterialTheme.colorScheme.primary else SubtleGray
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (!isFollowing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        followersCount,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (!isFollowing) MaterialTheme.colorScheme.onPrimary else SubtleGray
                    )
                }
            }

            Spacer(Modifier.weight(1f))
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    stringResource(R.string.watchlist_search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SubtleGray
                )
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SubtleGray) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.search_clear_recent),
                            tint = SubtleGray
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        PawPullRefreshBox(isRefreshing = isLoading, onRefresh = onRefresh) {
        when {
            isLoading && currentData.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            errorMessage != null && currentData.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, null, Modifier.size(64.dp), tint = SubtleGray)
                        Spacer(Modifier.height(16.dp))
                        Text(errorMessage, style = MaterialTheme.typography.bodyLarge, color = SubtleGray, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
                    }
                }
            }
            else -> {
                if (displayData.isEmpty() && currentData.isNotEmpty()) {
                    // 本地列表非空但搜索无命中
                    EmptyState(
                        message = stringResource(R.string.watchlist_search_empty),
                        icon = Icons.Default.PersonSearch
                    )
                } else {
                Crossfade(targetState = isGrid, animationSpec = tween(300), label = "watchlistGridList") { grid ->
                    if (grid) {
                        LiveScrollSourceEffect(register = onRegisterScrollSource) {
                            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
                        }
                        LazyVerticalGrid(
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(displayData, key = { it.username }, contentType = { "watch_user" }) { user ->
                                WatchlistGridItem(
                                    user = user,
                                    bannerUrl = banners[user.username.lowercase()],
                                    blurDp = blurDp,
                                    note = notes[user.username.lowercase()],
                                    onClick = { onNavigateToUser(user.username) },
                                    onLongClick = { onEditNote(user) }
                                )
                            }
                            if (isLoadingMore) {
                                item(key = "loading_more", contentType = "footer") { LoadingMoreFooter() }
                            }
                        }
                    } else {
                        LiveScrollSourceEffect(register = onRegisterScrollSource) {
                            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(displayData, key = { it.username }, contentType = { "watch_user" }) { user ->
                                WatchlistItem(
                                    user = user,
                                    note = notes[user.username.lowercase()],
                                    onClick = { onNavigateToUser(user.username) },
                                    onLongClick = { onEditNote(user) }
                                )
                            }
                            if (isLoadingMore) {
                                item(key = "loading_more", contentType = "footer") { LoadingMoreFooter() }
                            }
                        }
                    }
                }

                LaunchedEffect(gridState, listState, currentTab) {
                    snapshotFlow {
                        if (isGrid) {
                            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            val total = gridState.layoutInfo.totalItemsCount
                            lastVisible >= total - 5
                        } else {
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            val total = listState.layoutInfo.totalItemsCount
                            lastVisible >= total - 5
                        }
                    }.collect { shouldLoadMore ->
                        if (shouldLoadMore && !isLoadingMore && currentData.isNotEmpty()) {
                            onLoadMore()
                        }
                    }
                }
                }
            }
        }
        }
    }
}
