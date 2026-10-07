package com.furflix.app.ui.screens

import androidx.compose.material3.MaterialTheme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.request.ImageRequest
import com.furflix.app.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import com.furflix.app.ui.components.SubmissionCard
import com.furflix.app.ui.components.galleryGridCells
import com.furflix.app.ui.components.masonryGridCells
import com.furflix.app.ui.components.EmptyState
import com.furflix.app.ui.components.ImageLoaderFactory
import com.furflix.app.ui.components.LocalThumbnailQuality
import com.furflix.app.ui.components.GlassmorphicBottomBar
import com.furflix.app.ui.components.rememberBarsVisibility
import com.furflix.app.ui.components.LiveScrollSourceEffect
import com.furflix.app.data.selection.SelectionStore
import com.furflix.app.data.download.DownloadManager
import com.furflix.app.ui.components.GlassBarTab
import com.furflix.app.ui.components.SiteBannerStrip
import com.furflix.app.ui.components.FurryTabIcons
import dev.chrisbanes.haze.HazeState
import com.furflix.app.ui.components.HazeTopAppBar
import com.furflix.app.ui.components.LoadingMoreFooter
import com.furflix.app.ui.components.PawPullIndicator
import dev.chrisbanes.haze.hazeSource

import com.furflix.app.ui.theme.*
import com.furflix.app.viewmodel.MainViewModel
import com.furflix.app.data.FA_CATEGORIES
import com.furflix.app.data.FA_ART_TYPES
import com.furflix.app.data.FA_GENDER_OPTIONS
import com.furflix.app.data.model.GalleryViewMode
import com.furflix.app.data.model.Submission
import com.furflix.app.data.model.addTagToken
import com.furflix.app.data.model.toggleTagExclusion
import com.furflix.app.data.remote.DynamicThumbnail
import com.furflix.app.data.repository.FurRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPost: (String) -> Unit,
    onNavigateToUser: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToFavoritesPost: (String) -> Unit = onNavigateToPost,
    onOpenCollectedFavorites: (String) -> Unit = {},
    viewModel: MainViewModel = viewModel(),
    watchlistViewModel: com.furflix.app.viewmodel.WatchlistViewModel = viewModel()
) {
    val browseSubmissions by viewModel.browseSubmissions.collectAsState()
    val latestSubmissions by viewModel.latestSubmissions.collectAsState()
    val searchSubmissions by viewModel.searchSubmissions.collectAsState()
    val cardStats by viewModel.cardStats.collectAsState()
    val currentSearchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val nsfwEnabled by viewModel.nsfwEnabled.collectAsState()

    val selectedTab by viewModel.selectedTab.collectAsState()
    val hasSearched by viewModel.hasSearched.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val searchPresets by viewModel.searchPresets.collectAsState()
    // 收藏夹页(page 3)的下拉刷新走 WatchlistViewModel,复位条件要一并看它的加载态
    val watchlistIsLoading by watchlistViewModel.isLoading.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Resolve tab labels as plain strings first — stringResource() is @Composable,
    // so it must not be called inside `remember { ... }`.
    val tabBrowse = stringResource(R.string.nav_tab_browse)
    val tabLatest = stringResource(R.string.nav_tab_latest)
    val tabSearch = stringResource(R.string.nav_tab_search)
    val tabProfile = stringResource(R.string.nav_tab_profile)

    val hazeState = remember { HazeState() }
    val bottomBarItems = remember(tabBrowse, tabLatest, tabSearch, tabProfile) {
        listOf(
            GlassBarTab(tabBrowse, FurryTabIcons.PawOutline, TabBrowseAccent),
            GlassBarTab(tabLatest, FurryTabIcons.TailSpark, TabLatestAccent),
            GlassBarTab(tabSearch, FurryTabIcons.SharkSearch, TabSearchAccent),
            GlassBarTab(tabProfile, FurryTabIcons.MuzzleProfile, TabProfileAccent)
        )
    }

    // View options
    val context = LocalContext.current
    val repository = remember { FurRepository.getInstance(context) }

    // 今日横幅（浏览/最新/搜索/我的共用顶栏）：与启动页共用同一份当日缓存；
    // 当日未命中时先读一次联网刷新开关再决定是否补抓（关=仅用本地缓存，零流量）
    var siteBannerUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val refreshAllowed = repository.splashBannerRefreshFlow.first()
        siteBannerUrl = repository.loadSiteBanner()
            ?: if (refreshAllowed) repository.refreshSiteBanner()
            else repository.loadSiteBannerCached()
    }
    val persistedViewMode by repository.viewModeFlow.collectAsState(initial = GalleryViewMode.GRID)
    val gridColumnsPref by repository.gridColumnsFlow.collectAsState(initial = 2)
    // 预取用的自适应列数估算；卡片自己还会按实测宽度协商，这里只是把该暖的档位暖对
    val estimatedMasonryLanes = if (gridColumnsPref <= 0) {
        (LocalConfiguration.current.screenWidthDp / 160).coerceIn(2, 5)
    } else gridColumnsPref
    // 乐观覆盖：DataStore 落盘→回流的延迟会让切换晚一拍才重绘；先本地生效，
    // 持久化值回流后对齐（与 Downloads 页 isGrid 开关的本地先行做法一致）
    var pendingViewMode by remember { mutableStateOf<GalleryViewMode?>(null) }
    val viewMode = pendingViewMode ?: persistedViewMode
    LaunchedEffect(persistedViewMode) {
        if (pendingViewMode == persistedViewMode) pendingViewMode = null
    }

    // 搜索结果视图（紧凑/瀑布流/大图/列表）：搜索页独立偏好，乐观覆盖同 viewMode
    val persistedSearchView by repository.searchViewModeFlow.collectAsState(
        initial = com.furflix.app.data.model.SearchResultView.MASONRY
    )
    var pendingSearchView by remember { mutableStateOf<com.furflix.app.data.model.SearchResultView?>(null) }
    val searchView = pendingSearchView ?: persistedSearchView
    LaunchedEffect(persistedSearchView) {
        if (pendingSearchView == persistedSearchView) pendingSearchView = null
    }

    // 缩略图标题开关（'T' 图标）为全局偏好：“我的”页收藏/历史栏目同源生效；
    // 乐观覆盖先行生效，DataStore 回流后对齐（与 viewMode 开关同一做法）
    val persistedShowTitles by repository.cardTitlesFlow.collectAsState(initial = true)
    var pendingShowTitles by remember { mutableStateOf<Boolean?>(null) }
    val showTitles = pendingShowTitles ?: persistedShowTitles
    LaunchedEffect(persistedShowTitles) {
        if (pendingShowTitles == persistedShowTitles) pendingShowTitles = null
    }

    LaunchedEffect(Unit) {
        viewModel.tryAutoLogin()
    }

    var backPressedTime by remember { mutableLongStateOf(0L) }
    val activity = context as? android.app.Activity
    val exitToastText = stringResource(R.string.exit_press_back_again)
    // lint LocalContextGetResourceValueCall：资源值在组合层先解析成值，回调只捕获结果
    val historyRemovedTemplate = stringResource(R.string.toast_history_removed)
    androidx.activity.compose.BackHandler {
        if (System.currentTimeMillis() - backPressedTime < 2000) {
            activity?.finish()
        } else {
            backPressedTime = System.currentTimeMillis()
            android.widget.Toast.makeText(context, exitToastText, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Page index → scroll-to-top action; re-registered by whichever list
    // container (grid or staggered grid) currently owns the page.
    val scrollers = remember { mutableMapOf<Int, suspend () -> Unit>() }
    // 各 tab 当前列表的滚动位置源，供顶底栏显隐判定；值可为 null = 当前 tab 无存活列表
    val scrollSources = remember { mutableMapOf<Int, (() -> Pair<Int, Int>?)?>() }

        fun registerScrollSource(tab: Int, source: (() -> Pair<Int, Int>?)?) {
            if (source == null) scrollSources.remove(tab) else scrollSources[tab] = source
        }

    val autoHideBars by repository.hideBarsOnScrollFlow.collectAsState(initial = true)
    val barsVisibility = rememberBarsVisibility(selectedTab) { scrollSources[selectedTab]?.invoke() }
    val barsVisible = !autoHideBars || barsVisibility.visible

    // 多选批量（浏览/最新/搜索）
    val selection by com.furflix.app.data.selection.SelectionStore.state.collectAsState()
    val currentSource = when (selectedTab) { 0 -> "browse"; 1 -> "latest"; 2 -> "search"; else -> "profile" }
    val selectionActive = selection.active && selection.source == currentSource
    var profileScrollToTopTrigger by remember { mutableIntStateOf(0) }

    val focusManager = LocalFocusManager.current
    Scaffold(
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = {
                focusManager.clearFocus()
            })
        },
        topBar = {
            AnimatedVisibility(
                visible = barsVisible || selectionActive,
                enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it }
            ) {
            // 今日横幅条（FA 官网同款页头顺序）：浏览/最新/搜索/我的四 tab 共用顶栏，
            // 取到当日 URL 才渲染；随顶栏一起滚动隐藏/唤回
            Column {
                siteBannerUrl?.let { url -> SiteBannerStrip(url = url) }
            HazeTopAppBar(
                title = {
                    if (selectionActive) {
                        Text(
                            text = stringResource(R.string.selection_mode_n, selection.ids.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "FurFlix",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                hazeState = hazeState,
                actions = {
                    if (selectionActive) {
                        val batchQueuedText = stringResource(R.string.toast_batch_download_queued, selection.ids.size)
                        IconButton(onClick = {
                            val ids = when (selectedTab) {
                                0 -> viewModel.browseSubmissions.value
                                1 -> viewModel.latestSubmissions.value
                                else -> viewModel.searchSubmissions.value
                            }.map { it.id }
                            SelectionStore.selectAll(currentSource, ids)
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.DoneAll, contentDescription = stringResource(R.string.action_select_all), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { viewModel.batchFavorite(selection.ids.toList()) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Favorite, contentDescription = stringResource(R.string.action_batch_favorite), tint = Color(0xFFFF007F), modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = {
                            selection.ids.forEach { DownloadManager.enqueueDownload(context, it) }
                            android.widget.Toast.makeText(context, batchQueuedText, android.widget.Toast.LENGTH_SHORT).show()
                            SelectionStore.exit()
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Download, contentDescription = stringResource(R.string.action_batch_download), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { SelectionStore.exit() }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    } else {
                    IconButton(onClick = onNavigateToDownloads) {
                        Icon(Icons.Default.Download, contentDescription = stringResource(R.string.nav_tab_downloads), tint = MaterialTheme.colorScheme.primary)
                    }
                    // NSFW toggle pill
                    NsfwTogglePill(
                        nsfwEnabled = nsfwEnabled,
                        onToggle = { 
                            viewModel.toggleNsfw()
                            coroutineScope.launch {
                                try {
                                    scrollers[selectedTab]?.invoke()
                                } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    // Gallery layout: grid → masonry → list
                    // 搜索 tab 不显示：搜索页自有的 InlineFiltersRow 视图切换与其重复，
                    // 双顶栏同现"两套画廊视图选项"即由此而来
                    if (selectedTab != MainViewModel.TAB_SEARCH) {
                    IconButton(
                        onClick = {
                            val next = when (viewMode) {
                                GalleryViewMode.GRID -> GalleryViewMode.MASONRY
                                GalleryViewMode.MASONRY -> GalleryViewMode.LIST
                                GalleryViewMode.LIST -> GalleryViewMode.GRID
                            }
                            pendingViewMode = next
                            coroutineScope.launch {
                                repository.saveViewMode(next)
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = when (viewMode) {
                                GalleryViewMode.GRID -> Icons.Default.GridView
                                GalleryViewMode.MASONRY -> Icons.Default.Dashboard
                                GalleryViewMode.LIST -> Icons.AutoMirrored.Filled.ViewList
                            },
                            contentDescription = stringResource(R.string.cd_toggle_view_mode),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Show titles toggle
                    IconButton(onClick = {
                        pendingShowTitles = !showTitles
                        coroutineScope.launch { repository.saveCardTitles(!showTitles) }
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Title,
                            contentDescription = stringResource(R.string.cd_toggle_titles),
                            tint = if (showTitles) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    }

                    // Settings
                    IconButton(onClick = onNavigateToSettings, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.cd_settings),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    }
                }
            )
            }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val pagerState = rememberPagerState(
                initialPage = selectedTab,
                pageCount = { 4 }
            )

            val loadedTabs = remember { mutableSetOf<Int>() }

            // Sync: user swipe → update selectedTab
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }
                    .distinctUntilChanged()
                    .collect { page ->
                            if (page != selectedTab) {
                                viewModel.setSelectedTab(page)
                                if (page !in loadedTabs) {
                                    when (page) {
                                        // 首次进入 tab：缓存优先（本地有就先出，不自动联网）
                                        0 -> viewModel.loadBrowse(allowCacheRestore = true)
                                        1 -> viewModel.loadLatest(allowCacheRestore = true)
                                        2 -> {}
                                        3 -> {}
                                    }
                                    loadedTabs.add(page)
                                }
                            }
                    }
            }

            // Sync: selectedTab change → scroll pager
            LaunchedEffect(selectedTab) {
                if (selectedTab != pagerState.currentPage) {
                    pagerState.scrollToPage(selectedTab)
                }
            }

            // Pull-to-refresh
            var isRefreshing by remember { mutableStateOf(false) }
            var pullProgress by remember { mutableFloatStateOf(0f) }
            // 与 PawPullRefreshBox 同一阻尼参数：0.65 倍率累积 + 下调阈值
            val pullThreshold = 230f
            var hasTriggered by remember { mutableStateOf(false) }

            LaunchedEffect(isLoading, watchlistIsLoading, isRefreshing) {
                if (!isLoading && !watchlistIsLoading && isRefreshing) {
                    isRefreshing = false
                    pullProgress = 0f
                    hasTriggered = false
                }
            }

            val nestedScrollConnection = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = Offset.Zero

                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset {
                        // 第 3 页（我的）有自己的 PawPullRefreshBox：外层不再参与，
                        // 否则内层刷新期间继续下拉会同时弹出两个爪印
                        if (available.y > 0 && source == NestedScrollSource.UserInput && !isRefreshing &&
                            pagerState.currentPage != 3
                        ) {
                            pullProgress = (pullProgress + available.y * 0.65f).coerceIn(0f, pullThreshold * 1.5f)
                            if (pullProgress >= pullThreshold && !hasTriggered) {
                                hasTriggered = true
                                isRefreshing = true
                                when (pagerState.currentPage) {
                                    0 -> viewModel.loadBrowse()
                                    1 -> viewModel.loadLatest()
                                    2 -> viewModel.loadSearch(currentSearchQuery)
                                }
                            }
                            return Offset(0f, available.y)
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        if (pullProgress > 0 && !isRefreshing) {
                            pullProgress = 0f
                        }
                        return Velocity.Zero
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().hazeSource(state = hazeState),
                    beyondViewportPageCount = 1
            ) { page ->
                val pageSource = when (page) { 0 -> "browse"; 1 -> "latest"; 2 -> "search"; else -> "profile" }
                val pageSelectionActive = selectionActive && selection.source == pageSource
                when (page) {
                    0, 1 -> {
                        val isList = viewMode == GalleryViewMode.LIST
                        val gridPadding = if (isList) 12.dp else 6.dp
                        val itemSpacing = if (isList) 12.dp else 4.dp
                        val compact = !isList
                        val bottomBarClearance = 120.dp
                        val submissions = if (page == 0) browseSubmissions else latestSubmissions

                        Box(modifier = Modifier.fillMaxSize()) {
                        if (page == 1 && !isLoggedIn) {
                            LoginContent(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = bottomBarClearance),
                                onLoginSuccess = { viewModel.loadLatest() },
                                viewModel = viewModel
                            )
                        } else if (isLoading && submissions.isEmpty()) {
                            // 首屏骨架：与目标网格同构占位，加载完成零跳动
                            Box(modifier = Modifier.fillMaxSize()) {
                                com.furflix.app.ui.components.GallerySkeletonPlaceholder(
                                    viewMode = viewMode,
                                    gridColumnsPref = gridColumnsPref,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (!isLoggedIn) {
                                    Text(
                                        text = stringResource(R.string.home_not_logged_in),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 24.dp)
                                    )
                                }
                            }
                        } else if (errorMessage != null && submissions.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = errorMessage ?: "",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            viewModel.clearError()
                                            if (page == 0) viewModel.loadBrowse() else viewModel.loadLatest()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text(stringResource(R.string.action_retry))
                                    }
                                }
                            }
                        } else {
                            if (viewMode == GalleryViewMode.MASONRY) {
                                val staggeredState = rememberLazyStaggeredGridState()
                                SideEffect {
                    scrollers[page] = { staggeredState.animateScrollToItem(0) }
                }
                                LiveScrollSourceEffect(register = { source -> registerScrollSource(page, source) }) {
                                    staggeredState.firstVisibleItemIndex to staggeredState.firstVisibleItemScrollOffset
                                }
                                DisposableEffect(Unit) { onDispose { scrollers.keys.remove(page) } }
                                LazyVerticalStaggeredGrid(
                                    columns = masonryGridCells(gridColumnsPref),
                                    state = staggeredState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = gridPadding,
                                        end = gridPadding,
                                        top = gridPadding,
                                        bottom = gridPadding + bottomBarClearance
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                                    verticalItemSpacing = itemSpacing
                                ) {
                                    staggeredItems(
                                        items = submissions,
                                        key = { it.id },
                                        contentType = { "submission" }
                                    ) { submission ->
                                        SubmissionCard(
                                            modifier = Modifier.animateItem(),
                                            submission = submission,
                                            onClick = {
                    if (pageSelectionActive) {
                        SelectionStore.toggle(pageSource, submission.id)
                    } else {
                        viewModel.prefetchSubmission(submission.id)
                        onNavigateToPost(submission.id)
                    }
                },
                selectionActive = pageSelectionActive,
                isSelected = pageSelectionActive && selection.ids.contains(submission.id),
                onLongClick = { SelectionStore.toggle(pageSource, submission.id) },
                                            showTitle = showTitles,
                                            compact = compact,
                                            ratio = submission.thumbnailWidthOnHeightRatio,
                                            stats = com.furflix.app.ui.components.rememberCardStats(cardStats, submission.id),
                                            onAuthorClick = { onNavigateToUser(it) }
                                        )
                                    }

                                    if (isLoadingMore) {
                                        item(
                                            span = StaggeredGridItemSpan.FullLine,
                                            key = "loading_more",
                                            contentType = "footer"
                                        ) {
                                            LoadingMoreFooter()
                                        }
                                    }
                                }

                                LaunchedEffect(staggeredState) {
                                    snapshotFlow {
                                        val lastVisibleItem = staggeredState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: 0
                                        val totalItems = staggeredState.layoutInfo.totalItemsCount
                                        lastVisibleItem >= totalItems - (if (compact) 6 else 3)
                                    }.collect { shouldLoadMore ->
                                        if (shouldLoadMore && !isLoadingMore && submissions.isNotEmpty()) {
                                            viewModel.loadMore()
                                        }
                                    }
                                }

                                MasonryPrefetchEffect(
                                    submissions = submissions,
                                    active = selectedTab == page,
                                    laneCount = estimatedMasonryLanes
                                )
                            } else {
                                val gridState = rememberLazyGridState()
                                SideEffect {
                                    scrollers[page] = { gridState.animateScrollToItem(0) }
                                }
                                LiveScrollSourceEffect(register = { source -> registerScrollSource(page, source) }) {
                                    gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
                                }
                                DisposableEffect(Unit) { onDispose { scrollers.keys.remove(page) } }
                                LazyVerticalGrid(
                                    modifier = Modifier.fillMaxSize(),
                                    columns = galleryGridCells(gridColumnsPref, isList),
                                    state = gridState,
                                    contentPadding = PaddingValues(
                                        start = gridPadding,
                                        end = gridPadding,
                                        top = gridPadding,
                                        bottom = gridPadding + bottomBarClearance
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                                    verticalArrangement = Arrangement.spacedBy(itemSpacing)
                                ) {
                                    items(
                                        items = submissions,
                                        key = { it.id },
                                        contentType = { "submission" }
                                    ) { submission ->
                                        SubmissionCard(
                                            modifier = Modifier.animateItem(),
                                            submission = submission,
                                            onClick = {
                    if (pageSelectionActive) {
                        SelectionStore.toggle(pageSource, submission.id)
                    } else {
                        viewModel.prefetchSubmission(submission.id)
                        onNavigateToPost(submission.id)
                    }
                },
                selectionActive = pageSelectionActive,
                isSelected = pageSelectionActive && selection.ids.contains(submission.id),
                onLongClick = { SelectionStore.toggle(pageSource, submission.id) },
                                            showTitle = showTitles,
                                            compact = compact,
                                            stats = com.furflix.app.ui.components.rememberCardStats(cardStats, submission.id),
                                            onAuthorClick = { onNavigateToUser(it) }
                                        )
                                    }

                                    if (isLoadingMore) {
                                        item {
                                            LoadingMoreFooter()
                                        }
                                    }
                                }

                                LaunchedEffect(gridState) {
                                    snapshotFlow {
                                        val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                        val totalItems = gridState.layoutInfo.totalItemsCount
                                        lastVisibleItem >= totalItems - (if (compact) 6 else 3)
                                    }.collect { shouldLoadMore ->
                                        if (shouldLoadMore && !isLoadingMore && submissions.isNotEmpty()) {
                                            viewModel.loadMore()
                                        }
                                    }
                                }
                            }
                        }

                        // 缓存优先提示条：当前列表整页来自本地缓存（秒开零流量），下拉刷新即拉最新
                        val browseRestored by viewModel.browseRestoredFromCache.collectAsState()
                        val latestRestored by viewModel.latestRestoredFromCache.collectAsState()
                        val showCachedHint = when (page) {
                            0 -> browseRestored
                            1 -> latestRestored
                            else -> false
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showCachedHint,
                            enter = fadeIn() + slideInVertically { -it / 2 },
                            exit = fadeOut() + slideOutVertically { -it / 2 },
                            modifier = Modifier.align(Alignment.TopCenter)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.Black.copy(alpha = 0.55f),
                                modifier = Modifier.padding(top = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CloudOff,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.list_restored_from_cache),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                        }
                    }
                    2 -> {
                        // 搜索结果视图参数：紧凑=自适应多列无标题 / 瀑布流=跟随全局 / 大图=2 列带标题 / 列表
                        val isList = searchView == com.furflix.app.data.model.SearchResultView.LIST
                        val isMasonry = searchView == com.furflix.app.data.model.SearchResultView.MASONRY
                        val searchColumnsPref = when (searchView) {
                            com.furflix.app.data.model.SearchResultView.COMPACT -> 0
                            com.furflix.app.data.model.SearchResultView.LARGE -> 2
                            else -> gridColumnsPref
                        }
                        val searchShowTitles = when (searchView) {
                            com.furflix.app.data.model.SearchResultView.COMPACT -> false
                            com.furflix.app.data.model.SearchResultView.LARGE -> true
                            else -> showTitles
                        }
                        val gridPadding = if (isList) 12.dp else 6.dp
                        val itemSpacing = if (isList) 12.dp else 4.dp
                        val searchGridState = rememberLazyGridState()
                        val searchStaggeredState = rememberLazyStaggeredGridState()
                        var isHeaderVisible by remember { mutableStateOf(true) }

                        // Header visibility tracks whichever list owns the scroll.
                        LaunchedEffect(searchStaggeredState, searchView) {
                            if (!isMasonry) return@LaunchedEffect
                            var prevOffset = searchStaggeredState.firstVisibleItemScrollOffset
                            var prevIndex = searchStaggeredState.firstVisibleItemIndex
                            snapshotFlow { searchStaggeredState.firstVisibleItemScrollOffset to searchStaggeredState.firstVisibleItemIndex }
                                .collect { (offset, index) ->
                                    if (index == 0 && offset < 50) {
                                        isHeaderVisible = true
                                    } else {
                                        val dy = if (index == prevIndex) offset - prevOffset else if (index > prevIndex) 100 else -100
                                        if (dy > 15) isHeaderVisible = false
                                        else if (dy < -15) isHeaderVisible = true
                                    }
                                    prevOffset = offset
                                    prevIndex = index
                                }
                        }

                        LaunchedEffect(searchGridState, searchView) {
                            if (isMasonry) return@LaunchedEffect
                            var prevOffset = searchGridState.firstVisibleItemScrollOffset
                            var prevIndex = searchGridState.firstVisibleItemIndex
                            snapshotFlow { searchGridState.firstVisibleItemScrollOffset to searchGridState.firstVisibleItemIndex }
                                .collect { (offset, index) ->
                                    if (index == 0 && offset < 50) {
                                        isHeaderVisible = true
                                    } else {
                                        val dy = if (index == prevIndex) offset - prevOffset else if (index > prevIndex) 100 else -100
                                        if (dy > 15) isHeaderVisible = false
                                        else if (dy < -15) isHeaderVisible = true
                                    }
                                    prevOffset = offset
                                    prevIndex = index
                                }
                        }

                        var showFilters by remember { mutableStateOf(false) }
                        val searchFilters by viewModel.searchFilters.collectAsState()
                        var draftFilters by remember(searchFilters) { mutableStateOf(searchFilters) }
                        val searchQuery = viewModel.searchQuery.collectAsState().value

                        // Scroll to top whenever a new search query is fired
                        LaunchedEffect(searchQuery) {
                            if (searchQuery.isNotEmpty()) {
                                searchGridState.scrollToItem(0)
                            }
                        }
                        
                        androidx.activity.compose.BackHandler(enabled = (pagerState.currentPage == 2 && hasSearched)) {
                            if (showFilters) {
                                showFilters = false
                            } else {
                                viewModel.resetSearch()
                            }
                        }

                        
                        Column(modifier = Modifier.fillMaxSize()) {
                        
                        val tagSuggestions by viewModel.tagSuggestions.collectAsState()
                        // 服务端检索统计（#query-stats）：逐词命中 + 结果区间 + 耗时
                        val queryStats by viewModel.searchQueryStats.collectAsState()

                        androidx.compose.animation.AnimatedVisibility(
                            visible = isHeaderVisible || showFilters || !hasSearched,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                SearchBar(
                                    query = searchQuery,
                                    tagSuggestions = tagSuggestions,
                                    onTypingWordChanged = { viewModel.fetchTagSuggestions(it) },
                                    onSearch = { query ->
                                        // 粘贴 FA 链接直达：作品/用户/搜索页，搜索链不进普通搜索
                                        when (val link = com.furflix.app.data.remote.parseFaLink(query)) {
                                            is com.furflix.app.data.remote.FaLink.Submission -> {
                                                focusManager.clearFocus()
                                                onNavigateToPost(link.id)
                                            }
                                            is com.furflix.app.data.remote.FaLink.User -> {
                                                focusManager.clearFocus()
                                                onNavigateToUser(link.username)
                                            }
                                            is com.furflix.app.data.remote.FaLink.Search -> {
                                                focusManager.clearFocus()
                                                viewModel.loadSearch(link.query, link.filters)
                                                showFilters = false
                                            }
                                            null -> {
                                                viewModel.updateSearchFilters(draftFilters)
                                                viewModel.loadSearch(query)
                                                showFilters = false
                                            }
                                        }
                                    },
                                    onFilterClick = { 
                                        if (showFilters) {
                                            viewModel.updateSearchFilters(draftFilters)
                                            showFilters = false
                                            if (searchQuery.isNotEmpty()) {
                                                viewModel.loadSearch(searchQuery)
                                            }
                                        } else {
                                            showFilters = true 
                                        }
                                    },
                                    activeFilterCount = draftFilters.advancedFilterCount,
                                    isFilterOpen = showFilters
                                )
                                
                                InlineFiltersRow(
                                    filters = draftFilters,
                                    onFilterChange = { newFilters ->
                                        draftFilters = newFilters
                                        viewModel.updateSearchFilters(newFilters)
                                    },
                                    searchView = searchView,
                                    onToggleSearchView = {
                                        val next = searchView.next()
                                        pendingSearchView = next
                                        coroutineScope.launch { repository.saveSearchViewMode(next) }
                                    }
                                )
                            }
                        }

                        // 服务端统计条常驻（不随搜索栏收起）：搜索后始终可见
                        QueryStatsBar(
                            stats = queryStats,
                            visible = hasSearched,
                            onTermClick = { viewModel.loadSearch(it) }
                        )
                        
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showFilters,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            InlineSearchFilters(
                                filters = draftFilters,
                                onFiltersChanged = { draftFilters = it },
                                onClear = { draftFilters = com.furflix.app.data.model.SearchFilters() },
                                query = currentSearchQuery,
                                onRunQuery = { q ->
                                    focusManager.clearFocus()
                                    viewModel.loadSearch(q)
                                },
                                onSavePreset = { name ->
                                    viewModel.addSearchPreset(name, currentSearchQuery, draftFilters)
                                }
                            )
                        }

                        if (!hasSearched) {
                            // Search presets — full (query + filters) snapshots, one tap replays
                            if (searchPresets.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 20.dp, end = 20.dp, top = 12.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.search_presets),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = SubtleGray
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        searchPresets.forEach { preset ->
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                onClick = { viewModel.loadSearch(preset.query, preset.filters) }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Default.Bookmark,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = preset.name,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = DarkOnSurface
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    IconButton(
                                                        onClick = { viewModel.removeSearchPreset(preset) },
                                                        modifier = Modifier.size(16.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Close,
                                                            contentDescription = stringResource(R.string.cd_remove),
                                                            modifier = Modifier.size(14.dp),
                                                            tint = SubtleGray
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (recentSearches.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f, fill = false)
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 20.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.search_recent),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = SubtleGray
                                        )
                                        TextButton(onClick = { viewModel.clearRecentSearches() }) {
                                            Text(stringResource(R.string.search_clear_recent), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    @OptIn(ExperimentalLayoutApi::class)
                                    androidx.compose.foundation.layout.FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        recentSearches.forEach { query ->
                                            val displayQuery = query.replace("@keywords ", "#")

                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = DarkSurfaceVariant,
                                                onClick = { viewModel.loadSearch(query) }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Removed Icon here
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = displayQuery,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = DarkOnSurface
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    IconButton(
                                                        onClick = { viewModel.removeRecentSearch(query) },
                                                        modifier = Modifier.size(16.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Close,
                                                            contentDescription = stringResource(R.string.cd_remove),
                                                            modifier = Modifier.size(14.dp),
                                                            tint = SubtleGray
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp),
                                            tint = DividerDark
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = stringResource(R.string.search_furaffinity),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = SubtleGray
                                        )
                                    }
                                }
                            }
                        } else if (isLoading && searchSubmissions.isEmpty()) {
                            // 首屏骨架：按搜索结果四视图同构占位
                            com.furflix.app.ui.components.SearchSkeletonPlaceholder(
                                view = searchView,
                                gridColumnsPref = gridColumnsPref,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (errorMessage != null && searchSubmissions.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = errorMessage ?: "",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            viewModel.clearError()
                                            viewModel.loadSearch(searchQuery)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text(stringResource(R.string.action_retry))
                                    }
                                }
                            }
                        } else if (searchSubmissions.isEmpty()) {
                            EmptyState(
                                message = stringResource(R.string.search_no_results),
                                icon = Icons.Default.SearchOff
                            )
                        } else {
                            if (isMasonry) {
                                SideEffect {
                                    scrollers[2] = { searchStaggeredState.animateScrollToItem(0) }
                                }
                                LiveScrollSourceEffect(register = { source -> registerScrollSource(2, source) }) {
                                    searchStaggeredState.firstVisibleItemIndex to searchStaggeredState.firstVisibleItemScrollOffset
                                }
                                DisposableEffect(Unit) { onDispose { scrollers.keys.remove(2) } }
                                LazyVerticalStaggeredGrid(
                                    columns = masonryGridCells(gridColumnsPref),
                                    state = searchStaggeredState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = gridPadding,
                                        end = gridPadding,
                                        top = gridPadding,
                                        bottom = gridPadding + 120.dp
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                                    verticalItemSpacing = itemSpacing
                                ) {
                                    staggeredItems(
                                        items = searchSubmissions,
                                        key = { it.id },
                                        contentType = { "submission" }
                                    ) { submission ->
                                        SubmissionCard(
                                            modifier = Modifier.animateItem(),
                                            submission = submission,
                                            onClick = {
                    if (pageSelectionActive) {
                        SelectionStore.toggle(pageSource, submission.id)
                    } else {
                        viewModel.prefetchSubmission(submission.id)
                        onNavigateToPost(submission.id)
                    }
                },
                selectionActive = pageSelectionActive,
                isSelected = pageSelectionActive && selection.ids.contains(submission.id),
                onLongClick = { SelectionStore.toggle(pageSource, submission.id) },
                                            showTitle = searchShowTitles,
                                            compact = true,
                                            ratio = submission.thumbnailWidthOnHeightRatio,
                                            stats = com.furflix.app.ui.components.rememberCardStats(cardStats, submission.id),
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
                                LaunchedEffect(searchStaggeredState) {
                                    snapshotFlow {
                                        val lastVisibleItem = searchStaggeredState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: 0
                                        val totalItems = searchStaggeredState.layoutInfo.totalItemsCount
                                        lastVisibleItem >= totalItems - (if (isList) 3 else 6)
                                    }.collect { shouldLoadMore ->
                                        if (shouldLoadMore && !isLoadingMore && searchSubmissions.isNotEmpty()) {
                                            viewModel.loadMore()
                                        }
                                    }
                                }
                                MasonryPrefetchEffect(
                                    submissions = searchSubmissions,
                                    active = selectedTab == 2,
                                    laneCount = estimatedMasonryLanes
                                )
                            } else {
                                SideEffect {
                                    scrollers[2] = { searchGridState.animateScrollToItem(0) }
                                }
                                LiveScrollSourceEffect(register = { source -> registerScrollSource(2, source) }) {
                                    searchGridState.firstVisibleItemIndex to searchGridState.firstVisibleItemScrollOffset
                                }
                                DisposableEffect(Unit) { onDispose { scrollers.keys.remove(2) } }
                                LazyVerticalGrid(
                                    modifier = Modifier.fillMaxSize(),
                                    columns = galleryGridCells(searchColumnsPref, isList),
                                    state = searchGridState,
                                    contentPadding = PaddingValues(
                                        start = gridPadding,
                                        end = gridPadding,
                                        top = gridPadding,
                                        bottom = gridPadding + 120.dp
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                                    verticalArrangement = Arrangement.spacedBy(itemSpacing)
                                ) {
                                    items(
                                        items = searchSubmissions,
                                        key = { it.id },
                                        contentType = { "submission" }
                                    ) { submission ->
                                        SubmissionCard(
                                            modifier = Modifier.animateItem(),
                                            submission = submission,
                                            onClick = {
                    if (pageSelectionActive) {
                        SelectionStore.toggle(pageSource, submission.id)
                    } else {
                        viewModel.prefetchSubmission(submission.id)
                        onNavigateToPost(submission.id)
                    }
                },
                selectionActive = pageSelectionActive,
                isSelected = pageSelectionActive && selection.ids.contains(submission.id),
                onLongClick = { SelectionStore.toggle(pageSource, submission.id) },
                                            showTitle = searchShowTitles,
                                            compact = !isList,
                                            stats = com.furflix.app.ui.components.rememberCardStats(cardStats, submission.id),
                                            onAuthorClick = { onNavigateToUser(it) }
                                        )
                                    }
                                    if (isLoadingMore) {
                                        item(key = "loading_more", contentType = "footer") { LoadingMoreFooter() }
                                    }
                                }
                                LaunchedEffect(searchGridState) {
                                    snapshotFlow {
                                        val lastVisibleItem = searchGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                        val totalItems = searchGridState.layoutInfo.totalItemsCount
                                        lastVisibleItem >= totalItems - (if (isList) 3 else 6)
                                    }.collect { shouldLoadMore ->
                                        if (shouldLoadMore && !isLoadingMore && searchSubmissions.isNotEmpty()) {
                                            viewModel.loadMore()
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }
                    3 -> {
                        LaunchedEffect(isLoggedIn) {
                            watchlistViewModel.refreshIfNeeded()
                        }
                        ProfileScreen(
                            viewMode = viewMode,
                            columnsPref = gridColumnsPref,
                            showTitles = showTitles,
                            cardStats = cardStats,
                            onClearHistory = { viewModel.clearCardStats() },
                            onWarmStats = { viewModel.prefetchSubmissionStats(it) },
                            onBatchUnfavorite = { ids -> viewModel.batchUnfavorite(ids) },
                            onRemoveHistory = { ids ->
                                viewModel.removeHistoryEntries(ids)
                                android.widget.Toast.makeText(
                                    context,
                                    historyRemovedTemplate.format(ids.size),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            onNavigateToUser = onNavigateToUser,
                            onNavigateToPost = onNavigateToPost,
                            onNavigateToFavoritesPost = onNavigateToFavoritesPost,
                            onOpenCollectedFavorites = onOpenCollectedFavorites,
                            scrollToTopTrigger = profileScrollToTopTrigger,
                            onRegisterScrollSource = { source -> registerScrollSource(3, source) },
                            viewModel = watchlistViewModel
                        )
                    }
                }
            }
            } // close Box(nestedScroll)

            // Pull indicator overlay — drawn on top of everything
            if (pullProgress > 0f || isRefreshing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .size(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    PawPullIndicator(
                        isRefreshing = isRefreshing,
                        pullProgress = pullProgress / pullThreshold
                    )
                }
            }

            // Floating panel sits on top of the content (true overlay, not a Scaffold slot).
            // 滚动隐藏只平移浮层，内容底部预留不变，避免重排跳动
            val bottomBarOffset by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (barsVisible && !selectionActive) 0.dp else 112.dp,
                animationSpec = tween(250),
                label = "bottomBarOffset"
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
                    onTabSelected = { index ->
                        if (selectedTab == index) {
                            if (index == 3) {
                                profileScrollToTopTrigger++
                            } else {
                                coroutineScope.launch {
                                    try { scrollers[index]?.invoke() } catch (_: Exception) {}
                                }
                            }
                        } else {
                            viewModel.setSelectedTab(index)
                            if (index !in loadedTabs) {
                                when (index) {
                                    0 -> viewModel.loadBrowse()
                                    1 -> viewModel.loadLatest()
                                    2 -> {}
                                    3 -> {}
                                }
                                loadedTabs.add(index)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun NsfwTogglePill(
    nsfwEnabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (nsfwEnabled) Color(0xFFFF007F).copy(alpha = 0.15f) else Rating_General.copy(alpha = 0.15f),
        label = "bg_color"
    )
    val contentColor by animateColorAsState(
        targetValue = if (nsfwEnabled) Color(0xFFFF007F) else Rating_General,
        label = "content_color"
    )
    val text = if (nsfwEnabled) "NSFW" else "SFW"
    val icon = if (nsfwEnabled) Icons.Default.LocalFireDepartment else Icons.Default.Shield

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .height(32.dp)
            .clickable(onClick = onToggle)
            .border(
                1.dp,
                contentColor.copy(alpha = 0.3f),
                RoundedCornerShape(16.dp)
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            AnimatedContent(
                targetState = text,
                transitionSpec = {
                    fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                },
                label = "text_anim"
            ) { targetText ->
                Text(
                    text = targetText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }
    }
}


/**
 * Warms Coil's caches for the first screenful of masonry cards before they
 * compose: cards negotiate their own CDN tier from the measured lane width,
 * this pre-negotiates from an estimate of the same lane so the warm URLs hit
 * the same cache keys. Runs only for the selected tab: the HorizontalPager
 * pre-composes the adjacent page, which would otherwise double the traffic.
 * Coil 3 dropped the request-priority API, so "first screen first" is
 * expressed by executing the first three requests before enqueueing the rest.
 * 预取范围收紧到一屏余量（12 张）：可视区按需加载为主，预取只补首屏外
 * 半屏余量；后台时段由网络层闸门统一拦停。
 */
@Composable
private fun MasonryPrefetchEffect(
    submissions: List<Submission>,
    active: Boolean,
    laneCount: Int
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val quality = LocalThumbnailQuality.current
    LaunchedEffect(submissions, active, laneCount, quality) {
        if (!active || submissions.isEmpty()) return@LaunchedEffect
        if (!com.furflix.app.data.lifecycle.AppLifecycleTracker.isForeground.value) return@LaunchedEffect
        val dm = context.resources.displayMetrics
        val lanes = laneCount.coerceAtLeast(1)
        val laneWidthPx = (
            dm.widthPixels - with(density) { 16.dp.toPx() } -
                with(density) { 4.dp.toPx() } * (lanes - 1)
            ).coerceAtLeast(400f) / lanes
        val loader = ImageLoaderFactory.getInstance(context)
        val requests = submissions.take(12).mapNotNull { sub ->
            val ratio = sub.thumbnailWidthOnHeightRatio
            if (ratio <= 0f || sub.thumbnailUrl.isEmpty()) return@mapNotNull null
            val heightPx = laneWidthPx / ratio
            val negotiated = DynamicThumbnail(sub.thumbnailUrl).bestThumbnailUrl(laneWidthPx, heightPx, quality)
            ImageRequest.Builder(context)
                .data(negotiated)
                .size(laneWidthPx.toInt(), heightPx.toInt())
                .build()
        }
        coroutineScope {
            requests.take(3).map { request -> launch { runCatching { loader.execute(request) } } }.joinAll()
            requests.drop(3).forEach { request -> launch { runCatching { loader.execute(request) } } }
        }
    }
}
