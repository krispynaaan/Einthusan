package com.example.einthusan.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.einthusan.data.TokenScraper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenScraper = TokenScraper(application)

    private val _streamUrl = MutableStateFlow<String?>(null)
    val streamUrl = _streamUrl.asStateFlow()

    private val _status = MutableStateFlow("Initializing...")
    val status = _status.asStateFlow()

    fun loadVideo(videoPageUrl: String) {
        viewModelScope.launch {
            _status.value = "Hunting for token..."
            try {
                // 1. Launch invisible WebView to intercept token
                val finalUrl = tokenScraper.scrapeStreamUrl(videoPageUrl)
                _status.value = "Token acquired. Buffering..."
                _streamUrl.value = finalUrl
            } catch (e: Exception) {
                _status.value = "Error: ${e.message}"
            }
        }
    }
}