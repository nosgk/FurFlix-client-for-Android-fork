package com.furflix.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furflix.app.R
import com.furflix.app.data.model.Submission
import com.furflix.app.ui.theme.SubtleGray

/**
 * 收藏夹 / 浏览记录共用的客户端检索筛选。
 *
 * 为什么是客户端筛选：FA 的 `getUserFavorites` 不接受任何筛选参数（站点侧没有
 * 收藏夹搜索），而浏览记录本来就是纯本地数据（CardStats 的 visited 条目）。
 * 两边都只能对**已加载**的列表做筛选，这也是这两个页面唯一的可行做法。
 *
 * 排序里的"时间"用的是列表自身的顺序：FA 收藏夹与用户画廊都是"最近在前"返回，
 * 浏览记录也按最新在前排列——所以顺序/倒序即时间序。FA 列表页 HTML 不含日期
 * （figure 里只有标题与作者），无法按真实日期排序，故不提供"按日期"选项以免误导。
 */

enum class SubmissionSortOrder { TIME_DESC, TIME_ASC, TITLE_ASC }

enum class SubmissionTypeGroup { ALL, IMAGE, TEXT, MUSIC, FLASH, DOCUMENT, OTHER }

data class SubmissionFilterState(
    val query: String = "",
    val type: SubmissionTypeGroup = SubmissionTypeGroup.ALL,
    val sort: SubmissionSortOrder = SubmissionSortOrder.TIME_DESC
)

private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "heic")
private val AUDIO_EXTS = setOf("mp3", "m4a", "ogg", "wav", "flac", "aac", "mid", "midi")
private val DOC_EXTS = setOf("pdf", "doc", "docx", "rtf", "odt")

/** 类型分组：FA 的 t-* class（fileType）与文件扩展名取并集判断，两者任一命中即可。 */
fun typeGroupOf(sub: Submission): SubmissionTypeGroup {
    val type = sub.fileType.lowercase()
    val ext = sub.fileExt.lowercase()
    return when {
        type == "music" || ext in AUDIO_EXTS -> SubmissionTypeGroup.MUSIC
        type == "flash" || ext == "swf" -> SubmissionTypeGroup.FLASH
        type == "story" || type == "poetry" || type == "text" || ext == "txt" ->
            SubmissionTypeGroup.TEXT
        ext in DOC_EXTS -> SubmissionTypeGroup.DOCUMENT
        type == "image" || ext in IMAGE_EXTS -> SubmissionTypeGroup.IMAGE
        else -> SubmissionTypeGroup.OTHER
    }
}

/** 关键词（标题/作者）+ 类型筛选 + 排序。空白关键词与 ALL 类型原样通过。 */
fun applySubmissionFilter(
    items: List<Submission>,
    state: SubmissionFilterState
): List<Submission> {
    val q = state.query.trim().lowercase()
    val byKeyword = if (q.isEmpty()) {
        items
    } else {
        items.filter {
            it.title.lowercase().contains(q) || it.author.lowercase().contains(q)
        }
    }
    val byType = if (state.type == SubmissionTypeGroup.ALL) {
        byKeyword
    } else {
        byKeyword.filter { typeGroupOf(it) == state.type }
    }
    return when (state.sort) {
        SubmissionSortOrder.TIME_DESC -> byType
        SubmissionSortOrder.TIME_ASC -> byType.asReversed()
        SubmissionSortOrder.TITLE_ASC -> byType.sortedBy { it.title.lowercase() }
    }
}

@StringRes
private fun typeLabelRes(group: SubmissionTypeGroup): Int = when (group) {
    SubmissionTypeGroup.ALL -> R.string.list_filter_type_all
    SubmissionTypeGroup.IMAGE -> R.string.list_filter_type_image
    SubmissionTypeGroup.TEXT -> R.string.list_filter_type_text
    SubmissionTypeGroup.MUSIC -> R.string.list_filter_type_music
    SubmissionTypeGroup.FLASH -> R.string.list_filter_type_flash
    SubmissionTypeGroup.DOCUMENT -> R.string.list_filter_type_document
    SubmissionTypeGroup.OTHER -> R.string.list_filter_type_other
}

@StringRes
private fun sortLabelRes(order: SubmissionSortOrder): Int = when (order) {
    SubmissionSortOrder.TIME_DESC -> R.string.list_filter_sort_time_desc
    SubmissionSortOrder.TIME_ASC -> R.string.list_filter_sort_time_asc
    SubmissionSortOrder.TITLE_ASC -> R.string.list_filter_sort_title
}

/** 关键词输入 + 类型/排序下拉 + 命中数量。调用方负责把 [SubmissionFilterState] 喂给 [applySubmissionFilter]。 */
@Composable
fun SubmissionFilterBar(
    state: SubmissionFilterState,
    onStateChange: (SubmissionFilterState) -> Unit,
    resultCount: Int,
    modifier: Modifier = Modifier
) {
    var typeMenuOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { onStateChange(state.copy(query = it)) },
            placeholder = {
                Text(stringResource(R.string.list_filter_search_hint), fontSize = 13.sp)
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onStateChange(state.copy(query = "")) }) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box {
                TextButton(
                    onClick = { typeMenuOpen = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(stringResource(typeLabelRes(state.type)), fontSize = 12.sp)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                    SubmissionTypeGroup.entries.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(stringResource(typeLabelRes(group))) },
                            onClick = {
                                onStateChange(state.copy(type = group))
                                typeMenuOpen = false
                            }
                        )
                    }
                }
            }
            Box {
                TextButton(
                    onClick = { sortMenuOpen = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(stringResource(sortLabelRes(state.sort)), fontSize = 12.sp)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    SubmissionSortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(stringResource(sortLabelRes(order))) },
                            onClick = {
                                onStateChange(state.copy(sort = order))
                                sortMenuOpen = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.list_filter_count, resultCount),
                fontSize = 11.sp,
                color = SubtleGray
            )
        }
    }
}
