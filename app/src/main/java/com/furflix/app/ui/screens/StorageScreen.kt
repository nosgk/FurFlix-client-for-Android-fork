package com.furflix.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.furflix.app.R
import com.furflix.app.data.repository.FurRepository
import com.furflix.app.translate.data.TranslationCache
import com.furflix.app.ui.components.HazeTopAppBar
import com.furflix.app.ui.theme.formatBytes
import com.furflix.app.viewmodel.MainViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 存储透明化子屏：分项列出本地占用（图片缓存 / 翻译缓存 / 搜索历史 /
 * 已看帖子统计 / 内置标签数据库）并支持分项清理。
 * 本地没有 Room/SQLite 业务库（TagsDatabase 是内置只读 FTS 词库），
 * 所以不存在"重建索引"一说，最大项就是各缓存。
 */
@Composable
fun StorageScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val repository = remember { FurRepository.getInstance(context) }
    val scope = rememberCoroutineScope()

    val cacheSize by viewModel.cacheSize.collectAsState()
    val isClearingCache by viewModel.isClearingCache.collectAsState()
    val cardStats by viewModel.cardStats.collectAsState()
    val recentSearches by repository.recentSearchesFlow.collectAsState(initial = emptyList())

    val translationCache = remember { TranslationCache.getInstance(context) }
    var translationEpoch by remember { mutableIntStateOf(0) }
    var translationStats by remember { mutableStateOf(0 to 0) }
    LaunchedEffect(translationEpoch) {
        translationStats = translationCache.stats()
    }

    var tagDbBytes by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val dbFile = context.getDatabasePath("tags_fur_v5.db")
            tagDbBytes = if (dbFile.exists()) dbFile.length() else 0L
        }
    }

    LaunchedEffect(Unit) { viewModel.refreshCacheSize() }

    // Toast 文案在组合期解析（LocalContext.getString 非配置感知，lint 会报错）
    val translationClearedText = stringResource(R.string.translation_cache_cleared)
    val historyClearedText = stringResource(R.string.storage_history_cleared)
    val browseDataClearedText = stringResource(R.string.storage_browse_data_cleared)

    val hazeState = remember { HazeState() }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            HazeTopAppBar(
                title = { Text(stringResource(R.string.settings_storage)) },
                hazeState = hazeState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
                .hazeSource(state = hazeState)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SettingsSectionHeader(stringResource(R.string.settings_storage))
            SettingsGroupCard {
                SettingsRow(
                    icon = Icons.Default.DeleteSweep,
                    iconColor = Color(0xFFFF5252),
                    title = stringResource(R.string.settings_image_cache),
                    subtitle = if (isClearingCache) stringResource(R.string.settings_clearing) else cacheSize,
                    onClick = { viewModel.clearImageCache() },
                    trailing = {
                        if (isClearingCache) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Default.Cached,
                    iconColor = Color(0xFF00BCD4),
                    title = stringResource(R.string.translation_cache),
                    subtitle = stringResource(R.string.translation_cache_stats, translationStats.first, formatBytes(translationStats.second.toLong())),
                    onClick = {
                        scope.launch {
                            translationCache.clear()
                            translationEpoch++
                            Toast.makeText(context, translationClearedText, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Default.History,
                    iconColor = Color(0xFF2196F3),
                    title = stringResource(R.string.storage_search_history),
                    subtitle = stringResource(R.string.storage_entries_count, recentSearches.size),
                    onClick = {
                        scope.launch {
                            repository.saveRecentSearches(emptyList())
                            Toast.makeText(context, historyClearedText, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Default.BarChart,
                    iconColor = Color(0xFFFF9800),
                    title = stringResource(R.string.storage_browse_data),
                    subtitle = stringResource(R.string.storage_entries_count, cardStats.size),
                    onClick = {
                        viewModel.clearCardStats()
                        Toast.makeText(context, browseDataClearedText, Toast.LENGTH_SHORT).show()
                    }
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Default.Storage,
                    iconColor = Color(0xFF9E9E9E),
                    title = stringResource(R.string.storage_tag_database),
                    subtitle = formatBytes(tagDbBytes.coerceAtLeast(0L)),
                    onClick = {}
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.storage_clear_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
                lineHeight = 18.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}
