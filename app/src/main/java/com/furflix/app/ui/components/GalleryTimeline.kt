package com.furflix.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furflix.app.data.model.Submission
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 画廊快速拖动滚动条（时间线）+ 最后浏览位置记忆。
 *
 * ## 关于"时间节点"
 * FA 的列表页 HTML（browse/latest/search/用户画廊）里 **figure 不含日期**——2026-10-15
 * 用真实页面复核过：figure 里只有标题与作者，`span.popup_date` 只出现在详情页。
 * 因此列表条目普遍没有日期可用，这里做两级降级：
 *  - 条目带可解析日期时（详情页解析过的 date 字段）→ 按月打点，标签为 "Oct 2026"；
 *  - 否则 → 等距位置刻度（无标签），保证每个画廊都有一条可快速拖动的滚动条。
 * 这样"时间线"在有数据时是真的时间线，没数据时也不会退化成不可用。
 */

/** 懒列表滚动句柄：三种 Lazy 状态没有公共父类型，用适配器统一。 */
interface GalleryScrollHandle {
    val firstVisibleIndex: Int
    val totalCount: Int
    suspend fun scrollTo(index: Int)
}

fun lazyListHandle(state: LazyListState): GalleryScrollHandle = object : GalleryScrollHandle {
    override val firstVisibleIndex: Int get() = state.firstVisibleItemIndex
    override val totalCount: Int get() = state.layoutInfo.totalItemsCount
    override suspend fun scrollTo(index: Int) {
        state.scrollToItem(index.coerceIn(0, (totalCount - 1).coerceAtLeast(0)))
    }
}

fun lazyGridHandle(state: LazyGridState): GalleryScrollHandle = object : GalleryScrollHandle {
    override val firstVisibleIndex: Int get() = state.firstVisibleItemIndex
    override val totalCount: Int get() = state.layoutInfo.totalItemsCount
    override suspend fun scrollTo(index: Int) {
        state.scrollToItem(index.coerceIn(0, (totalCount - 1).coerceAtLeast(0)))
    }
}

fun lazyStaggeredHandle(state: LazyStaggeredGridState): GalleryScrollHandle =
    object : GalleryScrollHandle {
        override val firstVisibleIndex: Int get() = state.firstVisibleItemIndex
        override val totalCount: Int get() = state.layoutInfo.totalItemsCount
        override suspend fun scrollTo(index: Int) {
            state.scrollToItem(index.coerceIn(0, (totalCount - 1).coerceAtLeast(0)))
        }
    }

/** 滚动条上的一个节点：[index] 为条目下标，[label] 为空表示只画刻度不标注。 */
data class TimelineNode(val index: Int, val label: String)

private val FA_DATE_PATTERNS = listOf(
    "MMM d, yyyy h:mm a",
    "MMM d, yyyy",
    "MMM dd, yyyy h:mm a"
)

/** FA 日期串（如 "Oct 15, 2026 10:23 AM" / "Oct 14, 2026"）→ epoch ms；解析不出返回 null。 */
fun parseFaDate(raw: String): Long? {
    val s = raw.trim()
    if (s.isEmpty()) return null
    for (pattern in FA_DATE_PATTERNS) {
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.US).apply { isLenient = true }.parse(s)
        }.getOrNull()
        if (parsed != null) return parsed.time
    }
    return null
}

/**
 * 构造时间线节点。过半条目有日期 → 按月分桶（取每月首条）；否则等距位置刻度。
 * 节点数上限 [maxNodes]，避免滚动条被刻度塞满。
 *
 * [indexOffset]：懒容器里作品条目之前的固定头部条目数（用户页的横幅/资料卡、
 * 浏览记录的标题行等）。节点下标必须加上它才能与真实懒下标对齐。
 */
fun buildTimelineNodes(
    items: List<Submission>,
    maxNodes: Int = 12,
    indexOffset: Int = 0
): List<TimelineNode> {
    if (items.size < 2) return emptyList()
    val dates = items.map { parseFaDate(it.date) }
    val datedCount = dates.count { it != null }
    return if (datedCount * 2 >= items.size) {
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.US)
        val monthLabel = SimpleDateFormat("MMM yyyy", Locale.US)
        val seen = LinkedHashMap<String, TimelineNode>()
        items.forEachIndexed { index, _ ->
            val time = dates[index] ?: return@forEachIndexed
            val key = monthKey.format(Date(time))
            if (!seen.containsKey(key)) {
                seen[key] = TimelineNode(indexOffset + index, monthLabel.format(Date(time)))
            }
        }
        seen.values.take(maxNodes).toList()
    } else {
        val step = (items.size / maxNodes).coerceAtLeast(1)
        (0 until items.size step step).take(maxNodes).map { TimelineNode(indexOffset + it, "") }
    }
}

/**
 * 右侧快速拖动滚动条。拖动/点按按比例映射到条目下标并直接跳转；拖动时在右上角
 * 显示最近的节点标签（有日期时即月份）。
 */
