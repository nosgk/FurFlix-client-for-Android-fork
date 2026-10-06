package com.furflix.app.data.model

import com.furflix.app.data.SEARCH_PAGE_SIZE
import kotlinx.serialization.Serializable

/**
 * Mirrors FA's search form as a value type so it can be persisted (last-used
 * filters survive restarts) and built deterministically. Hard constraint: the
 * query always posts mode=extended because @keywords/! operators require it.
 *
 * 2026-10-07 /search/ 表单实测：gender 复选框没有 name 属性——站点 JS
 * (gender_checkboxes_search.js) 在提交前把所选性别并进 @keywords 组（性别值
 * 本身就是作品关键词），因此这里不做 gender 表单键，走同一语义。range 支持
 * manual + range_from/range_to（yyyy-MM-dd，实测服务端回显生效）。
 */
@Serializable
data class SearchFilters(
    val orderBy: String = "date",
    val orderDirection: String = "desc",
    val ratingGeneral: Boolean = true,
    val ratingMature: Boolean = true,
    val ratingAdult: Boolean = true,
    val typeArt: Boolean = true,
    val typeFlash: Boolean = true,
    val typePhoto: Boolean = true,
    val typeMusic: Boolean = true,
    val typeStory: Boolean = true,
    val typePoetry: Boolean = true,
    val category: Int = 0,
    val artType: Int = 0,
    val species: Int = 0,
    val genders: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val perpage: Int = SEARCH_PAGE_SIZE,
    val range: String = "all",
    val rangeFrom: String = "",
    val rangeTo: String = "",
    val mode: String = "extended"
) {
    fun toSearchQueryMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        map["order-by"] = orderBy
        map["order-direction"] = orderDirection
        if (ratingGeneral) map["rating-general"] = "1"
        if (ratingMature) map["rating-mature"] = "1"
        if (ratingAdult) map["rating-adult"] = "1"
        if (typeArt) map["type-art"] = "1"
        if (typeFlash) map["type-flash"] = "1"
        if (typePhoto) map["type-photo"] = "1"
        if (typeMusic) map["type-music"] = "1"
        if (typeStory) map["type-story"] = "1"
        if (typePoetry) map["type-poetry"] = "1"
        // FA 只认原生枚举：旧版存过 "1week"/"1month"，投递前归一化
        map["range"] = normalizedRange
        if (normalizedRange == "manual") {
            if (rangeFrom.isNotEmpty()) map["range_from"] = rangeFrom
            if (rangeTo.isNotEmpty()) map["range_to"] = rangeTo
        }
        map["mode"] = mode
        if (category != 0) map["category"] = category.toString()
        if (artType != 0) map["arttype"] = artType.toString()
        if (species != 0) map["species"] = species.toString()
        return map
    }

    /** range 归一化到 FA 原生枚举（2026-10-07 /search/ 表单实测）。 */
    val normalizedRange: String
        get() = when (range) {
            "1week" -> "7days"
            "1month" -> "30days"
            else -> range
        }

    fun toBrowseQueryMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (category != 0) map["cat"] = category.toString()
        if (artType != 0) map["atype"] = artType.toString()
        if (species != 0) map["species"] = species.toString()
        // /browse/ 表单的 gender 仍是单选下拉：多选集合取第一项保持旧行为
        genders.firstOrNull()?.takeIf { it.isNotEmpty() }?.let { map["gender"] = it }
        map["rating_general"] = if (ratingGeneral) "1" else "0"
        map["rating_mature"] = if (ratingMature) "1" else "0"
        map["rating_adult"] = if (ratingAdult) "1" else "0"
        return map
    }

    /**
     * 组装 q：关键词组（keywords + genders 单组，保证 "!tag" 排除与性别
     * 过滤作用在同一组内，与站点 recompile_search_text 的产物一致）。
     */
    fun buildKeywordQuery(baseQuery: String): String {
        val groupTerms = keywords + genders
        if (groupTerms.isEmpty()) return baseQuery
        // Single @keywords group (iOS searchQueryText semantics): "!tag"
        // exclusions only take effect inside the same group as their includes.
        val kw = if (mode == "extended") {
            "@keywords " + groupTerms.joinToString(" ")
        } else {
            groupTerms.joinToString(" ")
        }
        return if (baseQuery.isBlank()) kw else "$baseQuery $kw"
    }

    /**
     * Deterministic search form — fixed key order, q/page/perpage always
     * present, filter map in declaration order. Pure and order-stable so a
     * unit test can assert the output verbatim.
     */
    fun buildSearchForm(query: String, page: Int): List<Pair<String, String>> = buildList {
        add("q" to buildKeywordQuery(query))
        add("page" to page.toString())
        add("perpage" to perpage.coerceIn(PERPAGE_MIN, PERPAGE_MAX).toString())
        toSearchQueryMap().forEach { (key, value) -> add(key to value) }
    }

    val hasBrowseFilters: Boolean
        get() = category != 0 || artType != 0 || species != 0 || genders.isNotEmpty()

    val hasKeywords: Boolean
        get() = keywords.isNotEmpty() || genders.isNotEmpty()

    val advancedFilterCount: Int
        get() {
            var count = 0
            if (keywords.isNotEmpty()) count++
            if (genders.isNotEmpty()) count++
            if (species != 0) count++
            if (!ratingGeneral || !ratingMature || !ratingAdult) count++
            if (!typeArt || !typeFlash || !typePhoto || !typeMusic || !typeStory || !typePoetry) count++
            if (range != "all") count++
            if (mode != "extended") count++
            if (orderBy != "date") count++
            return count
        }

    companion object {
        /** FA 原生每页档位（/search/ 表单实测：24/48/72）。 */
        const val PERPAGE_MIN = 24
        const val PERPAGE_MAX = 72
        val PERPAGE_CHOICES = listOf(24, 48, 72)
    }
}
