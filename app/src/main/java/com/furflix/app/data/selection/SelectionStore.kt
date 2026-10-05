package com.furflix.app.data.selection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SelectionState(
    val active: Boolean = false,
    val source: String = "",
    val ids: Set<String> = emptySet()
)

/**
 * 进程内多选状态（与 DownloadManager 同模式的单例）。
 * [SelectionState.source] 标识来源列表（browse/latest/search），
 * 切换列表时旧选择自动失效；ids 为空即自动退出多选。
 */
object SelectionStore {
    private val _state = MutableStateFlow(SelectionState())
    val state: StateFlow<SelectionState> = _state.asStateFlow()

    fun toggle(source: String, id: String) {
        _state.update { s ->
            if (!s.active || s.source != source) {
                SelectionState(active = true, source = source, ids = setOf(id))
            } else {
                val ids = if (id in s.ids) s.ids - id else s.ids + id
                if (ids.isEmpty()) SelectionState() else s.copy(ids = ids)
            }
        }
    }

    fun selectAll(source: String, ids: Collection<String>) {
        _state.update { SelectionState(active = true, source = source, ids = ids.toSet()) }
    }

    fun exit() {
        _state.value = SelectionState()
    }
}
