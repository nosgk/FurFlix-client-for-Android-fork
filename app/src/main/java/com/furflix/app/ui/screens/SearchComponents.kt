package com.furflix.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.furflix.app.R
import com.furflix.app.data.FA_ART_TYPES
import com.furflix.app.data.FA_CATEGORIES
import com.furflix.app.data.FA_GENDER_OPTIONS
import com.furflix.app.data.FA_SPECIES
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
    // 上一轮查询解析出的标签；新词搜索时若无本轮新增，则不再自动带入旧标签
    val inheritedTags = remember(query) {
        Regex("@keywords\\s+(\\S+)").findAll(query).map { it.groupValues[1] }.toList()
    }
    var tags by remember(query) {
        mutableStateOf(inheritedTags)
    }
    var text by remember(query) {
        mutableStateOf(
            "\u200B" + query.replace(Regex("@keywords\\s+\\S+"), "").trim().replace(Regex("\\s+"), " ")
        )
    }
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

                BasicTextField(
                    value = text,
                    onValueChange = { newText ->
                        if (!newText.startsWith("\u200B")) {
                            // Backspace was pressed on the empty field
                            if (tags.isNotEmpty()) {
                                tags = tags.dropLast(1)
                            }
                            text = "\u200B"
                            return@BasicTextField
                        }

                        if (newText.endsWith(" ")) {
                            val words = newText.removePrefix("\u200B").split(" ")
                            val lastWord = words.dropLast(1).lastOrNull()
                            if (lastWord != null && lastWord.startsWith("#") && lastWord.length > 1) {
                                tags = addTagToken(tags, lastWord.removePrefix("#"))
                                text = "\u200B" + newText.removePrefix("\u200B").substring(0, newText.removePrefix("\u200B").lastIndexOf(lastWord))
                                return@BasicTextField
                            }
                        }
                        text = newText

                        val words = newText.removePrefix("\u200B").split(" ")
                        val currentWord = words.lastOrNull() ?: ""
                        if (currentWord.startsWith("#")) {
                            onTypingWordChanged(currentWord.removePrefix("#"))
                        } else {
                            onTypingWordChanged(currentWord)
                        }
                    },
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .defaultMinSize(minWidth = 60.dp)
                        .padding(vertical = 4.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            var finalText = text.removePrefix("\u200B").trim()
                            val words = finalText.split(" ")
                            val lastWord = words.lastOrNull()
                            if (lastWord != null && lastWord.startsWith("#") && lastWord.length > 1) {
                                tags = addTagToken(tags, lastWord.removePrefix("#"))
                                finalText = finalText.substring(0, finalText.lastIndexOf(lastWord)).trim()
                                text = "\u200B" + finalText
                            }

                            // 输入新词搜索 = 全新查询：上一轮遗留的标签不再自动带入
                            //（本轮通过 #词/建议点选新加的标签除外，保留组合意图）
                            if (finalText.isNotEmpty() && tags == inheritedTags) {
                                tags = emptyList()
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
                                keyboardController?.hide()
                            }
                        }
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        if (text == "\u200B" && tags.isEmpty()) {
                            Text(stringResource(R.string.search_hint), color = MutedText, style = MaterialTheme.typography.bodyMedium)
                        }
                        innerTextField()
                    }
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
                                val words = text.removePrefix("\u200B").split(" ").toMutableList()
                                if (words.isNotEmpty()) {
                                    words.removeAt(words.lastIndex)
                                }
                                text = "\u200B" + words.joinToString(" ") + (if (words.isNotEmpty()) " " else "")
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

