package com.furflix.app.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.furflix.app.R
import com.furflix.app.data.model.GalleryViewMode
import com.furflix.app.data.repository.FurRepository
import com.furflix.app.ui.components.LoadingMoreFooter
import com.furflix.app.ui.components.PawPullRefreshBox
import com.furflix.app.ui.screens.InlineFiltersRow
import com.furflix.app.ui.screens.InlineSearchFilters
import com.furflix.app.ui.screens.SearchBar
import com.furflix.app.ui.components.SubmissionCard
import com.furflix.app.ui.components.galleryGridCells
import com.furflix.app.ui.components.masonryGridCells
import com.furflix.app.viewmodel.MainViewModel
import com.furflix.app.viewmodel.TagSearchViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagSearchScreen(
    tag: String,
    viewModel: TagSearchViewModel,
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToPost: (String) -> Unit,
    onNavigateToUser: (String) -> Unit,
    cardStats: Map<String, com.furflix.app.data.model.CardStats> = emptyMap()
) {
    val submissions by viewModel.submissions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val context = LocalContext.current
    val repository = remember { FurRepository.getInstance(context) }
    // 画廊三视图与全局共用（网格/瀑布流/列表），列数偏好同源
    val viewMode by repository.viewModeFlow.collectAsState(initial = GalleryViewMode.GRID)
    val gridColumnsPref by repository.gridColumnsFlow.collectAsState(initial = 2)

    // 多选批量（与首页/用户页同一 SelectionStore；source 与详情页导航的 tag_ 前缀对齐）
    val selection by com.furflix.app.data.selection.SelectionStore.state.collectAsState()
    val pageSource = "tag_$tag"
    val selectionActive = selection.active && selection.source == pageSource
    androidx.activity.compose.BackHandler(enabled = selectionActive) {
        com.furflix.app.data.selection.SelectionStore.exit()
    }
    DisposableEffect(tag) {
        onDispose {
            if (com.furflix.app.data.selection.SelectionStore.state.value.source == pageSource) {
                com.furflix.app.data.selection.SelectionStore.exit()
            }
        }
    }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            var showFilters by remember { mutableStateOf(false) }
            val searchFilters by viewModel.searchFilters.collectAsState()
            var draftFilters by remember(searchFilters) { mutableStateOf(searchFilters) }
            val searchQuery by viewModel.searchQuery.collectAsState()
            val tagSuggestions by viewModel.tagSuggestions.collectAsState()

            Column(modifier = Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        SearchBar(
                            query = searchQuery,
                            tagSuggestions = tagSuggestions,
                            onTypingWordChanged = { viewModel.fetchTagSuggestions(it) },
                            onSearch = { query -> 
                                viewModel.updateSearchFilters(draftFilters)
                                viewModel.loadSearch(query)
                                showFilters = false
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
                    }
                }

                InlineFiltersRow(
                    filters = draftFilters,
                    onFilterChange = { newFilters ->
                        draftFilters = newFilters
                        viewModel.updateSearchFilters(newFilters)
                    }
                )

                // 多选动作条：与首页/用户页同一套批量操作
                if (selectionActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            com.furflix.app.data.selection.SelectionStore.selectAll(pageSource, submissions.map { it.id })
                        }) {
                            Text(stringResource(R.string.action_select_all), color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { mainViewModel.batchFavorite(selection.ids.toList()) }) {
                            Icon(Icons.Default.Favorite, contentDescription = stringResource(R.string.action_batch_favorite), tint = Color(0xFFFF007F))
                        }
                        IconButton(onClick = {
                            val batchQueuedText = stringResource(R.string.toast_batch_download_queued, selection.ids.size)
                            selection.ids.forEach { com.furflix.app.data.download.DownloadManager.enqueueDownload(context, it) }
                            android.widget.Toast.makeText(context, batchQueuedText, android.widget.Toast.LENGTH_SHORT).show()
                            com.furflix.app.data.selection.SelectionStore.exit()
                        }) {
                            Icon(Icons.Default.Download, contentDescription = stringResource(R.string.action_batch_download), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { com.furflix.app.data.selection.SelectionStore.exit() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showFilters,
                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                ) {
                    InlineSearchFilters(
                        filters = draftFilters,
                        onFiltersChanged = { draftFilters = it },
                        onClear = { draftFilters = com.furflix.app.data.model.SearchFilters() }
                    )
                }

                PawPullRefreshBox(
                    isRefreshing = isLoading,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
            if (isLoading && submissions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                            onClick = { viewModel.clearError() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(stringResource(R.string.action_ok))
                        }
                    }
                }
            } else {
                // 三视图共用同一张卡片与同一套多选回调（与首页/用户页一致）
                val submissionCard: @Composable (com.furflix.app.data.model.Submission) -> Unit = { submission ->
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
                        showTitle = viewMode == GalleryViewMode.GRID,
                        compact = viewMode != GalleryViewMode.LIST,
                        ratio = if (viewMode == GalleryViewMode.MASONRY) submission.thumbnailWidthOnHeightRatio else 0f,
                        stats = cardStats[submission.id],
                        onAuthorClick = { onNavigateToUser(it) }
                    )
                }

                Crossfade(targetState = viewMode, animationSpec = tween(300), label = "tagViewMode") { mode ->
                    when (mode) {
                        GalleryViewMode.MASONRY -> {
                            val staggeredState = rememberLazyStaggeredGridState()
                            LazyVerticalStaggeredGrid(
                                columns = masonryGridCells(gridColumnsPref),
                                state = staggeredState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 86.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalItemSpacing = 4.dp
                            ) {
                                staggeredItems(
                                    items = submissions,
                                    key = { it.id },
                                    contentType = { "submission" }
                                ) { submission -> submissionCard(submission) }
                                if (isLoadingMore) {
                                    item(
                                        span = StaggeredGridItemSpan.FullLine,
                                        key = "loading_more",
                                        contentType = "footer"
                                    ) { LoadingMoreFooter() }
                                }
                            }
                            LaunchedEffect(staggeredState) {
                                snapshotFlow {
                                    val lastVisibleItem = staggeredState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: 0
                                    val totalItems = staggeredState.layoutInfo.totalItemsCount
                                    lastVisibleItem >= totalItems - 6
                                }.collect { shouldLoadMore ->
                                    if (shouldLoadMore && !isLoadingMore && submissions.isNotEmpty()) {
                                        viewModel.loadMore()
                                    }
                                }
                            }
                        }
                        GalleryViewMode.LIST -> {
                            val listState = rememberLazyListState()
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 92.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                listItems(
                                    items = submissions,
                                    key = { it.id },
                                    contentType = { "submission" }
                                ) { submission -> submissionCard(submission) }
                                if (isLoadingMore) {
                                    item(key = "loading_more", contentType = "footer") { LoadingMoreFooter() }
                                }
                            }
                            LaunchedEffect(listState) {
                                snapshotFlow {
                                    val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                    val totalItems = listState.layoutInfo.totalItemsCount
                                    lastVisibleItem >= totalItems - 3
                                }.collect { shouldLoadMore ->
                                    if (shouldLoadMore && !isLoadingMore && submissions.isNotEmpty()) {
                                        viewModel.loadMore()
                                    }
                                }
                            }
                        }
                        else -> {
                            val gridState = rememberLazyGridState()
                            LazyVerticalGrid(
                                modifier = Modifier.fillMaxSize(),
                                columns = galleryGridCells(gridColumnsPref, isList = false),
                                state = gridState,
                                contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 86.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(
                                    items = submissions,
                                    key = { it.id },
                                    contentType = { "submission" }
                                ) { submission -> submissionCard(submission) }
                                if (isLoadingMore) {
                                    item(key = "loading_more", contentType = "footer") { LoadingMoreFooter() }
                                }
                            }
                            LaunchedEffect(gridState) {
                                snapshotFlow {
                                    val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                    val totalItems = gridState.layoutInfo.totalItemsCount
                                    lastVisibleItem >= totalItems - 6
                                }.collect { shouldLoadMore ->
                                    if (shouldLoadMore && !isLoadingMore && submissions.isNotEmpty()) {
                                        viewModel.loadMore()
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }
}
}
