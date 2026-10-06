package com.furflix.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.furflix.app.R
import com.furflix.app.data.model.Journal
import com.furflix.app.data.repository.FurRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class JournalDetailViewModel(
    application: Application,
    private val journalId: String
) : AndroidViewModel(application) {

    private val repository = FurRepository.getInstance(application)

    private val _detail = MutableStateFlow<Journal?>(null)
    val detail: StateFlow<Journal?> = _detail.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = repository.getJournal(journalId)
                _detail.value = result
                if (result == null) {
                    _errorMessage.value = getApplication<Application>().getString(R.string.error_generic, "empty response")
                }
            } catch (e: Exception) {
                Log.e("JournalVM", "load failed", e)
                _errorMessage.value = getApplication<Application>().getString(R.string.error_generic, e.message ?: "")
            }
            _isLoading.value = false
        }
    }

    class Factory(
        private val application: Application,
        private val journalId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return JournalDetailViewModel(application, journalId) as T
        }
    }
}
