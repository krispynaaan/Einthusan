package com.example.einthusan.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.einthusan.data.EinthusanRepository
import com.example.einthusan.data.Movie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    private val repository = EinthusanRepository()

    private val _searchResults = MutableStateFlow<List<Movie>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // NEW: Error state to show on screen
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    fun search(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null // Clear previous errors

            Log.d("Einthusan", "Starting search for: $query")

            try {
                val results = repository.searchMovies(query)
                Log.d("Einthusan", "Search success. Found ${results.size} movies.")

                if (results.isEmpty()) {
                    _errorMessage.value = "No movies found. The scraper selectors might be outdated."
                } else {
                    _searchResults.value = results
                }
            } catch (e: Exception) {
                Log.e("Einthusan", "Search failed", e)
                _errorMessage.value = "Error: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}