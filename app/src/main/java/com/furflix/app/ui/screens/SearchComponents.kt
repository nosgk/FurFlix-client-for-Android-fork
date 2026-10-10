package com.furflix.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.furflix.app.R
import com.furflix.app.data.FA_ART_TYPES
import com.furflix.app.data.FA_CATEGORIES
import com.furflix.app.data.model.SearchFilters
import com.furflix.app.data.model.SearchResultView
import com.furflix.app.data.model.addTagToken
import com.furflix.app.data.model.toggleTagExclusion
import com.furflix.app.ui.theme.BorderDark
import com.furflix.app.ui.theme.CardBackground
import com.furflix.app.ui.theme.DarkSurface
import com.furflix.app.ui.theme.DarkSurfaceVariant
import com.furflix.app.ui.theme.MutedText
import com.furflix.app.ui.theme.SubtleGray

/**
 * 搜索模块共享组件：SearchBar / 快捷筛选行 / 多维复合筛选面板。
 * 首页搜索 tab 与标签搜索页（TagSearchScreen）两处挂载同一套，
 * 改动时务必两侧联检（下拉刷新、多选、滚动源各自独立）。
 */

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SearchBar(
    query: String = "",
    onSearch: (String) -> Unit,
    onFilterClick: () -> Unit = {},
    activeFilterCount: Int = 0,
    isFilterOpen: Boolean = false,
    tagSuggestions: List<String> = emptyList(),
    onTypingWordChanged: (String) -> Unit = {}
) {
    // 输入框是本地草稿：query 只在“提交搜索”时向外回写，绝不反向灌回输入框
    // （此前 remember(query) 会在搜索触发时把框内容重置成上次查询词——“带入之前
    // 的搜索词”的根因）。标签 chips 同样只由本轮输入产生，不再从旧查询解析继承。
    var tags by remember { mutableStateOf(emptyList<String>()) }
    var text by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    androidx.compose.runtime.LaunchedEffect(text, tags.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = CardBackground,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            shadowElevation = 8.dp,
            tonalElevation = 4.dp
        ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
                    .horizontalScroll(scrollState)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    },
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tags.forEach { tag ->
                    val excluded = tag.startsWith("!")
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        color = if (excluded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    ) {
                        Row(
                            // Tap flips the polarity in place (chip stays put);
                            // the trailing × removes it entirely.
                            modifier = Modifier
                                .clickable { tags = toggleTagExclusion(tags, tag) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tag.removePrefix("!"),
                                color = DarkSurface,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                textDecoration = if (excluded) TextDecoration.LineThrough else null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_remove),
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { tags = tags - tag },
                                tint = DarkSurface
                            )
                        }
                    }
                }

                // 原生 TextField：此前用 BasicTextField + 零宽字符(\u200B)哨兵判断退格，
                // 中文/日文等组词输入法在组词过程中会吞掉该哨兵，导致输入被整体清空并
                // 误删一个标签 chip（“必须切英文输入法才能输”的根因）。原生字段不需要
                // 哨兵：空框退格删最后一个标签由 onPreviewKeyEvent 处理。
                TextField(
                    value = text,
                    onValueChange = { newText ->
                        val commitWord = if (newText.endsWith(" ")) {
                            val words = newText.trimEnd().split(" ")
                            val lastWord = words.lastOrNull()
                            lastWord != null && lastWord.startsWith("#") && lastWord.length > 1
                        } else false
                        if (commitWord) {
                            val words = newText.trimEnd().split(" ")
                            val lastWord = words.last()
                            tags = addTagToken(tags, lastWord.removePrefix("#"))
                            text = words.dropLast(1).joinToString(" ").let { if (it.isNotEmpty()) "$it " else "" }
                            onTypingWordChanged("")
                            return@TextField
                        }
                        text = newText

                        val currentWord = newText.split(" ").lastOrNull() ?: ""
                        if (currentWord.startsWith("#")) {
                            onTypingWordChanged(currentWord.removePrefix("#"))
                        } else {
                            onTypingWordChanged(currentWord)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                            // 输入框为空时按退格：删除最后一个标签 chip
                            if (event.type == KeyEventType.KeyDown &&
                                event.key == Key.Backspace &&
                                text.isEmpty() && tags.isNotEmpty()
                            ) {
                                tags = tags.dropLast(1)
                                true
                            } else {
                                false
                            }
                        },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            var finalText = text.trim()
                            val lastWord = finalText.split(" ").lastOrNull()
                            if (lastWord != null && lastWord.startsWith("#") && lastWord.length > 1) {
                                tags = addTagToken(tags, lastWord.removePrefix("#"))
                                finalText = finalText.substringBeforeLast(lastWord).trim()
                            }

                            val finalQuery = buildString {
                                append(finalText)
                                if (tags.isNotEmpty()) {
                                    if (isNotEmpty()) append(" ")
                                    // Single @keywords group so "!tag" exclusions
                                    // apply within the same set as the includes.
                                    append("@keywords ")
                                    append(tags.joinToString(" "))
                                }
                            }
                            if (finalQuery.isNotBlank()) {
                                onSearch(finalQuery.trim())
                                // 提交即清空：框内不残留上一轮查询词
                                text = ""
                                tags = emptyList()
                                onTypingWordChanged("")
                                keyboardController?.hide()
                            }
                        }
                    ),
                    placeholder = {
                        Text(stringResource(R.string.search_hint), color = MutedText, style = MaterialTheme.typography.bodyMedium)
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )

                DropdownMenu(
                    expanded = tagSuggestions.isNotEmpty(),
                    onDismissRequest = { onTypingWordChanged("") },
                    properties = androidx.compose.ui.window.PopupProperties(focusable = false),
                    modifier = Modifier.background(DarkSurfaceVariant).heightIn(max = 250.dp)
                ) {
                    tagSuggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            text = {
                                Surface(
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                ) {
                                    Text(
                                        "#$suggestion",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        color = DarkSurface,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            onClick = {
                                tags = addTagToken(tags, suggestion)
                                val words = text.split(" ").toMutableList()
                                if (words.isNotEmpty()) {
                                    words.removeAt(words.lastIndex)
                                }
                                text = words.joinToString(" ") + if (words.isNotEmpty()) " " else ""
                                onTypingWordChanged("")
                            }
                        )
                    }
                }
            }


            Box {
                IconButton(onClick = onFilterClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (isFilterOpen) Icons.Default.KeyboardArrowUp else Icons.Default.FilterList,
                        contentDescription = stringResource(R.string.cd_filters),
                        tint = if (activeFilterCount > 0 || isFilterOpen) MaterialTheme.colorScheme.primary else MutedText
                    )
                }
                if (activeFilterCount > 0) {
                    Surface(
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(9.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = activeFilterCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = DarkSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }


}
}

