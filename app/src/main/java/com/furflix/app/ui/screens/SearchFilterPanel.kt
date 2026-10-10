package com.furflix.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furflix.app.R
import com.furflix.app.data.FA_ART_TYPE_GROUPS
import com.furflix.app.data.FA_ART_TYPES
import com.furflix.app.data.FA_CATEGORIES
import com.furflix.app.data.FA_CATEGORY_GROUPS
import com.furflix.app.data.FA_GENDER_OPTIONS
import com.furflix.app.data.FA_SPECIES
import com.furflix.app.data.model.SearchFilters
import com.furflix.app.ui.theme.BorderDark
import com.furflix.app.ui.theme.CardBackground
import com.furflix.app.ui.theme.DarkSurfaceVariant
import com.furflix.app.ui.theme.MutedText
import com.furflix.app.ui.theme.Rating_Adult
import com.furflix.app.ui.theme.Rating_General
import com.furflix.app.ui.theme.Rating_Mature
import com.furflix.app.ui.theme.SubtleGray

/**
 * 搜索与筛选面板(K-006 / REFACTOR-010,2026-10-10 二次升级)。
 *
 * 信息架构对齐 furaffinity.net `/search/` 侧栏 `#search-advanced`:
 *   - **Sort Criteria**:order-by(relevancy/date/popularity)× order-direction(asc/desc)
 *   - **Upload Type Filter**:Category / Type / Species 三行(FA 的 optgroup 分组原样保留)
 * 其余 FA 放在主搜索表单里的维度(Rating / Gender / Content Type)与本站扩展
 * (Time Range / per-page / Match Mode / 语法速查)按同一分区语言组织。
 *
 * 移动端适配:
 *   - 所有可点行最小高度 44dp(触控目标下限);
 *   - 排序用芯片而非下拉(3+2 项,平铺比嵌套菜单更快);
 *   - Category/Type 用分组下拉(FA 的 optgroup 结构),Species 用可搜索下拉(398 项)。
 *
 * 极客观感:分区小标签字距拉开、计数徽标与语法操作符用等宽字体。
 *
 * 行为契约不变:SearchFilters 字段读写、FA 原生枚举取值、URL 镜像与所有回调签名。
 */
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            FilterPanelHeader(
                activeCount = filters.advancedFilterCount,
                query = query,
                filters = filters,
                onClear = onClear,
                onSaveClick = { showSaveDialog = true }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ── Sort Criteria ────────────────────────────────────────────
            FilterSection(title = stringResource(R.string.filter_sort_criteria)) {
                Text(
                    stringResource(R.string.filter_order_by),
                    style = MaterialTheme.typography.bodySmall,
                    color = SubtleGray,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "date" to R.string.filter_sort_date,
                        "relevancy" to R.string.filter_sort_relevancy,
                        "popularity" to R.string.filter_sort_popularity
                    ).forEach { (value, labelRes) ->
                        FAFilterChip(
                            label = stringResource(labelRes),
                            selected = filters.orderBy == value
                        ) { onFiltersChanged(filters.copy(orderBy = value)) }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    stringResource(R.string.filter_sort_direction),
                    style = MaterialTheme.typography.bodySmall,
                    color = SubtleGray,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FAFilterChip(
                        label = stringResource(R.string.filter_dir_desc),
                        selected = filters.orderDirection == "desc"
                    ) { onFiltersChanged(filters.copy(orderDirection = "desc")) }
                    FAFilterChip(
                        label = stringResource(R.string.filter_dir_asc),
                        selected = filters.orderDirection == "asc"
                    ) { onFiltersChanged(filters.copy(orderDirection = "asc")) }
                }
            }

            // ── Upload Type Filter(Category / Type / Species)────────────
            FilterSection(title = stringResource(R.string.filter_upload_type)) {
                GroupedFilterSelect(
                    label = stringResource(R.string.filter_category),
                    valueLabel = FA_CATEGORIES.firstOrNull { it.first == filters.category }?.second ?: "All",
                    groups = remember { groupedOptions(FA_CATEGORIES, FA_CATEGORY_GROUPS) },
                    onSelect = { onFiltersChanged(filters.copy(category = it.toIntOrNull() ?: 0)) }
                )
                GroupedFilterSelect(
                    label = stringResource(R.string.filter_arttype),
                    valueLabel = FA_ART_TYPES.firstOrNull { it.first == filters.artType }?.second ?: "All",
                    groups = remember { groupedOptions(FA_ART_TYPES, FA_ART_TYPE_GROUPS) },
                    onSelect = { onFiltersChanged(filters.copy(artType = it.toIntOrNull() ?: 0)) }
                )
                val speciesOptions = remember {
                    listOf("0" to "All") + FA_SPECIES.map { it.first.toString() to it.second }
                }
                SearchableFilterDropdownRow(
                    title = stringResource(R.string.filter_species),
                    currentValueLabel = speciesOptions.firstOrNull { it.first == filters.species.toString() }?.second ?: "All",
                    options = speciesOptions,
                    onSelect = { onFiltersChanged(filters.copy(species = it.toIntOrNull() ?: 0)) }
                )
            }

            // ── Rating ───────────────────────────────────────────────────
            FilterSection(title = stringResource(R.string.filter_rating)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RatingChip(
                        label = stringResource(R.string.filter_general),
                        accent = Rating_General,
                        selected = filters.ratingGeneral,
                        modifier = Modifier.weight(1f)
                    ) { onFiltersChanged(filters.copy(ratingGeneral = !filters.ratingGeneral)) }
                    RatingChip(
                        label = stringResource(R.string.filter_mature),
                        accent = Rating_Mature,
                        selected = filters.ratingMature,
                        modifier = Modifier.weight(1f)
                    ) { onFiltersChanged(filters.copy(ratingMature = !filters.ratingMature)) }
                    RatingChip(
                        label = stringResource(R.string.filter_adult),
                        accent = Rating_Adult,
                        selected = filters.ratingAdult,
                        modifier = Modifier.weight(1f)
                    ) { onFiltersChanged(filters.copy(ratingAdult = !filters.ratingAdult)) }
                }
            }

            // ── Gender(FA 语义:并进 @keywords 组)──────────────────────
            FilterSection(title = stringResource(R.string.filter_gender)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FA_GENDER_OPTIONS.filter { it.first.isNotEmpty() }.forEach { (value, _) ->
                        FAFilterChip(
                            label = genderLabel(value),
                            selected = value in filters.genders
                        ) {
                            onFiltersChanged(
                                filters.copy(
                                    genders = if (value in filters.genders) {
                                        filters.genders - value
                                    } else {
                                        filters.genders + value
                                    }
                                )
                            )
                        }
                    }
                }
            }

            // ── Content Type(FA 主表单的 upload type 复选框)───────────
            FilterSection(title = stringResource(R.string.filter_content_type)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FAFilterChip(label = stringResource(R.string.filter_type_art), selected = filters.typeArt) { onFiltersChanged(filters.copy(typeArt = !filters.typeArt)) }
                    FAFilterChip(label = stringResource(R.string.filter_type_flash), selected = filters.typeFlash) { onFiltersChanged(filters.copy(typeFlash = !filters.typeFlash)) }
                    FAFilterChip(label = stringResource(R.string.filter_type_photo), selected = filters.typePhoto) { onFiltersChanged(filters.copy(typePhoto = !filters.typePhoto)) }
                    FAFilterChip(label = stringResource(R.string.filter_type_music), selected = filters.typeMusic) { onFiltersChanged(filters.copy(typeMusic = !filters.typeMusic)) }
                    FAFilterChip(label = stringResource(R.string.filter_type_story), selected = filters.typeStory) { onFiltersChanged(filters.copy(typeStory = !filters.typeStory)) }
                    FAFilterChip(label = stringResource(R.string.filter_type_poetry), selected = filters.typePoetry) { onFiltersChanged(filters.copy(typePoetry = !filters.typePoetry)) }
                }
            }

            // ── Time Range ───────────────────────────────────────────────
            // 值必须是 FA 原生枚举(1week/1month 是无效值,2026-10-07 表单实测);
            // manual + range_from/range_to(yyyy-MM-dd,实测服务端回显生效)
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
            FilterSection(title = stringResource(R.string.filter_time_range)) {
                GroupedFilterSelect(
                    label = "",
                    valueLabel = timeRanges.firstOrNull { it.first == filters.normalizedRange }?.second
                        ?: timeRanges.last().second,
                    groups = listOf("" to timeRanges),
                    onSelect = { onFiltersChanged(filters.copy(range = it)) }
                )
                if (filters.range == "manual") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
            }

            // ── Results per page(FA 原生档位 24/48/72)─────────────────
            FilterSection(title = stringResource(R.string.filter_perpage)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchFilters.PERPAGE_CHOICES.forEach { choice ->
                        FAFilterChip(
                            label = choice.toString(),
                            selected = filters.perpage == choice
                        ) { onFiltersChanged(filters.copy(perpage = choice)) }
                    }
                }
            }

            // ── Tag Match Mode ──────────────────────────────────────────
            val modes = listOf(
                "all" to stringResource(R.string.filter_require_all),
                "any" to stringResource(R.string.filter_require_any),
                "extended" to stringResource(R.string.filter_advanced)
            )
            FilterSection(title = stringResource(R.string.filter_match_mode)) {
                GroupedFilterSelect(
                    label = "",
                    valueLabel = modes.firstOrNull { it.first == filters.mode }?.second
                        ?: stringResource(R.string.filter_advanced),
                    groups = listOf("" to modes),
                    onSelect = { onFiltersChanged(filters.copy(mode = it)) }
                )
            }

            // ── Search syntax 速查 ──────────────────────────────────────
            var showSyntax by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showSyntax = !showSyntax }
                    .defaultMinSize(minHeight = 44.dp)
                    .padding(vertical = 10.dp, horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.search_syntax_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (showSyntax) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MutedText,
                    modifier = Modifier.size(18.dp)
                )
            }
            HorizontalDivider(color = BorderDark, thickness = 1.dp)
            if (showSyntax) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 10.dp, bottom = 10.dp)
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("@title", "@message", "@keywords", "@lower", "@(title,keywords)").forEach { op ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                onClick = { onRunQuery("$op $query".trim()) }
                            ) {
                                Text(
                                    op,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
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

/**
 * 面板顶栏:标题 + 生效筛选数徽标 + 分享链接 / 保存预设 / 清空。
 * 徽标用等宽字体方括号呈现;图标按钮 44dp 触控目标。
 */
@Composable
private fun FilterPanelHeader(
    activeCount: Int,
    query: String,
    filters: SearchFilters,
    onClear: () -> Unit,
    onSaveClick: () -> Unit
) {
    val context = LocalContext.current
    val copiedToast = stringResource(R.string.toast_search_link_copied)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.search_filters),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            if (activeCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "[$activeCount]",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 复制当前检索为 FA 原生 URL(状态→URL 的分享方向,与深链解析互逆)
            IconButton(
                onClick = {
                    val url = filters.toFaSearchUrl(query)
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as? android.content.ClipboardManager
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("FA search", url))
                    android.widget.Toast.makeText(context, copiedToast, android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.Default.Link,
                    contentDescription = stringResource(R.string.cd_copy_search_link),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onSaveClick, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Default.BookmarkAdd,
                    contentDescription = stringResource(R.string.search_save_preset),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            TextButton(
                onClick = onClear,
                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    stringResource(R.string.search_clear_all),
                    color = SubtleGray,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/**
 * 统一分区:小号标签(字距拉开)+ 内容 + 细分隔线。
 * 标签风格对齐 FA 侧栏的 h4 分组标题,但用字距与颜色而非加粗来区分层级。
 */
@Composable
private fun FilterSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MutedText,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
        )
        content()
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = BorderDark, thickness = 1.dp)
    }
}

/**
 * 分组下拉行(FA `<select>` + `<optgroup>` 的 Compose 对应)。
 *
 * @param label 左侧行标签;空串表示纯值行(如时间范围/匹配模式)
 * @param groups 分组名 → (值, 显示名) 列表;空组名渲染为无标题的平铺项
 *
 * 整行最小高度 44dp;菜单内组名以小号灰字作为不可点标题。
 */
@Composable
private fun GroupedFilterSelect(
    label: String,
    valueLabel: String,
    groups: List<Pair<String, List<Pair<String, String>>>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { expanded = true }
                .defaultMinSize(minHeight = 44.dp)
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (label.isNotEmpty()) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Spacer(modifier = Modifier.width(0.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    valueLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 200.dp)
                )
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
            modifier = Modifier.heightIn(max = 420.dp).fillMaxWidth(0.75f)
        ) {
            groups.forEach { (groupName, options) ->
                if (groupName.isNotEmpty()) {
                    Text(
                        text = groupName,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = SubtleGray,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 2.dp)
                    )
                }
                options.forEach { (value, text) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text,
                                color = if (text == valueLabel) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            onSelect(value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/** 把 FA 常量表按 optgroup 结构展开为分组选项;"All"(id 0)单列为无标题首组。 */
private fun groupedOptions(
    table: List<Pair<Int, String>>,
    groups: List<Pair<String, List<Int>>>
): List<Pair<String, List<Pair<String, String>>>> {
    val byId = table.toMap()
    val out = mutableListOf<Pair<String, List<Pair<String, String>>>>()
    byId[0]?.let { out += "" to listOf("0" to it) }
    groups.forEach { (name, ids) ->
        val items = ids.mapNotNull { id -> byId[id]?.let { id.toString() to it } }
        if (items.isNotEmpty()) out += name to items
    }
    return out
}

/** 评级芯片:FA 三色语义,选中时以对应色填充,未选中为暗色描边。 */
@Composable
private fun RatingChip(
    label: String,
    accent: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = {
            Text(
                label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accent.copy(alpha = 0.22f),
            selectedLabelColor = accent,
            containerColor = Color.White.copy(alpha = 0.04f),
            labelColor = MutedText
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = accent.copy(alpha = if (selected) 0.85f else 0.25f),
            selectedBorderColor = accent,
            enabled = true,
            selected = selected
        )
    )
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

/** 自定义时间范围的单个日期框:只读展示 + 系统日期选择器。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
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
            DatePicker(state = pickerState)
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
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = if (value.isEmpty()) BorderDark else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                disabledLabelColor = MutedText,
                disabledTrailingIconColor = MutedText
            ),
            modifier = Modifier.fillMaxWidth()
        )
        // 覆盖点击层:readOnly/enabled 字段不响应点击,这里统一接管
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(4.dp))
                .clickable { showPicker = true }
        )
    }
}

/** 保存检索预设对话框:预设名默认取当前查询词。 */
@Composable
fun SavePresetDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
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

/** 通用筛选芯片(非评级类:性别 / 内容类型 / 排序 / 每页)。 */
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
            containerColor = Color.White.copy(alpha = 0.05f),
            labelColor = MutedText
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = Color.White.copy(alpha = 0.06f),
            selectedBorderColor = MaterialTheme.colorScheme.primary,
            enabled = true,
            selected = selected
        )
    )
}
