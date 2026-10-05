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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FurRepository.getInstance(application)

    private val _browseSubmissions = MutableStateFlow<List<Submission>>(emptyList())
    val browseSubmissions: StateFlow<List<Submission>> = _browseSubmissions.asStateFlow()

    private val _latestSubmissions = MutableStateFlow<List<Submission>>(emptyList())
    val latestSubmissions: StateFlow<List<Submission>> = _latestSubmissions.asStateFlow()

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

    private val _quickPresets = MutableStateFlow<List<String>>(emptyList())
    val quickPresets: StateFlow<List<String>> = _quickPresets.asStateFlow()

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
     * visited 仅由真实到访（详情页加载）置 true，统计预热默认 false——浏览历史不收录预热条目。
     */
    private fun cardStatsOf(
        sub: Submission,
        favorited: Boolean = sub.isFavorited,
        favorites: Int = sub.favorites,
        visited: Boolean = false
    ): com.furflix.app.data.model.CardStats = com.furflix.app.data.model.CardStats(
        views = sub.views,
        favorites = favorites,
        comments = sub.comments,
        favorited = favorited,
        title = sub.title,
        author = sub.author,
        authorUsername = sub.authorUsername,
        thumbnailUrl = sub.thumbnailUrl,
        rating = sub.rating,
        ratio = sub.thumbnailWidthOnHeightRatio,
        visited = visited
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

    fun clearTagSuggestions() {
        searchTagsJob?.cancel()
        _tagSuggestions.value = emptyList()
    }

    private val _nsfwEnabled = MutableStateFlow(true)
    val nsfwEnabled: StateFlow<Boolean> = _nsfwEnabled.asStateFlow()

    private val _downloadFolder = MutableStateFlow("")
    val downloadFolder: StateFlow<String> = _downloadFolder.asStateFlow()

    fun updateSearchFilters(filters: com.furflix.app.data.model.SearchFilters) {
        _searchFilters.value = filters
        val hasKeywords = filters.keywords.isNotEmpty()
        if (_searchQuery.value.isNotEmpty() || hasKeywords || _selectedTab.value == 2) {
            loadSearch(_searchQuery.value)
        } else if (_selectedTab.value == 0) {
            }
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
        _searchFilters.value = com.furflix.app.data.model.SearchFilters()
    }

    fun clearRecentSearches() {
        viewModelScope.launch { repository.saveRecentSearches(emptyList()) }
    }

    fun clearCardStats() {
        viewModelScope.launch { cardStatsCache.clear() }
    }

    // 作者画廊等场景的可见块统计预热：静默抓详情并回填缓存，不触碰
    // isLoading/errorMessage，避免打断详情页的加载态
    private val statsPrefetchMutex = Mutex()
    private val statsPrefetchAttempted = mutableSetOf<String>()

    fun prefetchSubmissionStats(ids: List<String>) {
        val fresh = ids.filter { it.isNotEmpty() && statsPrefetchAttempted.add(it) }
        if (fresh.isEmpty()) return
        viewModelScope.launch {
            statsPrefetchMutex.withLock {
                for (id in fresh) {
                    if (_submissionDetails.value.containsKey(id)) continue
                    try {
                        val result = repository.getSubmission(id)
                        if (result != null) {
                            _submissionDetails.update { current ->
                                val newMap = current.toMutableMap()
                                newMap[id] = result
                                newMap
                            }
                            cardStatsCache.put(id, cardStatsOf(result))
                        }
                    } catch (_: Exception) {
                    }
                    delay(150)
                }
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

    /** 快捷预设上限：新的在前，去重后截断 */
    fun addQuickPreset(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.saveQuickPresets((listOf(trimmed) + _quickPresets.value).distinct().take(12))
        }
    }

    fun removeQuickPreset(query: String) {
        viewModelScope.launch {
            repository.saveQuickPresets(_quickPresets.value - query)
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

    companion object {
        const val TAB_BROWSE = 0
        const val TAB_LATEST = 1
        const val TAB_SEARCH = 2
        const val TAB_WATCHLIST = 3
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
            // Trigger load based on default tab
            when (defaultTab) {
                0 -> loadBrowse()
                1 -> loadLatest()
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
            repository.quickPresetsFlow.collect {
                _quickPresets.value = it
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

    fun loadBrowse() {
        browsePage = 1
        browseHasMore = true
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = repository.getBrowse(browsePage, _searchFilters.value)
                _browseSubmissions.value = result
                // Do not set errorMessage when empty — can happen briefly on startup
            } catch (e: Exception) {
                _errorMessage.value = getApplication<Application>().getString(
                    R.string.error_generic, e.message ?: ""
                )
            }
            _isLoading.value = false
        }
    }

    fun loadLatest() {
        latestPage = 1
        latestHasMore = true
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = repository.getLatest(latestPage)
                _latestSubmissions.value = result
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

    private val _searchKeywordCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** 分词计数：-1 = 抓取中，-2 = 失败；空 map = 不展示。 */
    val searchKeywordCounts: StateFlow<Map<String, Int>> = _searchKeywordCounts.asStateFlow()

    private fun loadKeywordCounts(query: String) {
        // 分词 = 输入框词 + 筛选面板关键词；`@keywords` 是组语法标记不是搜索词，
        // `!` 排除极性对计数无意义，按词本身计
        val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() && it != "@keywords" }
        val words = (tokens + _searchFilters.value.keywords)
            .map { it.removePrefix("!") }
            .filter { it.isNotBlank() }
            .distinct()
        if (words.size < 2 || words.size > 4) {
            _searchKeywordCounts.value = emptyMap()
            return
        }
        viewModelScope.launch {
            _searchKeywordCounts.value = words.associateWith { -1 }
            words.forEach { word ->
                launch {
                    try {
                        val result = repository.search(word, 1, _searchFilters.value)
                        _searchKeywordCounts.value = _searchKeywordCounts.value + (word to result.size)
                    } catch (_: Exception) {
                        _searchKeywordCounts.value = _searchKeywordCounts.value + (word to -2)
                    }
                }
            }
        }
    }

    fun loadSearch(query: String) {
        val currentFilters = _searchFilters.value
        if (query.isBlank() && currentFilters.keywords.isEmpty()) return
        _searchQuery.value = query
        _hasSearched.value = true
        searchPage = 1
        searchHasMore = true

        val trimmed = query.trim()
        val itemsToAdd = mutableListOf<String>()
        if (trimmed.isNotEmpty()) itemsToAdd.add(trimmed)
        itemsToAdd.addAll(currentFilters.keywords)

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

        viewModelScope.launch {
            _searchSubmissions.value = emptyList()
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = repository.search(query, searchPage, currentFilters)
                _searchSubmissions.value = result
                // An empty result is the empty state, not an error — the screen
                // renders its "no results" branch without an error banner.
            } catch (e: Exception) {
                _errorMessage.value = getApplication<Application>().getString(
                    R.string.error_generic, e.message ?: ""
                )
            }
            _isLoading.value = false
        }
        // 多词搜索时逐词并行抓取计数（首页抓取数口径，见 RFC §11/C9）
        loadKeywordCounts(query)
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
        viewModelScope.launch {
            try {
                val (page, result) = when (_selectedTab.value) {
                    TAB_BROWSE -> {
                        if (!browseHasMore) { _isLoadingMore.value = false; return@launch }
                        browsePage++
                        browsePage to repository.getBrowse(browsePage, _searchFilters.value)
                    }
                    TAB_LATEST -> {
                        if (!latestHasMore) { _isLoadingMore.value = false; return@launch }
                        latestPage++
                        latestPage to repository.getLatest(latestPage)
                    }
                    TAB_SEARCH -> {
                        if (!searchHasMore) { _isLoadingMore.value = false; return@launch }
                        searchPage++
                        searchPage to repository.search(_searchQuery.value, searchPage, _searchFilters.value)
                    }
                    else -> {
                        if (!browseHasMore) { _isLoadingMore.value = false; return@launch }
                        browsePage++
                        browsePage to repository.getBrowse(browsePage, _searchFilters.value)
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
                            if (unique.isEmpty()) browsePage-- else _browseSubmissions.value = _browseSubmissions.value + unique
                        }
                        TAB_LATEST -> {
                            val unique = result.filter { r -> _latestSubmissions.value.none { it.id == r.id } }
                            latestHasMore = unique.isNotEmpty()
                            if (unique.isEmpty()) latestPage-- else _latestSubmissions.value = _latestSubmissions.value + unique
                        }
                    TAB_SEARCH -> {
                        val unique = result.filter { r -> _searchSubmissions.value.none { it.id == r.id } }
                        // Full page = possibly more pages; a short page means the
                        // site has no more to give, so don't burn one more request
                        // just to learn that.
                        searchHasMore = result.size >= com.furflix.app.data.SEARCH_PAGE_SIZE && unique.isNotEmpty()
                        if (unique.isEmpty()) searchPage-- else _searchSubmissions.value = _searchSubmissions.value + unique
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
     * 下载一张原图：仅入队 DownloadManager（进度/速度/状态由任务流承载），
     * 扩展名从 URL/响应推断，不再假设 .jpg。
     */
    fun downloadImage(sub: Submission) {
        DownloadManager.enqueue(
            context = getApplication(),
            submissionId = sub.id,
            url = sub.fullImageUrl,
            author = sub.author,
            title = sub.title
        )
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _isLoggedIn.value = false
            _username.value = ""
            _browseSubmissions.value = emptyList()
            _latestSubmissions.value = emptyList()
            _searchSubmissions.value = emptyList()
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
