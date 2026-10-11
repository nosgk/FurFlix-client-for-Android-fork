package com.furflix.app.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import com.furflix.app.R
import com.furflix.app.data.download.DownloadManager
import com.furflix.app.data.model.Submission
import com.furflix.app.data.remote.FurAffinityScraper
import com.furflix.app.data.repository.FurRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FurRepository.getInstance(application)
    private val listCache = com.furflix.app.data.local.ListCacheStore.getInstance(application)

    private val _browseSubmissions = MutableStateFlow<List<Submission>>(emptyList())
    val browseSubmissions: StateFlow<List<Submission>> = _browseSubmissions.asStateFlow()

    private val _latestSubmissions = MutableStateFlow<List<Submission>>(emptyList())
    val latestSubmissions: StateFlow<List<Submission>> = _latestSubmissions.asStateFlow()

    /** 缓存优先指示：当前列表整页来自本地缓存（下拉刷新后熄灭），供页面提示条展示 */
    private val _browseRestoredFromCache = MutableStateFlow(false)
    val browseRestoredFromCache: StateFlow<Boolean> = _browseRestoredFromCache.asStateFlow()
    private val _latestRestoredFromCache = MutableStateFlow(false)
    val latestRestoredFromCache: StateFlow<Boolean> = _latestRestoredFromCache.asStateFlow()

    private val _searchSubmissions = MutableStateFlow<List<Submission>>(emptyList())
    val searchSubmissions: StateFlow<List<Submission>> = _searchSubmissions.asStateFlow()

    private val _submissionDetails = MutableStateFlow<Map<String, Submission>>(emptyMap())
    val submissionDetails: StateFlow<Map<String, Submission>> = _submissionDetails.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private val _searchPresets = MutableStateFlow<List<com.furflix.app.data.model.SearchPreset>>(emptyList())
    val searchPresets: StateFlow<List<com.furflix.app.data.model.SearchPreset>> = _searchPresets.asStateFlow()

    private val _searchFilters = MutableStateFlow(com.furflix.app.data.model.SearchFilters())
    val searchFilters: StateFlow<com.furflix.app.data.model.SearchFilters> = _searchFilters.asStateFlow()

    private val _tagSuggestions = MutableStateFlow<List<String>>(emptyList())
    val tagSuggestions: StateFlow<List<String>> = _tagSuggestions.asStateFlow()

    // 已看帖子的统计缓存（列表卡片浮层的数据源），随详情页加载与收藏动作保持新鲜
    private val cardStatsCache by lazy { com.furflix.app.data.local.CardStatsCache.getInstance(getApplication()) }
    private val _cardStats = MutableStateFlow<Map<String, com.furflix.app.data.model.CardStats>>(emptyMap())
    val cardStats: StateFlow<Map<String, com.furflix.app.data.model.CardStats>> = _cardStats.asStateFlow()

    /**
     * 统计浮层与浏览历史共用一份快照；收藏动作只覆盖计数与标记，展示字段沿用详情解析结果。
     * 字段映射收敛在 CardStats.of（与共享预热器同源），visited 仅由真实到访置 true。
     */
    private fun cardStatsOf(
        sub: Submission,
        favorited: Boolean = sub.isFavorited,
        favorites: Int = sub.favorites,
        visited: Boolean = false
    ): com.furflix.app.data.model.CardStats = com.furflix.app.data.model.CardStats.of(
        sub,
        visited = visited,
        favorited = favorited,
        favorites = favorites
    )

    private var searchTagsJob: kotlinx.coroutines.Job? = null

    fun fetchTagSuggestions(prefix: String) {
        searchTagsJob?.cancel()
        if (prefix.isBlank()) {
            _tagSuggestions.value = emptyList()
            return
        }
        searchTagsJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300) // Debounce 300ms
            _tagSuggestions.value = repository.searchTags(prefix)
        }
    }

    private val _nsfwEnabled = MutableStateFlow(true)
    val nsfwEnabled: StateFlow<Boolean> = _nsfwEnabled.asStateFlow()

    private val _downloadFolder = MutableStateFlow("")
    val downloadFolder: StateFlow<String> = _downloadFolder.asStateFlow()

    // ── 搜索请求生命周期 ──
    // 每次搜索持有唯一 Job 与递增代次号：旧请求被 cancel，迟到的回包按代次
    // 丢弃——杜绝网络异步延迟/回包乱序/状态残留导致的历史搜索词与当前结果
    // 交叉混淆；筛选连续调整走防抖合并，只发最后一次查询。
    private var searchJob: kotlinx.coroutines.Job? = null
    private var loadMoreJob: kotlinx.coroutines.Job? = null
    private var searchGeneration = 0

    fun updateSearchFilters(filters: com.furflix.app.data.model.SearchFilters) {
        // UI 是最后一个入口：面板里的越界组合（如手动日期只填一半）也在这里
        // 收敛成站点合法入参，请求与持久化读到的都是同一份规范值
        _searchFilters.value = filters.normalized()
        if (_searchQuery.value.isNotEmpty() || filters.hasKeywords || _selectedTab.value == 2) {
            scheduleSearchReload()
        }
    }

    /** 筛选变更触发的重搜：防抖合并，仅最后一次调整真正发起请求。 */
    fun scheduleSearchReload() {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        val gen = ++searchGeneration
        searchJob = viewModelScope.launch {
            delay(SEARCH_RELOAD_DEBOUNCE_MS)
            searchOnce(_searchQuery.value, _searchFilters.value, gen)
        }
    }

    private suspend fun searchOnce(query: String, filters: com.furflix.app.data.model.SearchFilters, gen: Int) {
        _searchSubmissions.value = emptyList()
        _searchQueryStats.value = null
        _isLoading.value = true
        _errorMessage.value = null
        try {
            val result = repository.search(query, 1, filters)
            if (gen != searchGeneration) return // 过期回包：不落结果集
            _searchSubmissions.value = result.submissions
            _searchQueryStats.value = result.stats
            prefetchSubmissionStats(result.submissions.map { it.id })
            // An empty result is the empty state, not an error — the screen
            // renders its "no results" branch without an error banner.
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (gen == searchGeneration) {
                _errorMessage.value = getApplication<Application>().getString(
                    R.string.error_generic, e.message ?: ""
                )
            }
        }
        if (gen == searchGeneration) _isLoading.value = false
    }

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _isTogglingFavorite = MutableStateFlow(false)
    val isTogglingFavorite: StateFlow<Boolean> = _isTogglingFavorite.asStateFlow()

    fun toggleFavorite(postId: String) {
        if (_isTogglingFavorite.value) return
        val details = _submissionDetails.value[postId] ?: return
        if (details.favUrl.isEmpty()) return
        val addToFavorites = !details.isFavorited
        val currentFavUrl = details.favUrl
        // 收藏动作不改变“是否到访”的语义：真实浏览过的仍是历史，批量收藏不产生历史
        val wasVisited = _cardStats.value[postId]?.visited ?: false
        _isTogglingFavorite.value = true
        // Optimistic update — toggle state + swap the fav/unfav URL
        _submissionDetails.update { current ->
            val newMap = current.toMutableMap()
            newMap[postId] = details.copy(
                isFavorited = addToFavorites,
                favUrl = currentFavUrl.replace(
                    if (addToFavorites) "/unfav/" else "/fav/",
                    if (addToFavorites) "/fav/" else "/unfav/"
                ),
                favorites = details.favorites + (if (addToFavorites) 1 else -1)
            )
            newMap
        }
        viewModelScope.launch {
            // 卡片浮层的数据源与详情页同步更新：乐观写新值，失败回滚后写回原值
            cardStatsCache.put(
                postId,
                cardStatsOf(details, favorited = addToFavorites, favorites = details.favorites + if (addToFavorites) 1 else -1, visited = wasVisited)
            )
            try {
                val success = repository.toggleFavorite(currentFavUrl)
                if (!success) {
                    // Revert on failure
                    _submissionDetails.update { current ->
                        val newMap = current.toMutableMap()
                        newMap[postId] = details
                        newMap
                    }
                    cardStatsCache.put(postId, cardStatsOf(details, visited = wasVisited))
                }
            } catch (_: Exception) {
                _submissionDetails.update { current ->
                    val newMap = current.toMutableMap()
                    newMap[postId] = details
                    newMap
                }
                cardStatsCache.put(postId, cardStatsOf(details, visited = wasVisited))
            }
            _isTogglingFavorite.value = false
        }
    }

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun resetSearch() {
        _hasSearched.value = false
        _searchQuery.value = ""
        _searchSubmissions.value = emptyList()
        _searchQueryStats.value = null
        _searchFilters.value = com.furflix.app.data.model.SearchFilters()
    }

    fun clearRecentSearches() {
        viewModelScope.launch { repository.saveRecentSearches(emptyList()) }
    }

    fun clearCardStats() {
        viewModelScope.launch { cardStatsCache.clear() }
    }

    // 各画廊列表页通用的统计预热：走共享预热器（会话去重+缓存 TTL 跳过+有界并发），
    // 回填按整批落缓存并顺带整批并入详情缓存（之后点进详情免二次抓取）；
    // 不触碰 isLoading/errorMessage。整批并入避免逐条触发热流发射引发成片重组。
    fun prefetchSubmissionStats(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            com.furflix.app.data.local.CardStatsPrefetcher.prefetch(
                getApplication(),
                ids
            ) { batch ->
                _submissionDetails.update { current -> current + batch }
            }
        }
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch {
            val currentList = repository.loadRecentSearches().toMutableList()
            currentList.remove(query)
            repository.saveRecentSearches(currentList)
        }
    }

    /** 完整检索预设（名称+查询词+筛选快照）：新的在前，按内容去重，上限 20。 */
    fun addSearchPreset(name: String, query: String, filters: com.furflix.app.data.model.SearchFilters) {
        val trimmedQuery = query.trim()
        val resolvedName = name.trim().ifEmpty { trimmedQuery }
        if (resolvedName.isEmpty()) return
        viewModelScope.launch {
            repository.saveSearchPresets(
                (listOf(com.furflix.app.data.model.SearchPreset(resolvedName, trimmedQuery, filters)) + _searchPresets.value)
                    .distinctBy { it.query to it.filters }
                    .take(20)
            )
        }
    }

    fun removeSearchPreset(preset: com.furflix.app.data.model.SearchPreset) {
        viewModelScope.launch {
            repository.saveSearchPresets(_searchPresets.value - preset)
        }
    }

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private var browsePage = 1
    private var latestPage = 1
    private var searchPage = 1
    private var watchlistPage = 1

    private var browseHasMore = true
    private var latestHasMore = true
    private var searchHasMore = true
    private var watchlistHasMore = true

    /** 浏览 tab 进行中会话的筛选快照（loadBrowse 时取值，翻页沿用同一份）。 */
    private var browseActiveFilters = com.furflix.app.data.model.SearchFilters()

    companion object {
        const val TAB_BROWSE = 0
        const val TAB_LATEST = 1
        const val TAB_SEARCH = 2
        const val TAB_WATCHLIST = 3

        /** 筛选连续调整的防抖窗口：最后一次调整后 450ms 才真正发请求。 */
        private const val SEARCH_RELOAD_DEBOUNCE_MS = 450L
    }

    init {
        com.furflix.app.data.local.TagsDatabase.getInstance(application).prefetch()
        viewModelScope.launch {
            val initialNsfw = repository.nsfwEnabledFlow.first()
            _nsfwEnabled.value = initialNsfw
            // Restore last-used search filters (order/range/types/keywords survive
            // restarts), then re-apply the NSFW gate — the sfw cookie outranks
            // remembered rating checkboxes.
            val restoredFilters = repository.loadLastSearchFilters()
                ?: com.furflix.app.data.model.SearchFilters()
            _searchFilters.value = restoredFilters.copy(
                ratingMature = initialNsfw,
                ratingAdult = initialNsfw
            )

            val defaultTab = repository.defaultTabFlow.first()
            _selectedTab.value = defaultTab
            // Trigger load based on default tab（缓存优先：本地有就先出，不自动联网）
            when (defaultTab) {
                0 -> loadBrowse(allowCacheRestore = true)
                1 -> loadLatest(allowCacheRestore = true)
            }
        }
        _isLoggedIn.value = FurAffinityScraper.isLoggedIn()
        viewModelScope.launch {
            cardStatsCache.flow.collect { _cardStats.value = it }
        }
        viewModelScope.launch {
            repository.recentSearchesFlow.collect {
                _recentSearches.value = it
            }
        }
        viewModelScope.launch {
            repository.searchPresetsFlow.collect {
                _searchPresets.value = it
            }
        }
        viewModelScope.launch {
            repository.downloadFolderFlow.collect {
                _downloadFolder.value = it
            }
        }
        viewModelScope.launch {
            repository.nsfwEnabledFlow.collect { nsfw ->
                val changed = _nsfwEnabled.value != nsfw
                _nsfwEnabled.value = nsfw
                if (changed) {
                    _searchFilters.value = _searchFilters.value.copy(
                        ratingMature = nsfw,
                        ratingAdult = nsfw
                    )
                }
                if (changed && hasInitialized) {
                    when (_selectedTab.value) {
                        TAB_BROWSE -> loadBrowse()
                        TAB_LATEST -> loadLatest()
                        TAB_SEARCH -> loadSearch(_searchQuery.value)
                        TAB_WATCHLIST -> { /* Profile handles its own refresh */ }
                    }
                }
            }
        }
    }

    fun toggleNsfw() {
        viewModelScope.launch {
            repository.saveNsfwEnabled(!_nsfwEnabled.value)
        }
    }

    private var hasInitialized = false

    fun tryAutoLogin() {
        if (hasInitialized) return
        hasInitialized = true
        viewModelScope.launch {
            val success = repository.loadSavedCookies()
            if (success) {
                _isLoggedIn.value = true
                _username.value = repository.getUsername()
                if (_browseSubmissions.value.isEmpty()) {
                    loadBrowse()
                }
            } else if (_browseSubmissions.value.isEmpty()) {
                loadBrowse()
            }
        }
    }

    fun loginWithCookies(cookies: String, webViewUsername: String = "") {
        FurAffinityScraper.setCookiesFromWebView(cookies)
        viewModelScope.launch {
            var name = webViewUsername
            if (name.isEmpty() || name == "Logged in") {
                val fetchedName = FurAffinityScraper.verifyLoginAndGetUsername()
                if (!fetchedName.isNullOrEmpty()) {
                    name = fetchedName
                } else {
                    name = "Logged in"
                }
            }
            repository.saveCookiesFromWebView(cookies, name)
            _isLoggedIn.value = true
            _username.value = name
            
            when (_selectedTab.value) {
                TAB_BROWSE -> loadBrowse()
                TAB_LATEST -> loadLatest()
                TAB_SEARCH -> loadSearch(_searchQuery.value)
                TAB_WATCHLIST -> { /* Profile handles its own refresh */ }
                else -> loadBrowse()
            }
        }
    }

    /**
     * 浏览 tab 加载：[allowCacheRestore]（首次进入/冷启动）时先吃本地缓存，
     * 命中即零流量出列表、不发网络请求；下拉刷新/NSFW 翻转/登录态变化走网络
     * 并整页回写缓存——缓存优先，刷新即校验。
     */
    fun loadBrowse(allowCacheRestore: Boolean = false) {
        browsePage = 1
        browseHasMore = true
        // 浏览 tab 的筛选快照：一次会话内（含 loadMore 翻页）用同一份，
        // 搜索 tab 的筛选调整不再静默改写进行中的浏览会话
        browseActiveFilters = _searchFilters.value
        viewModelScope.launch {
            if (allowCacheRestore) {
                val cached = listCache.load(
                    com.furflix.app.data.local.ListCacheStore.KEY_BROWSE, _nsfwEnabled.value
                )
                if (cached != null && _browseSubmissions.value.isEmpty()) {
                    _browseSubmissions.value = cached
                    _browseRestoredFromCache.value = true
                    return@launch
                }
            }
            fetchBrowseFirstPage()
        }
    }

    private fun fetchBrowseFirstPage() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = repository.getBrowse(browsePage, browseActiveFilters)
                _browseSubmissions.value = result
                _browseRestoredFromCache.value = false
                listCache.save(
                    com.furflix.app.data.local.ListCacheStore.KEY_BROWSE, result, _nsfwEnabled.value
                )
                // 列表页 HTML 不带统计：整页投喂预热，卡片浮层的数字/类型徽标随后点亮
                prefetchSubmissionStats(result.map { it.id })
                // Do not set errorMessage when empty — can happen briefly on startup
            } catch (e: Exception) {
                _errorMessage.value = getApplication<Application>().getString(
                    R.string.error_generic, e.message ?: ""
                )
            }
            _isLoading.value = false
        }
    }

    /** 最新 tab 加载：缓存优先语义同 [loadBrowse]（Latest 翻页游标随缓存失效，翻到底自动停）。 */
    fun loadLatest(allowCacheRestore: Boolean = false) {
        latestPage = 1
        latestHasMore = true
        viewModelScope.launch {
            if (allowCacheRestore) {
                val cached = listCache.load(
                    com.furflix.app.data.local.ListCacheStore.KEY_LATEST, _nsfwEnabled.value
                )
                if (cached != null && _latestSubmissions.value.isEmpty()) {
                    _latestSubmissions.value = cached
                    _latestRestoredFromCache.value = true
                    return@launch
                }
            }
            fetchLatestFirstPage()
        }
    }

    private fun fetchLatestFirstPage() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = repository.getLatest(latestPage)
                _latestSubmissions.value = result
                _latestRestoredFromCache.value = false
                listCache.save(
                    com.furflix.app.data.local.ListCacheStore.KEY_LATEST, result, _nsfwEnabled.value
                )
                prefetchSubmissionStats(result.map { it.id })
                // Do not set errorMessage when empty
            } catch (e: Exception) {
                _errorMessage.value = getApplication<Application>().getString(
                    R.string.error_generic, e.message ?: ""
                )
            }
            _isLoading.value = false
        }
    }

    private val _batchFavoriteRunning = MutableStateFlow(false)

    /**
     * 批量收藏/取消收藏：逐帖确保详情已解析（favUrl 必须来自详情页的 +Fav/-Fav 按钮），
     * 已达目标状态的帖子跳过（绝不反向操作），网络结果逐项计数。
     * 不再复用单帖 toggleFavorite 的全局互斥——旧实现里批量循环 300ms 间隔小于一次
     * 抓详情+收藏的耗时，后续项会被互斥锁静默丢弃却照常计入"已收藏"，正是
     * "部分作品没收藏上"的根因。串行 + 每次真实网络动作后 300ms 间隔容错 FA 速率。
     */
    private fun runBatchFavorite(postIds: List<String>, targetFavorited: Boolean) {
        if (_batchFavoriteRunning.value || postIds.isEmpty()) return
        viewModelScope.launch {
            _batchFavoriteRunning.value = true
            var succeeded = 0
            var already = 0
            var failed = 0
            for (id in postIds) {
                try {
                    var details = _submissionDetails.value[id]
                    if (details == null) {
                        repository.getSubmission(id)?.let { fetched ->
                            details = fetched
                            _submissionDetails.update { it + (id to fetched) }
                        }
                    }
                    val current = details
                    if (current == null || current.favUrl.isEmpty()) {
                        // 自己的作品/受限帖子没有 fav 按钮，只能如实计为失败
                        failed++
                    } else if (current.isFavorited == targetFavorited) {
                        already++
                    } else {
                        // fav/unfav 共用同一 CSRF key，方向由 URL 段互换表达
                        val targetUrl = if (targetFavorited) {
                            current.favUrl.replace("/unfav/", "/fav/")
                        } else {
                            current.favUrl.replace("/fav/", "/unfav/")
                        }
                        if (repository.toggleFavorite(targetUrl)) {
                            succeeded++
                            // 收藏动作不改变"是否到访"的语义：预热/历史归属原样保留
                            val wasVisited = _cardStats.value[id]?.visited ?: false
                            val updated = current.copy(
                                isFavorited = targetFavorited,
                                favUrl = targetUrl,
                                favorites = current.favorites + if (targetFavorited) 1 else -1
                            )
                            _submissionDetails.update { it + (id to updated) }
                            cardStatsCache.put(id, cardStatsOf(updated, visited = wasVisited))
                        } else {
                            failed++
                        }
                        delay(300)
                    }
                } catch (_: Exception) {
                    failed++
                }
            }
            _batchFavoriteRunning.value = false
            val app = getApplication<Application>()
            val message = when {
                succeeded > 0 && already > 0 -> app.getString(
                    if (targetFavorited) R.string.toast_batch_favorite_done_with_already
                    else R.string.toast_batch_unfavorite_done_with_already,
                    succeeded, already
                )
                succeeded > 0 -> app.getString(
                    if (targetFavorited) R.string.toast_batch_favorite_done
                    else R.string.toast_batch_unfavorite_done,
                    succeeded
                )
                already > 0 && failed == 0 -> app.getString(R.string.toast_batch_favorite_already_only, already)
                failed > 0 -> app.getString(R.string.toast_batch_favorite_failed, failed)
                else -> null
            }
            if (message != null) {
                Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 批量收藏：只对未收藏的帖子发 +fav，已收藏的跳过不反操作 */
    fun batchFavorite(postIds: List<String>) = runBatchFavorite(postIds, targetFavorited = true)

    /** 批量取消收藏（"我的-收藏"栏多选用）：只对已收藏的帖子发 -fav */
    fun batchUnfavorite(postIds: List<String>) = runBatchFavorite(postIds, targetFavorited = false)

    /** 批量移除浏览历史：visited 置 false（统计浮层保留，仅退出历史栏），供"我的-历史"多选使用 */
    fun removeHistoryEntries(postIds: List<String>) {
        if (postIds.isEmpty()) return
        viewModelScope.launch {
            val snapshot = _cardStats.value
            for (id in postIds) {
                snapshot[id]?.let { cardStatsCache.put(id, it.copy(visited = false)) }
            }
        }
    }

    /**
     * 相邻页预取：解析详情（命中缓存则跳过）并把原图 enqueue 进 Coil 缓存。
     * 进度上报按 URL 键隔离，预取流量不会污染当前页的进度显示。
     */
    fun prefetchSubmission(postId: String) {
        if (postId.isBlank()) return
        viewModelScope.launch {
            if (_submissionDetails.value[postId] == null) {
                try {
                    val sub = repository.getSubmission(postId)
                    if (sub != null) {
                        _submissionDetails.update { it + (postId to sub) }
                    }
                } catch (_: Exception) {
                }
            }
            val sub = _submissionDetails.value[postId]
            if (sub?.fullImageUrl?.isNotEmpty() == true) {
                val request = coil3.request.ImageRequest.Builder(getApplication())
                    .data(sub.fullImageUrl)
                    .build()
                com.furflix.app.ui.components.ImageLoaderFactory
                    .getInstance(getApplication())
                    .enqueue(request)
            }
        }
    }

    /**
     * 服务端检索统计（#query-stats：逐词命中 + 结果区间 + 耗时）。
     * 随一次搜索原子更新；null = 尚无/已失效。取代旧的逐词全量搜索假计数。
     */
    private val _searchQueryStats = MutableStateFlow<com.furflix.app.data.model.SearchQueryStats?>(null)
    val searchQueryStats: StateFlow<com.furflix.app.data.model.SearchQueryStats?> =
        _searchQueryStats.asStateFlow()

    /**
     * 发起一次全新搜索：原子替换 (查询词, 筛选)，取消旧请求，
     * 旧回包按代次号丢弃——单次查询逻辑独立闭环。
     */
    fun loadSearch(query: String, filters: com.furflix.app.data.model.SearchFilters? = null) {
        if (filters != null) _searchFilters.value = filters
        val currentFilters = _searchFilters.value
        if (query.isBlank() && !currentFilters.hasKeywords) return
        _searchQuery.value = query
        _hasSearched.value = true
        searchPage = 1
        searchHasMore = true

        val trimmed = query.trim()
        val itemsToAdd = mutableListOf<String>()
        if (trimmed.isNotEmpty()) itemsToAdd.add(trimmed)
        itemsToAdd.addAll(currentFilters.keywords)
        itemsToAdd.addAll(currentFilters.genders)

        // Remember the filter set for the next launch (prefills the panels only;
        // nothing is auto-researched on startup).
        viewModelScope.launch { repository.saveSearchFilters(currentFilters) }

        if (itemsToAdd.isNotEmpty()) {
            viewModelScope.launch {
                itemsToAdd.reversed().forEach { item ->
                    repository.addRecentSearch(item)
                }
            }
        }

        searchJob?.cancel()
        loadMoreJob?.cancel()
        val gen = ++searchGeneration
        searchJob = viewModelScope.launch {
            searchOnce(query, currentFilters, gen)
        }
    }

    fun loadWatchlist() {
        watchlistPage = 1
        watchlistHasMore = true
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = repository.getWatchlist(1)
                _browseSubmissions.value = result // shared with Profile tab
                if (result.isEmpty()) {
                    _errorMessage.value = getApplication<Application>().getString(R.string.error_watchlist_empty)
                }
            } catch (e: Exception) {
                _errorMessage.value = getApplication<Application>().getString(
                    R.string.error_login_required, e.message ?: ""
                )
            }
            _isLoading.value = false
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value) return
        _isLoadingMore.value = true
        loadMoreJob?.cancel()
        val gen = searchGeneration
        val searchFilters = _searchFilters.value
        loadMoreJob = viewModelScope.launch {
            try {
                val (page, result) = when (_selectedTab.value) {
                    TAB_BROWSE -> {
                        if (!browseHasMore) { _isLoadingMore.value = false; return@launch }
                        browsePage++
                        browsePage to repository.getBrowse(browsePage, browseActiveFilters)
                    }
                    TAB_LATEST -> {
                        if (!latestHasMore) { _isLoadingMore.value = false; return@launch }
                        latestPage++
                        latestPage to repository.getLatest(latestPage)
                    }
                    TAB_SEARCH -> {
                        if (!searchHasMore) { _isLoadingMore.value = false; return@launch }
                        // 新搜索仍在途（或已被替换）：绝不把旧页追加进新结果集
                        if (searchJob?.isActive == true || gen != searchGeneration) {
                            _isLoadingMore.value = false; return@launch
                        }
                        searchPage++
                        searchPage to repository.search(_searchQuery.value, searchPage, searchFilters).submissions
                    }
                    else -> {
                        if (!browseHasMore) { _isLoadingMore.value = false; return@launch }
                        browsePage++
                        browsePage to repository.getBrowse(browsePage, browseActiveFilters)
                    }
                }
                if (result.isEmpty()) {
                    when (_selectedTab.value) {
                        TAB_BROWSE -> { browsePage--; browseHasMore = false }
                        TAB_LATEST -> { latestPage--; latestHasMore = false }
                        TAB_SEARCH -> { searchPage--; searchHasMore = false }
                    }
                } else {
                    when (_selectedTab.value) {
                        TAB_BROWSE -> {
                            val unique = result.filter { r -> _browseSubmissions.value.none { it.id == r.id } }
                            browseHasMore = unique.isNotEmpty()
                            if (unique.isNotEmpty()) {
                                _browseSubmissions.value = _browseSubmissions.value + unique
                                prefetchSubmissionStats(unique.map { it.id })
                            }
                        }
                        TAB_LATEST -> {
                            val unique = result.filter { r -> _latestSubmissions.value.none { it.id == r.id } }
                            latestHasMore = unique.isNotEmpty()
                            if (unique.isNotEmpty()) {
                                _latestSubmissions.value = _latestSubmissions.value + unique
                                prefetchSubmissionStats(unique.map { it.id })
                            }
                        }
                    TAB_SEARCH -> {
                        if (gen != searchGeneration) {
                            // 请求在途期间发生了新搜索：丢弃旧页并回滚页码
                            searchPage--
                            _isLoadingMore.value = false
                            return@launch
                        }
                        val unique = result.filter { r -> _searchSubmissions.value.none { it.id == r.id } }
                        // Full page = possibly more pages; a short page means the
                        // site has no more to give, so don't burn one more request
                        // just to learn that.
                        searchHasMore = result.size >= searchFilters.perpage && unique.isNotEmpty()
                        if (unique.isNotEmpty()) {
                            _searchSubmissions.value = _searchSubmissions.value + unique
                            prefetchSubmissionStats(unique.map { it.id })
                        }
                    }
                    }
                }
            } catch (e: Exception) {
                when (_selectedTab.value) {
                    TAB_BROWSE -> browsePage--
                    TAB_LATEST -> latestPage--
                    TAB_SEARCH -> searchPage--
                }
            }
            _isLoadingMore.value = false
        }
    }

    fun loadSubmission(postId: String) {
        _submissionDetails.value[postId]?.let { cached ->
            // 已在内存：不重复抓取，但仍把浏览历史/统计顶到最新；真实到访 → visited
            viewModelScope.launch { cardStatsCache.put(postId, cardStatsOf(cached, visited = true)) }
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = repository.getSubmission(postId)
                if (result != null) {
                    _submissionDetails.update { currentMap ->
                        val newMap = currentMap.toMutableMap()
                        newMap[postId] = result
                        newMap
                    }
                    cardStatsCache.put(postId, cardStatsOf(result, visited = true))
                } else {
                    _errorMessage.value = getApplication<Application>().getString(R.string.error_load_details)
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
                    ?: getApplication<Application>().getString(R.string.error_unknown)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 下载一件作品：仅入队 DownloadManager（进度/速度/状态由任务流承载）。
     * 下载方式分流：original = 下载原件（d.furaffinity.net，无下载锚点回落展示
     * 文件）；copy（默认）= 复制详情页正式载入的展示文件。
     */
    fun downloadImage(sub: Submission) {
        viewModelScope.launch {
            val method = repository.downloadMethodFlow.first()
            val url = if (method == FurRepository.DOWNLOAD_METHOD_ORIGINAL) {
                sub.fileUrl.ifEmpty { sub.fullImageUrl }
            } else {
                sub.fullImageUrl
            }
            if (url.isEmpty()) return@launch
            DownloadManager.enqueue(
                context = getApplication(),
                submissionId = sub.id,
                url = url,
                author = sub.author,
                title = sub.title
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _isLoggedIn.value = false
            _username.value = ""
            _browseSubmissions.value = emptyList()
            _latestSubmissions.value = emptyList()
            _searchSubmissions.value = emptyList()
            _browseRestoredFromCache.value = false
            _latestRestoredFromCache.value = false
        }
    }

    // --- Cache management ---
    private val _cacheSize = MutableStateFlow("Calculating...")
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    private val _isClearingCache = MutableStateFlow(false)
    val isClearingCache: StateFlow<Boolean> = _isClearingCache.asStateFlow()

    fun refreshCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val cacheDir = getApplication<Application>().cacheDir.resolve("image_cache")
            val sizeBytes = cacheDir.walkTopDown()
                .filter { it.isFile }
                .sumOf { it.length() }
            val formatted = when {
                sizeBytes >= 1_073_741_824L -> String.format(java.util.Locale.US, "%.1f GB", sizeBytes / 1_073_741_824.0)
                sizeBytes >= 1_048_576L     -> String.format(java.util.Locale.US, "%.1f MB", sizeBytes / 1_048_576.0)
                sizeBytes >= 1_024L         -> String.format(java.util.Locale.US, "%.1f KB", sizeBytes / 1_024.0)
                else                        -> "$sizeBytes B"
            }
            _cacheSize.value = formatted
        }
    }

    @OptIn(coil3.annotation.DelicateCoilApi::class)
    fun clearImageCache() {
        viewModelScope.launch(Dispatchers.IO) {
            _isClearingCache.value = true
            try {
                // Clear memory + disk cache via factory reset
                com.furflix.app.ui.components.ImageLoaderFactory.reset()

                // Delete the cache directory entirely
                val cacheDir = getApplication<Application>().cacheDir.resolve("image_cache")
                cacheDir.deleteRecursively()
                cacheDir.mkdirs()

                // Rebuild global Coil loader with fresh caches
                val ctx = getApplication<Application>()
                val newLoader = com.furflix.app.ui.components.ImageLoaderFactory.getInstance(ctx)
                    .newBuilder()
                    .build()
                coil3.SingletonImageLoader.setUnsafe(newLoader)

                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, ctx.getString(R.string.toast_cache_cleared), Toast.LENGTH_SHORT).show()
                }
            } finally {
                _isClearingCache.value = false
                refreshCacheSize()
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun setDownloadFolder(folder: String) {
        viewModelScope.launch {
            repository.saveDownloadFolder(folder)
        }
    }
}
