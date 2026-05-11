package com.example.einthusan.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    // No longer needs scraper
    private val _streamUrl = MutableStateFlow<String?>(null)
    val streamUrl = _streamUrl.asStateFlow()

    private val _status = MutableStateFlow("Initializing Player...")
    val status = _status.asStateFlow()

    fun playStream(url: String) {
        _status.value = "Bufferring..."
        _streamUrl.value = url
    }
}