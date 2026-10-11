package com.furflix.app.data.model

import com.furflix.app.data.FA_ART_TYPES
import com.furflix.app.data.FA_CATEGORIES
import com.furflix.app.data.FA_GENDER_OPTIONS
import com.furflix.app.data.FA_SPECIES
import com.furflix.app.data.FA_SPECIES_OPTIONS
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

    /**
     * 把任意来源的取值收敛到 FA 站点的合法入参集合（幂等）。所有筛选进入应用
     * 的三个口子都要过这一关：URL 解析、DataStore 读回、UI 提交——任一处漏掉，
     * 非法值（旧版本存的枚举、手改的分享链接、越界 id）就会直接进请求，
     * 站点侧表现为"静默按默认筛选"或空结果，很难排查。
     *
     * 收敛规则一律是"回落到不过滤"（0 / 默认排序 / all），绝不猜测用户意图：
     * - category / arttype / species 必须命中站点词表 id，否则归 0；
     * - perpage 取 24/48/72 中最近的一档；
     * - order-by / order-direction / mode / range 白名单；
     * - range 非 manual 时清空日期；日期必须是 yyyy-MM-dd；
     * - genders 只保留站点性别词表内的值；keywords 去空白去重。
     */
    fun normalized(): SearchFilters {
        val r = normalizedRange.lowercase().takeIf { it in RANGE_VALUES } ?: "all"
        return copy(
            orderBy = orderBy.lowercase().takeIf { it in ORDER_BY_VALUES } ?: "date",
            orderDirection = orderDirection.lowercase().takeIf { it in ORDER_DIRECTION_VALUES } ?: "desc",
            range = r,
            rangeFrom = if (r == "manual" && DATE_RE.matches(rangeFrom)) rangeFrom else "",
            rangeTo = if (r == "manual" && DATE_RE.matches(rangeTo)) rangeTo else "",
            mode = mode.lowercase().takeIf { it in MODE_VALUES } ?: "extended",
            perpage = PERPAGE_CHOICES.minByOrNull { kotlin.math.abs(it - perpage) } ?: PERPAGE_MAX,
            category = category.takeIf { id -> FA_CATEGORY_IDS.contains(id) } ?: 0,
            artType = artType.takeIf { id -> FA_ART_TYPE_IDS.contains(id) } ?: 0,
            species = species.takeIf { id -> FA_SPECIES_IDS.contains(id) } ?: 0,
            genders = genders.map { it.trim() }.filter { it in FA_GENDER_VALUES }.distinct(),
            keywords = keywords.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        )
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

    /**
     * 当前筛选快照 → FA 原生搜索 URL（/search/?q=...，2026-10 实测 GET 表单的
     * 序列化形态）。粘贴到浏览器执行与站内发起同语义，也是搜索状态的分享形态。
     * 与 FaLinks.parseSearchFilters 互为镜像：布尔组按表单成功控件语义输出
     * （勾选才发键；全组取消时 URL 缺省=不过滤，属可接受的极限情形）。
     */
    fun toFaSearchUrl(query: String): String {
        val params = buildList {
            add("q" to buildKeywordQuery(query))
            add("page" to "1")
            add("perpage" to perpage.coerceIn(PERPAGE_MIN, PERPAGE_MAX).toString())
            toSearchQueryMap().forEach { (key, value) -> add(key to value) }
        }
        val qs = params.joinToString("&") { (key, value) ->
            "${java.net.URLEncoder.encode(key, "UTF-8")}=${java.net.URLEncoder.encode(value, "UTF-8")}"
        }
        return "https://www.furaffinity.net/search/?$qs"
    }

    val hasBrowseFilters: Boolean
        get() = category != 0 || artType != 0 || species != 0 || genders.isNotEmpty()

    val hasKeywords: Boolean
        get() = keywords.isNotEmpty() || genders.isNotEmpty()

    /**
     * 生效的筛选维度数(面板顶栏 "[N]" 徽标与快捷行的 activeFilterCount)。
     * 每个维度只计一次(同维度取消多项仍算 1);排序把 order-by ×
     * order-direction 视为**一个**维度,避免按相关度排序时被重复计数。
     */
    val advancedFilterCount: Int
        get() {
            var count = 0
            if (keywords.isNotEmpty()) count++
            if (genders.isNotEmpty()) count++
            if (category != 0) count++
            if (artType != 0) count++
            if (species != 0) count++
            if (!ratingGeneral || !ratingMature || !ratingAdult) count++
            if (!typeArt || !typeFlash || !typePhoto || !typeMusic || !typeStory || !typePoetry) count++
            if (range != "all") count++
            if (mode != "extended") count++
            if (orderBy != "date" || orderDirection != "desc") count++
            return count
        }

    companion object {
        /** FA 原生每页档位（/search/ 表单实测：24/48/72）。 */
        const val PERPAGE_MIN = 24
        const val PERPAGE_MAX = 72
        val PERPAGE_CHOICES = listOf(24, 48, 72)

        // ---- 合法入参集合（站点词表的镜像，供 normalized() 校验）----

        private val ORDER_BY_VALUES = setOf("date", "relevancy", "popularity")
        private val ORDER_DIRECTION_VALUES = setOf("asc", "desc")
        private val MODE_VALUES = setOf("all", "any", "extended")
        private val RANGE_VALUES = setOf(
            "all", "1day", "3days", "7days", "30days", "90days",
            "1year", "3years", "5years", "manual"
        )
        private val DATE_RE = Regex("""\d{4}-\d{2}-\d{2}""")

        private val FA_CATEGORY_IDS = FA_CATEGORIES.map { it.first }.toSet()
        private val FA_ART_TYPE_IDS = FA_ART_TYPES.map { it.first }.toSet()

        /**
         * 物种 id 取两张表的并集：站点全量表（[FA_SPECIES]，398 项，搜索面板用）
         * 与精简选项表（[FA_SPECIES_OPTIONS]，140 项，行内下拉用）。只认其中一张
         * 会把另一张产出的合法 id 误判为非法——这正是"改共享层要逐面检查"的
         * 典型多米诺位点。
         */
        private val FA_SPECIES_IDS: Set<Int> =
            (FA_SPECIES.map { it.first } + FA_SPECIES_OPTIONS.map { it.first }).toSet()

        private val FA_GENDER_VALUES = FA_GENDER_OPTIONS.map { it.first }.toSet()

        /**
         * [buildKeywordQuery] 的逆变换（extended 模式产物）：把 q 里的
         * "@keywords 组" 拆回 (基查询, keywords, genders)。性别 token 按站点
         * 词表精确匹配回填——2026-10-07 浏览器实测 FA 自己也把组回填进性别
         * 复选框（?q=@keywords fox male 会勾上 male），此行为与站点语义一致。
         * 无 @keywords 组（自由文本/all-any 模式）返回 null，q 不拆。
         * 组 term 本身含空格或多词关键词在此形态下本就不可区分（空格连接），
         * 按空格逐 token 拆分即其精确逆运算。
         */
        fun splitKeywordQuery(q: String): Triple<String, List<String>, List<String>>? {
            val trimmed = q.trim()
            val base: String
            val groupTerms: List<String>
            if (trimmed.startsWith("@keywords ")) {
                base = ""
                groupTerms = trimmed.removePrefix("@keywords ").split(' ').filter { it.isNotEmpty() }
            } else {
                val marker = " @keywords "
                val idx = trimmed.lastIndexOf(marker)
                if (idx < 0) return null
                base = trimmed.take(idx)
                groupTerms = trimmed.substring(idx + marker.length).split(' ').filter { it.isNotEmpty() }
            }
            if (groupTerms.isEmpty()) return null
            val genderValues = com.furflix.app.data.FA_GENDER_OPTIONS
                .map { it.first }.filter { it.isNotEmpty() }.toSet()
            val genders = groupTerms.filter { it in genderValues }
            val keywords = groupTerms.filter { it !in genderValues }
            return Triple(base, keywords, genders)
        }
    }
}
