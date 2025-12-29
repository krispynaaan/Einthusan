package com.example.einthusan.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.einthusan.data.EinthusanRepository
import com.example.einthusan.data.Movie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val repository = EinthusanRepository()

    private val _homeCategories = MutableStateFlow<Map<String, List<Movie>>>(emptyMap())
    val homeCategories = _homeCategories.asStateFlow()

    private val _featuredMovies = MutableStateFlow<List<Movie>>(emptyList())
    val featuredMovies = _featuredMovies.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    // NEW: Error State
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    init {
        loadHomeContent()
    }

    fun loadHomeContent() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val data = repository.fetchHomeData()
                _featuredMovies.value = data.featuredMovies
                _homeCategories.value = data.categories
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Failed to load: ${e.message}\nCheck your internet connection."
            } finally {
                _isLoading.value = false
            }
        }
    }
}