package com.furflix.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.furflix.app.R
import com.furflix.app.data.model.Submission
import com.furflix.app.data.repository.FurRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TagSearchViewModel(
    application: Application,
    initialQuery: String
) : AndroidViewModel(application) {

    private val repository = FurRepository.getInstance(application)

    private val _submissions = MutableStateFlow<List<Submission>>(emptyList())
    val submissions: StateFlow<List<Submission>> = _submissions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _searchFilters = MutableStateFlow(com.furflix.app.data.model.SearchFilters())
    val searchFilters: StateFlow<com.furflix.app.data.model.SearchFilters> = _searchFilters.asStateFlow()

    private val _searchQuery = MutableStateFlow(initialQuery)
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _tagSuggestions = MutableStateFlow<List<String>>(emptyList())
    val tagSuggestions: StateFlow<List<String>> = _tagSuggestions.asStateFlow()

    private var tagSearchJob: kotlinx.coroutines.Job? = null

    // ── 搜索请求生命周期：唯一 Job + 递增代次号，旧请求取消、过期回包丢弃 ──
    private var searchJob: kotlinx.coroutines.Job? = null
    private var loadMoreJob: kotlinx.coroutines.Job? = null
    private var searchGeneration = 0

    private var currentPage = 1
    private var hasMore = true

    init {
        loadSearch()
    }

    fun loadSearch(newQuery: String = _searchQuery.value, filters: com.furflix.app.data.model.SearchFilters? = null) {
        if (filters != null) _searchFilters.value = filters
        if (newQuery != _searchQuery.value) _searchQuery.value = newQuery
        val trimmed = newQuery.trim()
        if (trimmed.isNotEmpty()) {
            viewModelScope.launch {
                repository.addRecentSearch(trimmed)
            }
        }
        currentPage = 1
        hasMore = true

        searchJob?.cancel()
        loadMoreJob?.cancel()
        val gen = ++searchGeneration
        searchJob = viewModelScope.launch {
            _submissions.value = emptyList()
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = repository.search(newQuery, currentPage, _searchFilters.value)
                if (gen == searchGeneration) {
                    _submissions.value = result
                    if (result.isEmpty()) {
                        _errorMessage.value = getApplication<Application>().getString(R.string.empty_search_results)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("TagSearchVM", "loadSearch error", e)
                if (gen == searchGeneration) {
                    _errorMessage.value = getApplication<Application>().getString(R.string.error_generic, e.message ?: "")
                }
            }
            if (gen == searchGeneration) _isLoading.value = false
        }
    }

    /** 下拉刷新：重放当前关键词与筛选（分页从第 1 页重来）。 */
    fun refresh() {
        loadSearch(_searchQuery.value)
    }

    /** 筛选变更触发的重搜：防抖合并连续调整。 */
    fun updateSearchFilters(filters: com.furflix.app.data.model.SearchFilters) {
        _searchFilters.value = filters
        searchJob?.cancel()
        loadMoreJob?.cancel()
        val gen = ++searchGeneration
        searchJob = viewModelScope.launch {
            delay(SEARCH_RELOAD_DEBOUNCE_MS)
            currentPage = 1
            hasMore = true
            _submissions.value = emptyList()
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = repository.search(_searchQuery.value, 1, filters)
                if (gen == searchGeneration) {
                    _submissions.value = result
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("TagSearchVM", "filter reload error", e)
            }
            if (gen == searchGeneration) _isLoading.value = false
        }
    }

    fun loadMore() {
        if (!hasMore || _isLoadingMore.value || _isLoading.value) return
        if (searchJob?.isActive == true) return
        loadMoreJob?.cancel()
        val gen = searchGeneration
        val filters = _searchFilters.value
        val pageSize = filters.perpage
        loadMoreJob = viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                currentPage++
                val result = repository.search(_searchQuery.value, currentPage, filters)
                if (gen != searchGeneration) {
                    currentPage--
                    return@launch
                }
                if (result.isEmpty()) {
                    hasMore = false
                    currentPage--
                } else {
                    val currentIds = _submissions.value.map { it.id }.toSet()
                    val newItems = result.filter { it.id !in currentIds }
                    if (newItems.isEmpty()) {
                        hasMore = false
                        currentPage--
                    } else {
                        _submissions.value = _submissions.value + newItems
                        hasMore = result.size >= pageSize
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("TagSearchVM", "loadMore error", e)
                currentPage--
            }
            _isLoadingMore.value = false
        }
    }

    fun fetchTagSuggestions(prefix: String) {
        tagSearchJob?.cancel()
        if (prefix.isBlank()) {
            _tagSuggestions.value = emptyList()
            return
        }
        tagSearchJob = viewModelScope.launch {
            delay(300) // Debounce 300ms
            _tagSuggestions.value = repository.searchTags(prefix)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    companion object {
        private const val SEARCH_RELOAD_DEBOUNCE_MS = 450L
    }

    class Factory(
        private val application: Application,
        private val query: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TagSearchViewModel::class.java)) {
                return TagSearchViewModel(application, query) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