/** 搜索结果视图切换的图标映射（紧凑→瀑布流→大图→列表循环）。 */
fun searchViewIcon(view: SearchResultView) = when (view) {
    SearchResultView.COMPACT -> Icons.Default.Apps
    SearchResultView.MASONRY -> Icons.Default.Dashboard
    SearchResultView.LARGE -> Icons.Default.Image
    SearchResultView.LIST -> Icons.AutoMirrored.Filled.List
}

fun searchViewLabel(view: SearchResultView): Int = when (view) {
    SearchResultView.COMPACT -> R.string.cd_search_view_compact
    SearchResultView.MASONRY -> R.string.cd_search_view_masonry
    SearchResultView.LARGE -> R.string.cd_search_view_large
    SearchResultView.LIST -> R.string.cd_search_view_list
}

/**
 * 常驻快捷筛选行：分类 / 类型 / 排序（三高下拉）+ 结果视图切换按钮。
 * 排序合并 FA 的 order-by × order-direction 为四个人性化选项。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun InlineFiltersRow(
    filters: SearchFilters,
    onFilterChange: (SearchFilters) -> Unit,
    searchView: SearchResultView,
    onToggleSearchView: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InlineDropdown(
            label = FA_CATEGORIES.firstOrNull { it.first == filters.category }?.second ?: "All",
            isDefault = filters.category == 0,
            options = FA_CATEGORIES,
            onSelect = { onFilterChange(filters.copy(category = it)) },
            modifier = Modifier.weight(1f)
        )
        InlineDropdown(
            label = FA_ART_TYPES.firstOrNull { it.first == filters.artType }?.second ?: "All",
            isDefault = filters.artType == 0,
            options = FA_ART_TYPES,
            onSelect = { onFilterChange(filters.copy(artType = it)) },
            modifier = Modifier.weight(1f)
        )
        InlineTextDropdown(
            label = stringResource(filters.sortPresetLabel()),
            isDefault = filters.orderBy == "date" && filters.orderDirection == "desc",
            options = listOf(
                "date_desc" to stringResource(R.string.filter_sort_recent),
                "date_asc" to stringResource(R.string.filter_sort_oldest),
                "relevancy" to stringResource(R.string.filter_sort_relevancy),
                "popularity" to stringResource(R.string.filter_sort_popular)
            ),
            onSelect = { preset ->
                when (preset) {
                    "date_asc" -> onFilterChange(filters.copy(orderBy = "date", orderDirection = "asc"))
                    "relevancy" -> onFilterChange(filters.copy(orderBy = "relevancy", orderDirection = "desc"))
                    "popularity" -> onFilterChange(filters.copy(orderBy = "popularity", orderDirection = "desc"))
                    else -> onFilterChange(filters.copy(orderBy = "date", orderDirection = "desc"))
                }
            },
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onToggleSearchView,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                searchViewIcon(searchView),
                contentDescription = stringResource(searchViewLabel(searchView)),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun SearchFilters.sortPresetLabel(): Int = when {
    orderBy == "relevancy" -> R.string.filter_sort_relevancy
    orderBy == "popularity" -> R.string.filter_sort_popular
    orderDirection == "asc" -> R.string.filter_sort_oldest
    else -> R.string.filter_sort_recent
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun InlineDropdown(
    label: String,
    isDefault: Boolean,
    options: List<Pair<Int, String>>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            color = DarkSurfaceVariant,
            border = BorderStroke(
                1.dp,
                if (isDefault) BorderDark else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDefault) MutedText else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = if (isDefault) MutedText else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = DarkSurfaceVariant,
            border = BorderStroke(1.dp, BorderDark)
        ) {
            options.forEach { (value, name) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            name,
                            color = if (value == 0 && options[0].first == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = { onSelect(value); expanded = false }
                )
            }
        }
    }
}

/** 字符串值版下拉（排序合并选项用）。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun InlineTextDropdown(
    label: String,
    isDefault: Boolean,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            color = DarkSurfaceVariant,
            border = BorderStroke(
                1.dp,
                if (isDefault) BorderDark else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDefault) MutedText else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = if (isDefault) MutedText else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = DarkSurfaceVariant,
            border = BorderStroke(1.dp, BorderDark)
        ) {
            options.forEach { (value, name) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            name,
                            color = if (name == label) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = { onSelect(value); expanded = false }
                )
            }
        }
    }
}

@Composable
fun FilterDropdownRow(
    title: String,
    currentValueLabel: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(currentValueLabel, style = MaterialTheme.typography.bodyMedium, color = MutedText)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MutedText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = DarkSurfaceVariant,
            border = BorderStroke(1.dp, BorderDark),
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label,
                            color = if (label == currentValueLabel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
        androidx.compose.material3.HorizontalDivider(color = BorderDark, thickness = 1.dp)
    }
}

/**
 * 带过滤输入的行内下拉：长列表（物种 398 项）先键入缩小范围再点选，
 * 空查询时截断展示前 80 项避免一次性组合。
 */
