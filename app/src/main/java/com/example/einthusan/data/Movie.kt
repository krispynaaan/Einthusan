package com.example.einthusan.data

data class Movie(
    val title: String,
    val imageUrl: String,
    val videoPageUrl: String,
    val synopsis: String,
    val year: String = "",
    val rating: String = "",
    val backdropUrl: String = imageUrl,
    val languages: List<String> = emptyList(),
    val videoUrls: Map<String, String> = emptyMap(),
    // NEW: Added genres field
    val genres: List<String> = emptyList()
)

data class MovieDetails(
    val title: String,
    val coverUrl: String,
    val backdropUrl: String,
    val synopsis: String,
    val year: String,
    val rating: String,
    val genres: List<String>,
    val cast: List<CastMember>,
    val languages: List<String>,
    val videoUrls: Map<String, String>
)

data class CastMember(
    val name: String,
    val imageUrl: String,
    val role: String
)