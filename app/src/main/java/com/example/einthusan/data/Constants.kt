package com.example.einthusan.data

object Constants {
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    const val BASE_URL = "https://einthusan.tv"
    const val SEARCH_BASE_URL = "https://einthusan.tv/movie/results/?lang=tamil&query="
    
    // Increased timeouts for slower networks
    const val TIMEOUT = 30_000 // 30 seconds

    // --- TMDB CONFIGURATION ---
    const val TMDB_API_KEY = "3cb45b83863284d1fbf883f628852cd8"
    const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"
    const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/original" 
}