package com.example.einthusan.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.einthusan.data.EinthusanRepository
import com.example.einthusan.data.MovieDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailsViewModel : ViewModel() {
    private val repository = EinthusanRepository()

    private val _details = MutableStateFlow<MovieDetails?>(null)
    val details = _details.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    fun loadDetails(videoUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val data = repository.getMovieDetails(videoUrl)
                _details.value = data
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}