package com.furflix.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.furflix.app.R
import com.furflix.app.data.model.GalleryFolder
import com.furflix.app.data.model.Submission
import com.furflix.app.data.model.UserProfile
import com.furflix.app.data.model.WatchlistUser
import com.furflix.app.data.repository.FurRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserViewModel(
    application: Application,
    private val username: String
) : AndroidViewModel(application) {

    private val repository = FurRepository.getInstance(application)

    private val _submissions = MutableStateFlow<List<Submission>>(emptyList())
    val submissions: StateFlow<List<Submission>> = _submissions.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isTogglingWatch = MutableStateFlow(false)
    val isTogglingWatch: StateFlow<Boolean> = _isTogglingWatch.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _watchingList = MutableStateFlow<List<WatchlistUser>>(emptyList())
    val watchingList: StateFlow<List<WatchlistUser>> = _watchingList.asStateFlow()

    private val _watchersList = MutableStateFlow<List<WatchlistUser>>(emptyList())
    val watchersList: StateFlow<List<WatchlistUser>> = _watchersList.asStateFlow()

    private val _watchingHasMore = MutableStateFlow(false)
    val watchingHasMore: StateFlow<Boolean> = _watchingHasMore.asStateFlow()

    private val _watchersHasMore = MutableStateFlow(false)
    val watchersHasMore: StateFlow<Boolean> = _watchersHasMore.asStateFlow()

    private val _journals = MutableStateFlow<List<com.furflix.app.data.model.Journal>>(emptyList())
    val journals: StateFlow<List<com.furflix.app.data.model.Journal>> = _journals.asStateFlow()
    private var journalsPage = 1
    private var journalsMore = true

    private var currentPage = 1
    private var watchingPage = 1
    private var watchersPage = 1
    private var watchingMore = true
    private var watchersMore = true
    private var contentMore = true

    private val _currentTab = MutableStateFlow(TAB_GALLERY)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    /** 画廊文件夹浏览模式：非空 = 正在该文件夹内浏览。 */
    private val _activeFolder = MutableStateFlow<GalleryFolder?>(null)
    val activeFolder: StateFlow<GalleryFolder?> = _activeFolder.asStateFlow()
    private var folderPage = 1
    private var folderMore = true

    companion object {
        const val TAB_GALLERY = 0
        const val TAB_FAVORITES = 1
        const val TAB_WATCHING = 2
        const val TAB_WATCHERS = 3
        const val TAB_JOURNALS = 4

        /** 画廊 tab 内的两个顶级分区，与 FA 侧栏 default-folders 对应（Main Gallery / Scraps）。 */
        const val GALLERY_SECTION_MAIN = 0
        const val GALLERY_SECTION_SCRAPS = 1
    }

    /** 画廊 tab 当前分区：主画廊或 Scraps。文件夹浏览只存在于主画廊分区。 */
    private val _gallerySection = MutableStateFlow(GALLERY_SECTION_MAIN)
    val gallerySection: StateFlow<Int> = _gallerySection.asStateFlow()

    private var _lastNsfwEnabled: Boolean? = null

    init {
        loadProfile()
        viewModelScope.launch {
            repository.nsfwEnabledFlow.collect { nsfw ->
                if (_lastNsfwEnabled != null && _lastNsfwEnabled != nsfw) {
                    _submissions.value = emptyList()
                    if (_currentTab.value == TAB_GALLERY) {
                        if (_activeFolder.value != null) {
                            // 文件夹浏览态下切 NSFW:留在原文件夹重载,而不是回落分区列表
                            folderPage = 1
                            folderMore = true
                            loadFolderContent()
                        } else {
                            loadGallery()
                        }
                    } else if (_currentTab.value == TAB_FAVORITES) {
                        loadFavorites()
                    }
                } else if (_lastNsfwEnabled == null) {
                    loadGallery()
                }
                _lastNsfwEnabled = nsfw
            }
        }
    }

    private fun loadProfile() {
        viewModelScope.launch { loadProfileInternal() }
    }

    private suspend fun loadProfileInternal() {
        try {
            val profile = repository.getUserProfile(username)
            _userProfile.value = profile
            // 用户页真实到访才记入浏览历史的“用户”栏；自己的主页不计
            if (profile.username.isNotBlank() &&
                !profile.username.equals(repository.getUsername(), ignoreCase = true)
            ) {
                com.furflix.app.data.local.UserVisitCache.getInstance(getApplication())
                    .put(profile.username, profile.displayName, profile.avatarUrl)
            }
            // Gallery Folders：现代主题用户页不含文件夹列表，从 /gallery/ 页解析补取
            // （画廊 tab 加载过 page 1 时命中解析器缓存，零额外请求）
            if (profile.folders.isEmpty()) {
                try {
                    val folders = repository.getUserGalleryFolders(username)
                    if (folders.isNotEmpty()) {
                        _userProfile.value = profile.copy(folders = folders)
                    }
                } catch (e: Exception) {
                    Log.e("UserVM", "loadFolders failed", e)
                }
            }
        } catch (e: Exception) {
            Log.e("UserVM", "loadProfile failed", e)
        }
    }

    fun toggleWatch() {
        if (_isTogglingWatch.value) return
        val profile = _userProfile.value ?: return
        if (profile.watchUrl.isEmpty()) return

        _isTogglingWatch.value = true
        val isCurrentlyWatching = profile.isWatching
        val actionUrl = profile.watchUrl

        _userProfile.value = profile.copy(
            isWatching = !isCurrentlyWatching,
            watchUrl = actionUrl.replace(
                if (isCurrentlyWatching) "/unwatch/" else "/watch/",
                if (isCurrentlyWatching) "/watch/" else "/unwatch/"
            )
        )

        viewModelScope.launch {
            try {
                val success = repository.toggleWatch(actionUrl)
                if (!success) {
                    _userProfile.value = profile
                    _errorMessage.value = getApplication<Application>().getString(R.string.error_watch_toggle_failed)
                } else {
                    loadProfileInternal()
                }
            } catch (e: Exception) {
                _userProfile.value = profile
                _errorMessage.value = getApplication<Application>().getString(R.string.error_generic, e.message ?: "")
            } finally {
                _isTogglingWatch.value = false
            }
        }
    }

    fun loadGallery(force: Boolean = false) {
        if (!force && _currentTab.value == TAB_GALLERY && _submissions.value.isNotEmpty()) return
        _currentTab.value = TAB_GALLERY
        currentPage = 1
        contentMore = true
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = if (_gallerySection.value == GALLERY_SECTION_SCRAPS) {
                    repository.getUserScraps(username, currentPage)
                } else {
                    repository.getUserGallery(username, currentPage)
                }
                _submissions.value = result
                if (result.isEmpty()) {
                    _errorMessage.value = if (_gallerySection.value == GALLERY_SECTION_SCRAPS) {
                        getApplication<Application>().getString(R.string.empty_scraps)
                    } else {
                        getApplication<Application>().getString(R.string.empty_gallery)
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = getApplication<Application>().getString(R.string.error_generic, e.message ?: "")
            }
            _isLoading.value = false
        }
    }

    /** 切换画廊 tab 的分区（主画廊/Scraps）：退出文件夹浏览、回到画廊 tab 并按新分区重载列表。 */
    fun selectGallerySection(section: Int) {
        if (_gallerySection.value == section && _activeFolder.value == null &&
            _currentTab.value == TAB_GALLERY
        ) return
        _gallerySection.value = section
        _activeFolder.value = null
        _submissions.value = emptyList()
        loadGallery(force = true)
    }

    fun loadFavorites(force: Boolean = false) {
        if (!force && _currentTab.value == TAB_FAVORITES && _submissions.value.isNotEmpty()) return
        _currentTab.value = TAB_FAVORITES
        currentPage = 1
        contentMore = true
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = repository.getUserFavorites(username, currentPage)
                _submissions.value = result
                if (result.isEmpty()) _errorMessage.value = getApplication<Application>().getString(R.string.profile_no_favorites)
            } catch (e: Exception) {
                _errorMessage.value = getApplication<Application>().getString(R.string.error_generic, e.message ?: "")
            }
            _isLoading.value = false
        }
    }

    fun loadWatching(force: Boolean = false) {
        if (!force && _currentTab.value == TAB_WATCHING && _watchingList.value.isNotEmpty()) return
        _currentTab.value = TAB_WATCHING
        watchingPage = 1
        watchingMore = true
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val (users, hasNext) = repository.getWatchlistBy(username, watchingPage)
                _watchingList.value = users
                watchingMore = hasNext
                _watchingHasMore.value = hasNext
                if (users.isEmpty()) _errorMessage.value = getApplication<Application>().getString(R.string.empty_watching)
            } catch (e: Exception) {
                _errorMessage.value = getApplication<Application>().getString(R.string.error_generic, e.message ?: "")
            }
            _isLoading.value = false
        }
    }

    fun loadWatchers(force: Boolean = false) {
        if (!force && _currentTab.value == TAB_WATCHERS && _watchersList.value.isNotEmpty()) return
        _currentTab.value = TAB_WATCHERS
        watchersPage = 1
        watchersMore = true
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val (users, hasNext) = repository.getWatchlistTo(username, watchersPage)
                _watchersList.value = users
                watchersMore = hasNext
                _watchersHasMore.value = hasNext
                if (users.isEmpty()) _errorMessage.value = getApplication<Application>().getString(R.string.empty_watchers)
            } catch (e: Exception) {
                _errorMessage.value = getApplication<Application>().getString(R.string.error_generic, e.message ?: "")
            }
            _isLoading.value = false
        }
    }

    fun loadJournals(force: Boolean = false) {
        if (!force && _currentTab.value == TAB_JOURNALS && _journals.value.isNotEmpty()) return
        _currentTab.value = TAB_JOURNALS
        journalsPage = 1
        journalsMore = true
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = repository.getUserJournals(username, journalsPage)
                _journals.value = result
                if (result.isEmpty()) _errorMessage.value = getApplication<Application>().getString(R.string.empty_journals)
            } catch (e: Exception) {
                _errorMessage.value = getApplication<Application>().getString(R.string.error_generic, e.message ?: "")
            }
            _isLoading.value = false
        }
    }

    fun openFolder(folder: GalleryFolder) {
        _activeFolder.value = folder
        _submissions.value = emptyList()
        // 文件夹只挂主画廊：从 Scraps 分区点文件夹时切回主画廊（FA 行为一致）
        _gallerySection.value = GALLERY_SECTION_MAIN
        _currentTab.value = TAB_GALLERY
        folderPage = 1
        folderMore = true
        loadFolderContent()
    }

    fun closeFolder() {
        _activeFolder.value = null
        _submissions.value = emptyList()
        loadGallery(force = true)
    }

    private fun loadFolderContent() {
        val folder = _activeFolder.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = repository.getUserGalleryFolder(username, folder, folderPage)
                _submissions.value = result
                if (result.isEmpty()) _errorMessage.value = getApplication<Application>().getString(R.string.empty_folder)
            } catch (e: Exception) {
                _errorMessage.value = getApplication<Application>().getString(R.string.error_generic, e.message ?: "")
            }
            _isLoading.value = false
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                when (_currentTab.value) {
                    TAB_GALLERY -> {
                        val folder = _activeFolder.value
                        if (folder != null) {
                            if (!folderMore) { _isLoadingMore.value = false; return@launch }
                            folderPage++
                            val more = repository.getUserGalleryFolder(username, folder, folderPage)
                            val uniqueMore = more.filter { newSub -> _submissions.value.none { it.id == newSub.id } }
                            folderMore = uniqueMore.isNotEmpty()
                            if (uniqueMore.isEmpty()) folderPage--
                            else _submissions.value = _submissions.value + uniqueMore
                            return@launch
                        }
                        if (!contentMore) { _isLoadingMore.value = false; return@launch }
                        currentPage++
                        val more = if (_gallerySection.value == GALLERY_SECTION_SCRAPS) {
                            repository.getUserScraps(username, currentPage)
                        } else {
                            repository.getUserGallery(username, currentPage)
                        }
                        val uniqueMore = more.filter { newSub -> _submissions.value.none { it.id == newSub.id } }
                        contentMore = uniqueMore.isNotEmpty()
                        if (uniqueMore.isEmpty()) currentPage--
                        else _submissions.value = _submissions.value + uniqueMore
                    }
                    TAB_FAVORITES -> {
                        if (!contentMore) { _isLoadingMore.value = false; return@launch }
                        currentPage++
                        val more = repository.getUserFavorites(username, currentPage)
                        val uniqueMore = more.filter { newSub -> _submissions.value.none { it.id == newSub.id } }
                        contentMore = uniqueMore.isNotEmpty()
                        if (uniqueMore.isEmpty()) currentPage--
                        else _submissions.value = _submissions.value + uniqueMore
                    }
                    TAB_WATCHING -> {
                        if (!watchingMore) { _isLoadingMore.value = false; return@launch }
                        watchingPage++
                        val (more, hasNext) = repository.getWatchlistBy(username, watchingPage)
                        watchingMore = hasNext
                        _watchingHasMore.value = hasNext
                        if (more.isNotEmpty()) {
                            _watchingList.value = (_watchingList.value + more)
                                .distinctBy { it.username.lowercase() }
                        } else watchingPage--
                    }
                    TAB_WATCHERS -> {
                        if (!watchersMore) { _isLoadingMore.value = false; return@launch }
                        watchersPage++
                        val (more, hasNext) = repository.getWatchlistTo(username, watchersPage)
                        watchersMore = hasNext
                        _watchersHasMore.value = hasNext
                        if (more.isNotEmpty()) {
                            _watchersList.value = (_watchersList.value + more)
                                .distinctBy { it.username.lowercase() }
                        } else watchersPage--
                    }
                    TAB_JOURNALS -> {
                        if (!journalsMore) { _isLoadingMore.value = false; return@launch }
                        journalsPage++
                        val more = repository.getUserJournals(username, journalsPage)
                        val uniqueMore = more.filter { newJournal -> _journals.value.none { it.id == newJournal.id } }
                        journalsMore = uniqueMore.isNotEmpty()
                        if (uniqueMore.isEmpty()) journalsPage--
                        else _journals.value = _journals.value + uniqueMore
                    }
                }
            } catch (e: Exception) {
                when (_currentTab.value) {
                    TAB_GALLERY, TAB_FAVORITES -> currentPage--
                    TAB_WATCHING -> watchingPage--
                    TAB_WATCHERS -> watchersPage--
                    TAB_JOURNALS -> journalsPage--
                }
            }
            _isLoadingMore.value = false
        }
    }

    /**
     * 下拉刷新：清分页缓存后重载当前 tab 与个人资料。
     * force 绕过"已有数据即 return"守卫；列表先清空让刷新态可见。
     */
    fun refresh() {
        val folder = _activeFolder.value
        if (folder != null) {
            repository.clearUserPagination(username)
            _submissions.value = emptyList()
            folderPage = 1
            folderMore = true
            loadFolderContent()
            return
        }
        viewModelScope.launch {
            repository.clearUserPagination(username)
            loadProfileInternal()
        }
        when (_currentTab.value) {
            TAB_GALLERY -> { _submissions.value = emptyList(); loadGallery(force = true) }
            TAB_FAVORITES -> { _submissions.value = emptyList(); loadFavorites(force = true) }
            TAB_WATCHING -> { _watchingList.value = emptyList(); loadWatching(force = true) }
            TAB_WATCHERS -> { _watchersList.value = emptyList(); loadWatchers(force = true) }
            TAB_JOURNALS -> { _journals.value = emptyList(); loadJournals(force = true) }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    class Factory(
        private val application: Application,
        private val username: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return UserViewModel(application, username) as T
        }
    }
}