@Composable
fun GalleryTimelineScrubber(
    handle: GalleryScrollHandle,
    nodes: List<TimelineNode>,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val total = handle.totalCount
    if (total <= 1) return

    val thumbColor = MaterialTheme.colorScheme.primary
    var dragging by remember { mutableStateOf(false) }
    var dragIndex by remember { mutableIntStateOf(-1) }
    val currentIndex = if (dragIndex >= 0) dragIndex else handle.firstVisibleIndex
    val progress = (currentIndex.toFloat() / (total - 1).coerceAtLeast(1)).coerceIn(0f, 1f)

    Box(modifier = modifier.fillMaxHeight().width(20.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .width(20.dp)
                .align(Alignment.CenterEnd)
                .pointerInput(total) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            val f = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                            dragIndex = (f * (total - 1)).toInt()
                            scope.launch { handle.scrollTo(dragIndex) }
                        },
                        onDragEnd = {
                            dragging = false
                            dragIndex = -1
                        },
                        onDragCancel = {
                            dragging = false
                            dragIndex = -1
                        }
                    ) { change, _ ->
                        change.consume()
                        val f = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                        dragIndex = (f * (total - 1)).toInt()
                        scope.launch { handle.scrollTo(dragIndex) }
                    }
                }
                .pointerInput(total) {
                    detectTapGestures { offset ->
                        val f = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                        scope.launch { handle.scrollTo((f * (total - 1)).toInt()) }
                    }
                }
        ) {
            val h = size.height
            val w = size.width
            val trackX = w - 3.dp.toPx()
            drawRoundRect(
                color = Color.White.copy(alpha = 0.12f),
                topLeft = Offset(trackX - 2.dp.toPx(), 0f),
                size = Size(4.dp.toPx(), h),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
            nodes.forEach { node ->
                val y = (node.index.toFloat() / (total - 1).coerceAtLeast(1)) * h
                drawLine(
                    color = Color.White.copy(alpha = 0.38f),
                    start = Offset(trackX - 9.dp.toPx(), y),
                    end = Offset(trackX + 2.dp.toPx(), y),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
            val thumbH = 28.dp.toPx()
            val thumbY = (progress * (h - thumbH)).coerceIn(0f, (h - thumbH).coerceAtLeast(0f))
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(trackX - 6.dp.toPx(), thumbY),
                size = Size(12.dp.toPx(), thumbH),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
        }

        if (dragging) {
            val label = nodes
                .filter { it.label.isNotEmpty() }
                .minByOrNull { kotlin.math.abs(it.index - currentIndex) }
                ?.label
            if (label != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 22.dp, top = 8.dp)
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

/**
 * 画廊滚动条 + 上次浏览位置记忆的一体化接线。
 *
 * 把列表包进 `Box` 并在右侧叠加滚动条，同时接上"数据到位恢复一次 / 停滚防抖写回"
 * 的位置记忆。调用方只需给出 [galleryKey]（每个画廊或子栏目唯一）、列表句柄
 * [handle] 与时间轴节点 [nodes]，不必各画廊各写一遍 Box + 两个 effect 的样板。
 *
 * [handle] 必须是 `remember(懒状态) { ...Handle(懒状态) }` 得到的**稳定对象**
 * （理由见 [RememberGalleryScrollPosition]）。
 */
@Composable
fun GalleryScrollMemory(
    galleryKey: String,
    handle: GalleryScrollHandle,
    nodes: List<TimelineNode>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { com.furflix.app.data.repository.FurRepository.getInstance(context) }
    val rememberPosition by repository.rememberTimelinePositionFlow.collectAsState(initial = true)
    val savedPositions by repository.scrollPositionsFlow.collectAsState(initial = emptyMap())
    val scope = rememberCoroutineScope()
    val active = enabled && rememberPosition

    Box(modifier = modifier) {
        content()
        if (active && nodes.isNotEmpty()) {
            GalleryTimelineScrubber(
                handle = handle,
                nodes = nodes,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }

    RememberGalleryScrollPosition(
        galleryKey = galleryKey,
        handle = handle,
        savedIndex = savedPositions[galleryKey]?.toIntOrNull(),
        enabled = active,
        onSave = { index -> scope.launch { repository.saveScrollPosition(galleryKey, index) } }
    )
}

/**
 * 最后浏览位置记忆：数据到位后恢复一次；滚动停下后防抖写回。
 *
 * [handle] 必须是 `remember(懒状态) { ...Handle(懒状态) }` 得到的**稳定对象**——
 * 每次重组新建句柄会让下面的保存协程不断重启。
 */
@OptIn(FlowPreview::class)
@Composable
fun RememberGalleryScrollPosition(
    galleryKey: String,
    handle: GalleryScrollHandle,
    savedIndex: Int?,
    enabled: Boolean,
    onSave: (Int) -> Unit
) {
    var restored by remember(galleryKey) { mutableStateOf(false) }
    LaunchedEffect(galleryKey, enabled, savedIndex, handle.totalCount) {
        if (!enabled || restored) return@LaunchedEffect
        if (handle.totalCount <= 0) return@LaunchedEffect
        restored = true
        val target = savedIndex ?: 0
        if (target > 0) {
            handle.scrollTo(target.coerceAtMost(handle.totalCount - 1))
        }
    }
    LaunchedEffect(galleryKey, enabled, handle) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow { handle.firstVisibleIndex }
            .distinctUntilChanged()
            .debounce(700)
            .collect { onSave(it) }
    }
}
