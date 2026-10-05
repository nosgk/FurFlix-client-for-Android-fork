package com.furflix.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 顶底栏随滚动显隐：下滚累计越过阈值隐藏、上滚小幅立即唤出。
 * 数据源由调用方提供（当前可见列表的 firstVisibleItemIndex/ScrollOffset），
 * 页面级 key（如选中 tab）变化时复位为可见。
 */
class BarsVisibility internal constructor() {
    var visible by mutableStateOf(true)
        private set

    private var downAcc = 0
    private var upAcc = 0

    internal fun update(dy: Int) {
        if (dy > 0) {
            downAcc += dy
            upAcc = 0
        } else if (dy < 0) {
            upAcc += -dy
            downAcc = 0
        } else {
            return
        }
        if (downAcc > HIDE_THRESHOLD_PX) {
            visible = false
            downAcc = 0
        }
        if (upAcc > SHOW_THRESHOLD_PX) {
            visible = true
            upAcc = 0
        }
    }

    fun show() {
        visible = true
        downAcc = 0
        upAcc = 0
    }

    companion object {
        const val HIDE_THRESHOLD_PX = 140
        const val SHOW_THRESHOLD_PX = 48
        /** 按整项跨页计一步：向下跨项视同一次大幅下滚。 */
        const val INDEX_STEP_PX = HIDE_THRESHOLD_PX + 1
    }
}

/**
 * @param key 列表身份（选中 tab / 视图模式），变化即复位可见
 * @param provider 返回当前列表的 (firstVisibleItemIndex, firstVisibleItemScrollOffset)；
 *                 返回 null（当前 tab 无注册列表）时保持可见
 */
@Composable
fun rememberBarsVisibility(
    key: Any?,
    provider: () -> Pair<Int, Int>?
): BarsVisibility {
    val bars = remember { BarsVisibility() }
    LaunchedEffect(key) {
        bars.show()
        var lastIndex = -1
        var lastOffset = -1
        snapshotFlow { provider() }
            .distinctUntilChanged()
            .collect { pos ->
                if (pos == null) {
                    bars.show()
                    lastIndex = -1
                    lastOffset = -1
                    return@collect
                }
                val (index, offset) = pos
                if (lastIndex >= 0 && (index != lastIndex || offset != lastOffset)) {
                    val dy = when {
                        index > lastIndex -> BarsVisibility.INDEX_STEP_PX
                        index < lastIndex -> -BarsVisibility.SHOW_THRESHOLD_PX * 2
                        else -> offset - lastOffset
                    }
                    bars.update(dy)
                }
                lastIndex = index
                lastOffset = offset
            }
    }
    return bars
}

/**
 * 分支级滚动源注册（自动隐藏栏的数据源）：组合期间把“当前列表的 (index, offset)”
 * 发布给 [register]；离开组合后自动上报 null。
 *
 * 不做显式注销：后来者覆盖注册位，被覆盖/已销毁的源自报 null（活性探测），
 * 两个分支短暂并存（如 Crossfade 过渡）或旧分支晚于新分支注销都不会误清注册。
 * 嵌套列表（“我的”页各子栏目、用户页等）用它向上注册，消灭“部分页面顶底栏
 * 不自动隐藏”的横向不一致。
 */
@Composable
fun LiveScrollSourceEffect(
    register: (((() -> Pair<Int, Int>?)?)) -> Unit,
    source: () -> Pair<Int, Int>
) {
    val alive = remember { mutableStateOf(true) }
    val hooked = remember { { if (alive.value) source() else null } }
    SideEffect { register(hooked) }
    DisposableEffect(Unit) {
        onDispose { alive.value = false }
    }
}
