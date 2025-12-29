package com.example.einthusan.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.example.einthusan.data.Movie
import com.example.einthusan.ui.VirtualKeyboard
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    onMovieClick: (String) -> Unit,
    viewModel: SearchViewModel = viewModel()
) {
    val movies by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var queryText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun updateSearch(newText: String) {
        queryText = newText
        searchJob?.cancel()
        if (newText.isNotEmpty()) {
            searchJob = coroutineScope.launch {
                delay(1000)
                viewModel.search(newText)
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp)
    ) {
        // --- LEFT PANE: KEYBOARD ---
        Column(
            modifier = Modifier
                .width(320.dp)
                .fillMaxHeight()
                .padding(end = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(Color.DarkGray.copy(alpha = 0.3f), MaterialTheme.shapes.small)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (queryText.isEmpty()) {
                    Text("Search...", color = Color.Gray, fontSize = 18.sp)
                } else {
                    Text(queryText, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            VirtualKeyboard(
                onKeyPress = { char -> updateSearch(queryText + char) },
                onBackspace = { if (queryText.isNotEmpty()) updateSearch(queryText.dropLast(1)) },
                onSpace = { updateSearch("$queryText ") },
                onClear = { updateSearch("") }
            )
        }

        // --- RIGHT PANE: RESULTS ---
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.Red, modifier = Modifier.align(Alignment.Center))
            } else if (errorMessage != null) {
                Text(errorMessage!!, color = Color.Red, modifier = Modifier.align(Alignment.Center))
            } else if (movies.isEmpty() && queryText.isNotEmpty()) {
                Text("No results found.", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(movies) { movie ->
                        MovieCard(movie = movie, onClick = {
                            // --- LOGGING THE CLICK HERE ---
                            Log.d("EinthusanUI", "CLICKED: ${movie.title} | URL: ${movie.videoPageUrl}")
                            try {
                                val encodedUrl = URLEncoder.encode(movie.videoPageUrl, StandardCharsets.UTF_8.toString())
                                onMovieClick(encodedUrl)
                            } catch (e: Exception) {
                                Log.e("EinthusanUI", "Error encoding URL", e)
                            }
                        })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieCard(movie: Movie, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    CompactCard(
        onClick = onClick,
        modifier = Modifier
            .width(150.dp)
            .aspectRatio(2f/3f)
            .onFocusChanged { isFocused = it.isFocused },
        image = {
            AsyncImage(
                model = movie.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        },
        title = {
            if (isFocused) {
                Text(
                    text = movie.title,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp),
                    fontSize = 12.sp
                )
            }
        }
    )
}