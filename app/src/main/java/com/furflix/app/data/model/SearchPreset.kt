package com.furflix.app.data.model

import kotlinx.serialization.Serializable

/**
 * 完整检索预设：高频检索的"查询词 + 筛选快照"一键重放。
 * 旧的 quick_presets（纯查询词字符串）读取时迁移为 name=query 的预设。
 */
@Serializable
data class SearchPreset(
    val name: String,
    val query: String,
    val filters: SearchFilters = SearchFilters()
)
