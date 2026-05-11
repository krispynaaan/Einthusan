package com.example.einthusan.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.einthusan.data.CastMember
import com.example.einthusan.data.MovieDetails
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailsScreen(
    videoUrl: String,
    onPlayClick: (String) -> Unit,
    viewModel: DetailsViewModel = viewModel()
) {
    val details by viewModel.details.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val isScraping by viewModel.isScraping.collectAsState()
    val navigateToStream by viewModel.navigateToStream.collectAsState()

    val playButtonRequester = remember { FocusRequester() }

    // 1. BACK HANDLER: Cancels loading if active
    BackHandler(enabled = isScraping) {
        // This is a "hack" to cancel scraping via the ViewModel:
        // Ideally, you'd expose a cancel function, but resetting the flag works visually
        // and calling a new load effectively cancels the old flow in simple cases.
        // For a robust cancel, we rely on the user navigating away or re-clicking.
        // But specifically for this UI requirement: "Back should cancel loading":
        viewModel.cancelScraping()
    }

    LaunchedEffect(videoUrl) {
        viewModel.loadDetails(videoUrl)
    }

    LaunchedEffect(navigateToStream) {
        navigateToStream?.let { streamUrl ->
            onPlayClick(streamUrl)
            viewModel.onNavigationConsumed()
        }
    }

    LaunchedEffect(details) {
        if (details != null) {
            delay(300)
            try { playButtonRequester.requestFocus() } catch (e: Exception) { e.printStackTrace() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // 2. KEY LOCK: Swallow navigation keys if loading
            .onPreviewKeyEvent { event ->
                if (isScraping && event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.Enter, Key.NumPadEnter -> {
                            true // Consume event (do nothing)
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        if (isLoading || details == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.Red)
        } else {
            val movie = details!!

            AsyncImage(
                model = movie.backdropUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(0.4f),
                contentScale = ContentScale.Crop
            )

            Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(colors = listOf(Color.Black, Color.Black.copy(alpha = 0.8f), Color.Transparent), endX = 1500f)))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 50.dp, top = 24.dp, bottom = 10.dp)
            ) {
                MovieInfoSection(
                    movie = movie,
                    onPlayClick = { url -> viewModel.getStreamAndPlay(url) },
                    focusRequester = playButtonRequester,
                    isPlayLoading = isScraping
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Cast", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

                CastRow(cast = movie.cast)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieInfoSection(
    movie: MovieDetails,
    onPlayClick: (String) -> Unit,
    focusRequester: FocusRequester,
    isPlayLoading: Boolean
) {
    Column(modifier = Modifier.width(400.dp)) {
        Text(text = movie.title, style = MaterialTheme.typography.displayMedium, color = Color.White, fontWeight = FontWeight.ExtraBold, maxLines = 2)

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = movie.year, color = Color.Green, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(Color.DarkGray, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Icon(Icons.Default.Star, null, tint = Color.Yellow, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = movie.rating.replace("★", "").trim(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = movie.languages.joinToString(", "), color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(10.dp))
        if (movie.genres.isNotEmpty()) {
            Text(text = movie.genres.joinToString(" | "), color = Color(0xFFCCCCCC), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(10.dp))
        }

        Text(text = movie.synopsis, color = Color.LightGray, style = MaterialTheme.typography.bodyLarge, maxLines = 6, overflow = TextOverflow.Ellipsis)

        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val sortedLangs = movie.videoUrls.keys.sortedBy {
                when(it.lowercase()) { "tamil"->0; "telugu"->1; "hindi"->2; else->9 }
            }

            sortedLangs.forEachIndexed { index, lang ->
                val url = movie.videoUrls[lang] ?: return@forEachIndexed

                var isFocused by remember { mutableStateOf(false) }
                val scale by animateFloatAsState(if (isFocused) 1.15f else 1f, label = "scale")

                val baseColor = when(lang.lowercase()) {
                    "tamil" -> Color(0xFF1976D2)
                    "telugu" -> Color(0xFFC62828)
                    "hindi" -> Color(0xFFEF6C00)
                    else -> Color(0xFF455A64)
                }

                val containerColor = if (isFocused) Color.White else baseColor
                val contentColor = if (isFocused) Color.Black else Color.White

                Button(
                    onClick = { if (!isPlayLoading) onPlayClick(url) },
                    modifier = (if (index == 0) Modifier.focusRequester(focusRequester) else Modifier)
                        .onFocusChanged { isFocused = it.isFocused }
                        .scale(scale),
                    colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
                    enabled = true
                ) {
                    if (isPlayLoading && isFocused) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Loading...", color = Color.Black)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Play $lang")
                    }
                }
            }
        }
    }
}

@Composable
fun CastRow(cast: List<CastMember>) {
    if (cast.isEmpty()) { Text("No cast information available", color = Color.Gray); return }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 10.dp, end = 50.dp)
    ) {
        items(cast) { member -> CastMemberCard(member) }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CastMemberCard(member: CastMember) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1f, label = "scale")
    val borderColor = if (isFocused) Color.White else Color.Transparent

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(100.dp).scale(scale).onFocusChanged { isFocused = it.isFocused }.clickable(onClick = {})) {
        Box(
            modifier = Modifier
                .requiredSize(80.dp)
                .border(2.dp, borderColor, CircleShape)
                .clip(CircleShape)
                .background(Color.DarkGray)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(member.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = member.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = member.name, color = if (isFocused) Color.White else Color.LightGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(text = member.role, color = Color.Gray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}