@Composable
fun SearchableFilterDropdownRow(
    title: String,
    currentValueLabel: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true; query = "" }
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(currentValueLabel, style = MaterialTheme.typography.bodyMedium, color = MutedText, maxLines = 1)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MutedText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false; query = "" },
            containerColor = DarkSurfaceVariant,
            border = BorderStroke(1.dp, BorderDark),
            modifier = Modifier.fillMaxWidth(0.75f)
        ) {
            val filtered = remember(query, options) {
                val trimmed = query.trim()
                if (trimmed.isEmpty()) {
                    options.take(80)
                } else {
                    options.filter { it.second.contains(trimmed, ignoreCase = true) }.take(100)
                }
            }
            DropdownMenuItem(
                text = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        placeholder = { Text(stringResource(R.string.filter_species_search), style = MaterialTheme.typography.bodySmall, color = MutedText) },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                onClick = {}
            )
            filtered.forEach { (value, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label,
                            color = if (label == currentValueLabel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = { onSelect(value); expanded = false; query = "" }
                )
            }
            if (filtered.size < options.size) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.filter_species_narrow),
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedText
                        )
                    },
                    onClick = {}
                )
            }
        }
        androidx.compose.material3.HorizontalDivider(color = BorderDark, thickness = 1.dp)
    }
}

/** 千分位计数展示（与 FA 站点数字口径一致）。 */
private fun formatSearchCount(count: Long): String =
    java.text.DecimalFormat("#,###").format(count)

/**
 * 服务端检索统计条（FA /search/ #query-stats 的镜像）：
 * 逐词命中（点击重搜该词）+ 结果区间 + 耗时。[visible] 为 true 且有数据才渲染，
 * 首页搜索 tab 传 hasSearched（搜索后常驻，不随搜索栏收起），标签搜索页恒传 true。
 */
@Composable
fun QueryStatsBar(
    stats: com.furflix.app.data.model.SearchQueryStats?,
    visible: Boolean,
    onTermClick: (String) -> Unit = {}
) {
    if (!visible || stats == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        stats.perTerm.forEach { (term, count) ->
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = DarkSurfaceVariant,
                onClick = { onTermClick(term) }
            ) {
                Text(
                    text = stringResource(R.string.search_stats_found_in, term, formatSearchCount(count)),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (stats.hasTotal) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            ) {
                Text(
                    text = buildString {
                        append(
                            stringResource(
                                R.string.search_stats_range,
                                stats.shownFrom ?: 0,
                                stats.shownTo ?: 0,
                                formatSearchCount(stats.totalResults ?: 0)
                            )
                        )
                        stats.elapsedSeconds?.let {
                            append(" · ")
                            append(stringResource(R.string.search_stats_time, it))
                        }
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
