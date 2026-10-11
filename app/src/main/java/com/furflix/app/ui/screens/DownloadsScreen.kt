package com.furflix.app.ui.screens

import androidx.compose.ui.res.stringResource
import com.furflix.app.R
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furflix.app.data.download.DownloadManager
import com.furflix.app.data.download.DownloadStatus
import com.furflix.app.data.download.DownloadTask
import com.furflix.app.data.local.DownloadHelper
import com.furflix.app.data.local.DownloadedItem
import com.furflix.app.data.model.GalleryViewMode
import com.furflix.app.data.local.DownloadedMediaRegistry
import com.furflix.app.data.repository.FurRepository
import com.furflix.app.ui.components.HazeTopAppBar
import com.furflix.app.ui.components.RetrySubcomposeAsyncImage
import com.furflix.app.ui.components.rememberBarsVisibility
import com.furflix.app.ui.components.rememberRetryImageState
import com.furflix.app.ui.components.galleryGridCells
import com.furflix.app.ui.components.masonryGridCells
import com.furflix.app.ui.theme.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

data class DownloadMeta(val author: String, val title: String, val postId: String)

private fun parseMeta(context: Context, filename: String): DownloadMeta? {
    val idStr = filename.substringAfterLast("_", "")
    val id = idStr.takeWhile { it.isDigit() }
    if (id.isEmpty()) return null

    val prefs = context.getSharedPreferences("downloads_meta", Context.MODE_PRIVATE)
    val jsonStr = prefs.getString(id, null)

    if (jsonStr == null) {
        val parts = filename.substringBeforeLast(".").split("_")
        if (parts.size >= 4 && parts[0] == "FA") {
            return DownloadMeta(
                author = parts[1],
                title = parts.subList(2, parts.size - 1).joinToString("_"),
                postId = id
            )
        }
        return DownloadMeta(
            author = "Unknown",
            title = filename.substringBeforeLast("_", filename.substringBeforeLast(".")),
            postId = id
        )
    }

    return try {
        val json = org.json.JSONObject(jsonStr)
        DownloadMeta(
            author = json.optString("author", "Unknown"),
            title = json.optString("title", "Unknown"),
            postId = json.optString("postId", id)
        )
    } catch (_: Exception) { null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToPost: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { FurRepository.getInstance(context) }
    val hazeState = remember { HazeState() }

    var images by remember { mutableStateOf<List<DownloadedItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // 三视图（网格/瀑布流/列表）与列数偏好直接挂 DataStore，写入后自动回流
    val viewMode by repository.downloadsViewModeFlow.collectAsState(initial = GalleryViewMode.GRID)
    val gridColumnsPref by repository.gridColumnsFlow.collectAsState(initial = 2)
    // 主点击落点：系统相册（默认）或软件内详情页（设置页可切）
    val downloadOpenTarget by repository.downloadOpenTargetFlow.collectAsState(
        initial = FurRepository.DOWNLOAD_OPEN_SYSTEM
    )

    // 实时任务状态（排队/下载中/失败）+ 完成信号驱动列表重查
    val downloadTasks by DownloadManager.tasks.collectAsState()
    val activeTasks = downloadTasks.filter { it.status != DownloadStatus.COMPLETED }

    val gridState = rememberLazyGridState()
    val staggeredState = rememberLazyStaggeredGridState()

    val autoHideBars by repository.hideBarsOnScrollFlow.collectAsState(initial = true)
    val barsVisibility = rememberBarsVisibility(viewMode) {
        when (viewMode) {
            GalleryViewMode.GRID -> gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
            else -> staggeredState.firstVisibleItemIndex to staggeredState.firstVisibleItemScrollOffset
        }
    }
    val barsVisible = !autoHideBars || barsVisibility.visible

    LaunchedEffect(Unit) {
        images = DownloadHelper.getDownloadedImages(context)
        // 登记本地副本：点"软件内查看"进详情页时直接读这份文件，不再重新下载
        DownloadedMediaRegistry.registerAll(images)
        isLoading = false
    }
    LaunchedEffect(Unit) {
        DownloadManager.refreshSignal.collect {
            images = DownloadHelper.getDownloadedImages(context)
            DownloadedMediaRegistry.registerAll(images)
        }
    }

    Scaffold(
        topBar = {
            androidx.compose.animation.AnimatedVisibility(
                visible = barsVisible,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) +
                    androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(200)) { -it },
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)) +
                    androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(200)) { -it }
            ) {
            HazeTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.nav_tab_downloads),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = FA_Accent
                    )
                },
                hazeState = hazeState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val next = when (viewMode) {
                            GalleryViewMode.GRID -> GalleryViewMode.MASONRY
                            GalleryViewMode.MASONRY -> GalleryViewMode.LIST
                            GalleryViewMode.LIST -> GalleryViewMode.GRID
                        }
                        coroutineScope.launch { repository.saveDownloadsViewMode(next) }
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = when (viewMode) {
                                GalleryViewMode.GRID -> Icons.Default.GridView
                                GalleryViewMode.MASONRY -> Icons.Default.Dashboard
                                GalleryViewMode.LIST -> Icons.AutoMirrored.Filled.ViewList
                            },
                            contentDescription = stringResource(R.string.cd_toggle_layout),
                            tint = FA_Accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
            }
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .hazeSource(state = hazeState)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 实时任务面板：排队/下载中/失败的任务置顶展示
                AnimatedVisibility(visible = activeTasks.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.downloads_section_in_progress),
                            style = MaterialTheme.typography.labelLarge,
                            color = SubtleGray
                        )
                        activeTasks.forEach { task ->
                            DownloadTaskRow(
                                task = task,
                                onRetry = { DownloadManager.retry(task.id) }
                            )
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = FA_Accent)
                }
            } else if (images.isEmpty() && activeTasks.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(72.dp), tint = DividerDark)
                    Spacer(Modifier.height(20.dp))
                    Text(stringResource(R.string.downloads_empty_title), style = MaterialTheme.typography.titleMedium, color = SubtleGray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.downloads_empty_desc), style = MaterialTheme.typography.bodyMedium, color = DividerDark)
                }
            } else {
                // 卡片动作回调只定义一次，三种视图共用（回调按条目分发）
                val shareVia = stringResource(R.string.share_via)
                val viewPostAction: (DownloadedItem) -> Unit = { item ->
                    parseMeta(context, item.filename)?.let { onNavigateToPost(it.postId) }
                }
                // 主点击的落点由设置决定：
                // - 系统相册（默认，历史行为）：把本地文件交给系统看图应用；
                // - 软件内：走详情页，吃满无损图像引擎与缩放查看器（代价是要联网
                //   重新解析作品，本地这份文件本身不会被用上）。
                // 解析不出 postId 时（老命名/无元数据）退回系统相册，不让点击落空。
                val openAction: (DownloadedItem) -> Unit = { item ->
                    val postId = parseMeta(context, item.filename)?.postId
                    if (downloadOpenTarget == FurRepository.DOWNLOAD_OPEN_INAPP && postId != null) {
                        onNavigateToPost(postId)
                    } else {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(item.uri, "image/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    }
                }
                val viewAuthorAction: (DownloadedItem) -> Unit = { item ->
                    parseMeta(context, item.filename)?.let {
                        if (it.author != "Unknown") onNavigateToUser(it.author)
                    }
                }
                val shareAction: (DownloadedItem) -> Unit = { item ->
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, item.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, shareVia))
                }
                val deleteAction: (DownloadedItem) -> Unit = { item ->
                    coroutineScope.launch {
                        DownloadHelper.deleteImage(context, item.uri)
                        images = images.filter { it.uri != item.uri }
                    }
                }

                Crossfade(targetState = viewMode, animationSpec = tween(300), label = "viewMode") { mode ->
                    when (mode) {
                        GalleryViewMode.GRID -> {
                            LazyVerticalGrid(
                                columns = galleryGridCells(gridColumnsPref),
                                modifier = Modifier.fillMaxSize(),
                                state = gridState,
                                contentPadding = PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(images, key = { it.uri.toString() }) { item ->
                                    DownloadCard(
                                        modifier = Modifier.animateItem(),
                                        item = item,
                                        useOriginalRatio = false,
                                        onOpen = openAction,
                                        onViewPost = viewPostAction,
                                        onViewAuthor = viewAuthorAction,
                                        onShare = shareAction,
                                        onDelete = deleteAction
                                    )
                                }
                            }
                        }
                        GalleryViewMode.MASONRY -> {
                            LazyVerticalStaggeredGrid(
                                columns = masonryGridCells(gridColumnsPref),
                                modifier = Modifier.fillMaxSize(),
                                state = staggeredState,
                                contentPadding = PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalItemSpacing = 8.dp
                            ) {
                                items(images, key = { it.uri.toString() }) { item ->
                                    DownloadCard(
                                        modifier = Modifier.animateItem(),
                                        item = item,
                                        useOriginalRatio = true,
                                        onOpen = openAction,
                                        onViewPost = viewPostAction,
                                        onViewAuthor = viewAuthorAction,
                                        onShare = shareAction,
                                        onDelete = deleteAction
                                    )
                                }
                            }
                        }
                        GalleryViewMode.LIST -> {
                            LazyVerticalStaggeredGrid(
                                columns = StaggeredGridCells.Fixed(1),
                                modifier = Modifier.fillMaxSize(),
                                state = staggeredState,
                                contentPadding = PaddingValues(12.dp),
                                verticalItemSpacing = 16.dp
                            ) {
                                items(images, key = { it.uri.toString() }) { item ->
                                    DownloadCard(
                                        modifier = Modifier.animateItem(),
                                        item = item,
                                        useOriginalRatio = true,
                                        onOpen = openAction,
                                        onViewPost = viewPostAction,
                                        onViewAuthor = viewAuthorAction,
                                        onShare = shareAction,
                                        onDelete = deleteAction
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
    }
}

@Composable
private fun DownloadCard(
    item: DownloadedItem,
    useOriginalRatio: Boolean,
    onOpen: (DownloadedItem) -> Unit,
    onViewPost: (DownloadedItem) -> Unit,
    onViewAuthor: (DownloadedItem) -> Unit,
    onShare: (DownloadedItem) -> Unit,
    onDelete: (DownloadedItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val meta = remember(item.filename) { parseMeta(context, item.filename) }
    var deleteConfirm by remember { mutableStateOf(false) }

    val displayAuthor = if (meta != null && meta.author != "Unknown" && meta.author.isNotBlank()) "@${meta.author}" else null

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            // Image：瀑布流/列表按实测宽高比展示（不裁切）；网格保持方形紧凑格
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (useOriginalRatio) Modifier.aspectRatio(item.ratio.coerceIn(0.45f, 2.2f))
                        else Modifier.aspectRatio(1f)
                    )
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .clickable { onOpen(item) }
            ) {
                when {
                    item.isAudio -> {
                        // 音频下载无位图可解：类型图标占位
                        Box(
                            modifier = Modifier.fillMaxSize().background(DarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = SubtleGray,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }
                    item.isDoc -> {
                        // 文档类（PDF 等）：类型图标占位
                        Box(
                            modifier = Modifier.fillMaxSize().background(DarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                tint = SubtleGray,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }
                    else -> {
                        val retryState = rememberRetryImageState(item.uri.toString())
                        RetrySubcomposeAsyncImage(
                            state = retryState,
                            contentDescription = stringResource(R.string.cd_downloaded_image),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Actions row (No text labels)
            Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionPill(
                        icon = Icons.Default.Description,
                        onClick = { onViewPost(item) },
                        modifier = Modifier
                    )
                    if (displayAuthor != null) {
                        ActionPill(
                        icon = Icons.Default.Person,
                        onClick = { onViewAuthor(item) },
                        modifier = Modifier
                    )
                    }
                    ActionPill(
                        icon = Icons.Default.Share,
                        onClick = { onShare(item) },
                        modifier = Modifier
                    )
                    ActionPill(
                        icon = Icons.Default.Delete,
                        onClick = { deleteConfirm = true },
                        modifier = Modifier
                    )
                }

                // Delete confirmation inside card
                AnimatedVisibility(
                    visible = deleteConfirm,
                    enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it },
                    exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurface)
                            .padding(12.dp)
                    ) {
                        Text(stringResource(R.string.downloads_delete_confirm), style = MaterialTheme.typography.bodySmall, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionPill(
                        icon = Icons.Default.Close,
                        onClick = { deleteConfirm = false },
                        modifier = Modifier
                    )
                            ActionPill(
                        icon = Icons.Default.Delete,
                        onClick = {
                                    onDelete(item)
                                    deleteConfirm = false
                                },
                        modifier = Modifier
                    )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun DownloadTaskRow(task: DownloadTask, onRetry: () -> Unit) {
    val context = LocalContext.current
    val failed = task.status == DownloadStatus.FAILED

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (failed) Icons.Default.ErrorOutline else Icons.Default.Download,
                contentDescription = null,
                tint = if (failed) Color(0xFFFF5252) else FA_Accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                if (failed) {
                    Text(
                        stringResource(R.string.error_load_failed_with_code, task.error ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF5252)
                    )
                } else {
                    val fraction = if (task.totalBytes > 0) {
                        (task.bytes.toFloat() / task.totalBytes).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = FA_Accent,
                        trackColor = DividerDark
                    )
                    Spacer(Modifier.height(4.dp))
                    val statusText = stringResource(
                        when (task.status) {
                            DownloadStatus.QUEUED -> R.string.downloads_status_queued
                            DownloadStatus.RUNNING -> R.string.downloads_status_running
                            DownloadStatus.COMPLETED -> R.string.downloads_status_completed
                            DownloadStatus.FAILED -> R.string.downloads_status_failed
                        }
                    )
                    val percentText = if (task.status == DownloadStatus.RUNNING && task.totalBytes > 0) {
                        val pct = ((task.bytes * 100 / task.totalBytes).toInt()).coerceIn(0, 100)
                        stringResource(R.string.load_percent_format, pct)
                    } else {
                        null
                    }
                    val speedText = if (task.status == DownloadStatus.RUNNING && task.speedBps > 0) {
                        stringResource(R.string.load_speed_format, Formatter.formatFileSize(context, task.speedBps))
                    } else {
                        null
                    }
                    Text(
                        listOfNotNull(statusText, percentText, speedText).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = SubtleGray
                    )
                }
            }
            if (failed) {
                IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.downloads_retry),
                        tint = FA_Accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
