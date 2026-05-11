package com.example.einthusan.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.einthusan.data.EinthusanRepository
import com.example.einthusan.data.MovieDetails
import com.example.einthusan.data.TokenScraper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EinthusanRepository()
    private val tokenScraper = TokenScraper(application)

    private val _details = MutableStateFlow<MovieDetails?>(null)
    val details = _details.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _isScraping = MutableStateFlow(false)
    val isScraping = _isScraping.asStateFlow()

    private val _navigateToStream = MutableStateFlow<String?>(null)
    val navigateToStream = _navigateToStream.asStateFlow()

    // Track the scraping job to allow cancellation
    private var scrapingJob: Job? = null

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

    fun getStreamAndPlay(videoPageUrl: String) {
        if (_isScraping.value) return

        // Launch job and save reference
        scrapingJob = viewModelScope.launch {
            _isScraping.value = true
            try {
                val streamUrl = tokenScraper.scrapeStreamUrl(videoPageUrl)
                _navigateToStream.value = streamUrl
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScraping.value = false
            }
        }
    }

    fun cancelScraping() {
        if (_isScraping.value) {
            scrapingJob?.cancel()
            _isScraping.value = false
        }
    }

    fun onNavigationConsumed() {
        _navigateToStream.value = null
    }
}