@Composable
fun ExpandableFilterRow(
    title: String,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MutedText,
                modifier = Modifier.size(18.dp)
            )
        }
        if (expanded) {
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp, start = 4.dp, end = 4.dp)) {
                content()
            }
        }
        androidx.compose.material3.HorizontalDivider(color = BorderDark, thickness = 1.dp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InlineSearchFilters(
    filters: SearchFilters,
    onFiltersChanged: (SearchFilters) -> Unit,
    onClear: () -> Unit,
    query: String,
    onRunQuery: (String) -> Unit,
    onSavePreset: (String) -> Unit
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    if (showSaveDialog) {
        SavePresetDialog(
            initialName = query,
            onConfirm = { name ->
                onSavePreset(name)
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false }
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 550.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.search_filters),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 复制当前检索为 FA 原生 URL（状态→URL 的分享方向，与深链解析互逆）
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val copiedToast = stringResource(R.string.toast_search_link_copied)
                    IconButton(
                        onClick = {
                            val url = filters.toFaSearchUrl(query)
                            val clipboard =
                                context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as? android.content.ClipboardManager
                            clipboard?.setPrimaryClip(
                                android.content.ClipData.newPlainText("FA search", url)
                            )
                            android.widget.Toast.makeText(context, copiedToast, android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = stringResource(R.string.cd_copy_search_link),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { showSaveDialog = true }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.BookmarkAdd,
                            contentDescription = stringResource(R.string.search_save_preset),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    TextButton(onClick = onClear, modifier = Modifier.height(24.dp), contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.search_clear_all), color = SubtleGray, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Rating
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FAFilterChip(modifier = Modifier.padding(horizontal = 4.dp), label = stringResource(R.string.filter_general), selected = filters.ratingGeneral) { onFiltersChanged(filters.copy(ratingGeneral = !filters.ratingGeneral)) }
                FAFilterChip(modifier = Modifier.padding(horizontal = 4.dp), label = stringResource(R.string.filter_mature), selected = filters.ratingMature) { onFiltersChanged(filters.copy(ratingMature = !filters.ratingMature)) }
                FAFilterChip(modifier = Modifier.padding(horizontal = 4.dp), label = stringResource(R.string.filter_adult), selected = filters.ratingAdult) { onFiltersChanged(filters.copy(ratingAdult = !filters.ratingAdult)) }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // 2. Gender — FA 语义：所选性别并进 @keywords 组（gender_checkboxes_search.js）
            Text(stringResource(R.string.filter_gender), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                FA_GENDER_OPTIONS.filter { it.first.isNotEmpty() }.forEach { (value, _) ->
                    FAFilterChip(
                        label = genderLabel(value),
                        selected = value in filters.genders
                    ) {
                        onFiltersChanged(
                            filters.copy(genders = if (value in filters.genders) filters.genders - value else filters.genders + value)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // 3. Content Type
            ExpandableFilterRow(title = stringResource(R.string.filter_content_type)) {
                FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    FAFilterChip(label = stringResource(R.string.filter_type_art), selected = filters.typeArt) { onFiltersChanged(filters.copy(typeArt = !filters.typeArt)) }
                    FAFilterChip(label = stringResource(R.string.filter_type_flash), selected = filters.typeFlash) { onFiltersChanged(filters.copy(typeFlash = !filters.typeFlash)) }
                    FAFilterChip(label = stringResource(R.string.filter_type_photo), selected = filters.typePhoto) { onFiltersChanged(filters.copy(typePhoto = !filters.typePhoto)) }
                    FAFilterChip(label = stringResource(R.string.filter_type_music), selected = filters.typeMusic) { onFiltersChanged(filters.copy(typeMusic = !filters.typeMusic)) }
                    FAFilterChip(label = stringResource(R.string.filter_type_story), selected = filters.typeStory) { onFiltersChanged(filters.copy(typeStory = !filters.typeStory)) }
                    FAFilterChip(label = stringResource(R.string.filter_type_poetry), selected = filters.typePoetry) { onFiltersChanged(filters.copy(typePoetry = !filters.typePoetry)) }
                }
            }

            // 4. Species（398 项：先键入过滤再点选）
            val speciesOptions = remember {
                listOf("0" to "All") + FA_SPECIES.map { it.first.toString() to it.second }
            }
            SearchableFilterDropdownRow(
                title = stringResource(R.string.filter_species),
                currentValueLabel = speciesOptions.firstOrNull { it.first == filters.species.toString() }?.second ?: "All",
                options = speciesOptions,
                onSelect = { onFiltersChanged(filters.copy(species = it.toIntOrNull() ?: 0)) }
            )

            // 5. Time Range — 值必须是 FA 原生枚举（1week/1month 是无效值，2026-10-07 表单实测）；
            //    manual + range_from/range_to（yyyy-MM-dd，实测服务端回显生效）
            val timeRanges = listOf(
                "1day" to stringResource(R.string.filter_time_1d),
                "3days" to stringResource(R.string.filter_time_3d),
                "7days" to stringResource(R.string.filter_time_1w),
                "30days" to stringResource(R.string.filter_time_1m),
                "90days" to stringResource(R.string.filter_time_90d),
                "1year" to stringResource(R.string.filter_time_1y),
                "3years" to stringResource(R.string.filter_time_3y),
                "5years" to stringResource(R.string.filter_time_5y),
                "all" to stringResource(R.string.filter_time_all),
                "manual" to stringResource(R.string.filter_time_custom)
            )
            FilterDropdownRow(
                title = stringResource(R.string.filter_time_range),
                currentValueLabel = timeRanges.firstOrNull { it.first == filters.normalizedRange }?.second
                    ?: timeRanges.last().second,
                options = timeRanges,
                onSelect = { onFiltersChanged(filters.copy(range = it)) }
            )
            if (filters.range == "manual") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    ManualDateField(
                        label = stringResource(R.string.filter_time_from),
                        value = filters.rangeFrom,
                        onPick = { onFiltersChanged(filters.copy(rangeFrom = it)) },
                        modifier = Modifier.weight(1f)
                    )
                    ManualDateField(
                        label = stringResource(R.string.filter_time_to),
                        value = filters.rangeTo,
                        onPick = { onFiltersChanged(filters.copy(rangeTo = it)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 6. Results per page（FA 原生档位 24/48/72）
            val perpageOptions = SearchFilters.PERPAGE_CHOICES.map { it.toString() to it.toString() }
            FilterDropdownRow(
                title = stringResource(R.string.filter_perpage),
                currentValueLabel = filters.perpage.toString(),
                options = perpageOptions,
                onSelect = { onFiltersChanged(filters.copy(perpage = it.toIntOrNull() ?: SearchFilters.PERPAGE_MAX)) }
            )

            // 7. Match Mode
            val modes = listOf("all" to stringResource(R.string.filter_require_all), "any" to stringResource(R.string.filter_require_any), "extended" to stringResource(R.string.filter_advanced))
            FilterDropdownRow(
                title = stringResource(R.string.filter_match_mode),
                currentValueLabel = modes.firstOrNull { it.first == filters.mode }?.second ?: stringResource(R.string.filter_advanced),
                options = modes,
                onSelect = { onFiltersChanged(filters.copy(mode = it)) }
            )

            // 8. 搜索语法速查：FA 原生 Lucene 系操作符（/help/#search），
            //    字段操作符 chip 点击即以"操作符 + 当前查询"立即执行
            var showSyntax by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSyntax = !showSyntax }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.search_syntax_title), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Icon(
                    imageVector = if (showSyntax) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MutedText,
                    modifier = Modifier.size(18.dp)
                )
            }
            androidx.compose.material3.HorizontalDivider(color = BorderDark, thickness = 1.dp)
            if (showSyntax) {
                Column(
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp, bottom = 10.dp)
                ) {
                    FlowRow(
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("@title", "@message", "@keywords", "@lower", "@(title,keywords)").forEach { op ->
                            Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                onClick = { onRunQuery("$op $query".trim()) }
                            ) {
                                Text(
                                    op,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.search_syntax_intro), style = MaterialTheme.typography.bodySmall, color = MutedText)
                    Text(stringResource(R.string.search_syntax_and), style = MaterialTheme.typography.bodySmall, color = MutedText)
                    Text(stringResource(R.string.search_syntax_or), style = MaterialTheme.typography.bodySmall, color = MutedText)
                    Text(stringResource(R.string.search_syntax_not), style = MaterialTheme.typography.bodySmall, color = MutedText)
                    Text(stringResource(R.string.search_syntax_group), style = MaterialTheme.typography.bodySmall, color = MutedText)
                    Text(stringResource(R.string.search_syntax_phrase), style = MaterialTheme.typography.bodySmall, color = MutedText)
                    Text(stringResource(R.string.search_syntax_order), style = MaterialTheme.typography.bodySmall, color = MutedText)
                    Text(stringResource(R.string.search_syntax_proximity), style = MaterialTheme.typography.bodySmall, color = MutedText)
                    Text(stringResource(R.string.search_syntax_quorum), style = MaterialTheme.typography.bodySmall, color = MutedText)
                    Text(stringResource(R.string.search_syntax_fields), style = MaterialTheme.typography.bodySmall, color = MutedText)
                    Text(stringResource(R.string.search_syntax_multi), style = MaterialTheme.typography.bodySmall, color = MutedText)
                }
            }
        }
    }
}

@Composable
private fun genderLabel(value: String): String = when (value) {
    "male" -> stringResource(R.string.filter_gender_male)
    "female" -> stringResource(R.string.filter_gender_female)
    "trans_male" -> stringResource(R.string.filter_gender_trans_male)
    "trans_female" -> stringResource(R.string.filter_gender_trans_female)
    "intersex" -> stringResource(R.string.filter_gender_intersex)
    else -> stringResource(R.string.filter_gender_non_binary)
}

/** 自定义时间范围的单个日期框：只读展示 + 系统日期选择器。 */
@Composable
private fun ManualDateField(
    label: String,
    value: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    if (showPicker) {
        val initialMillis = remember(value) {
            runCatching {
                java.time.LocalDate.parse(value)
                    .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrNull()
        }
        val pickerState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onPick(
                            java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
                        )
                    }
                    showPicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) {
            androidx.compose.material3.DatePicker(state = pickerState)
        }
    }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(label) },
            textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
            trailingIcon = {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = if (value.isEmpty()) BorderDark else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                disabledLabelColor = MutedText,
                disabledTrailingIconColor = MutedText
            ),
            modifier = Modifier.fillMaxWidth()
        )
        // 覆盖点击层：readOnly/enabled 字段不响应点击，这里统一接管
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .clickable { showPicker = true }
        )
    }
}

/** 保存检索预设对话框：预设名默认取当前查询词。 */
@Composable
fun SavePresetDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_save_preset)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.search_preset_name)) }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun FAFilterChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
            labelColor = MutedText
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f),
            selectedBorderColor = MaterialTheme.colorScheme.primary,
            enabled = true,
            selected = selected
        )
    )
}
