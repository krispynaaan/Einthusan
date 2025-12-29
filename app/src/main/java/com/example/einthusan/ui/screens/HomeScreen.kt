package com.example.einthusan.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Carousel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil.compose.AsyncImage
import com.example.einthusan.data.Movie
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onMovieClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val categories by viewModel.homeCategories.collectAsState()
    val featuredMovies by viewModel.featuredMovies.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val carouselHeight = screenHeight * 0.70f

    val searchFocusRequester = remember { FocusRequester() }
    val carouselFocusRequester = remember { FocusRequester() }

    // --- SMOOTH SCROLL FIX ---
    val stableFocusSpec = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float, size: Float, containerSize: Float
            ): Float {
                // 1. If the item is already fully visible, DO NOT SCROLL.
                if (offset >= 0 && offset + size <= containerSize) {
                    return 0f
                }

                // 2. If item is above the screen (scrolling UP), scroll just enough to show the top.
                // Also handles items that are larger than the entire screen (align top).
                if (offset < 0 || size > containerSize) {
                    return offset
                }

                // 3. If item is below the screen (scrolling DOWN), scroll just enough to show the bottom.
                // PREVIOUS BUG: We were returning 'offset' here, which forced a "Jump to Top".
                // changing it to 'offset + size - containerSize' aligns it to the bottom.
                return offset + size - containerSize
            }
        }
    }

    LaunchedEffect(isLoading, featuredMovies) {
        if (!isLoading && featuredMovies.isNotEmpty()) {
            delay(500)
            try { carouselFocusRequester.requestFocus() } catch (e: Exception) { }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        if (errorMessage != null) {
            ErrorState(errorMessage!!, onRetry = { viewModel.loadHomeContent() })
        } else if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.Red
            )
        } else {
            // Apply the custom smooth scroll behavior
            CompositionLocalProvider(LocalBringIntoViewSpec provides stableFocusSpec) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    // 1. FEATURED CAROUSEL
                    if (featuredMovies.isNotEmpty()) {
                        item {
                            FeaturedCarousel(
                                movies = featuredMovies,
                                height = carouselHeight,
                                onMovieClick = onMovieClick,
                                searchRequester = searchFocusRequester,
                                carouselRequester = carouselFocusRequester
                            )
                        }
                    }

                    // 2. CATEGORIES
                    items(categories.toList()) { (categoryName, movies) ->
                        ContentRow(
                            title = categoryName,
                            movies = movies,
                            onMovieClick = onMovieClick
                        )
                    }
                }
            }
        }

        // SEARCH OVERLAY
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 30.dp, end = 50.dp)
        ) {
            SearchButton(
                onClick = onSearchClick,
                myRequester = searchFocusRequester,
                downTarget = carouselFocusRequester
            )
        }
    }
}

// ... (Rest of the file remains exactly the same: SearchButton, FeaturedCarousel, etc.)
@Composable
fun SearchButton(
    onClick: () -> Unit,
    myRequester: FocusRequester,
    downTarget: FocusRequester
) {
    var isFocused by remember { mutableStateOf(false) }

    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) Color.White else Color.Black.copy(alpha = 0.4f),
            contentColor = if (isFocused) Color.Black else Color.White
        ),
        modifier = Modifier
            .focusRequester(myRequester)
            .onPreviewKeyEvent {
                if (it.key == Key.DirectionDown && it.type == KeyEventType.KeyDown) {
                    downTarget.requestFocus()
                    true
                } else false
            }
            .onFocusChanged { isFocused = it.isFocused }
            .size(48.dp)
            .border(1.dp, if(isFocused) Color.Transparent else Color.White.copy(alpha = 0.5f), CircleShape),
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = "Search",
            modifier = Modifier.size(24.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FeaturedCarousel(
    movies: List<Movie>,
    height: androidx.compose.ui.unit.Dp,
    onMovieClick: (String) -> Unit,
    searchRequester: FocusRequester,
    carouselRequester: FocusRequester
) {
    Carousel(
        itemCount = movies.size,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .focusRequester(carouselRequester)
            .onPreviewKeyEvent {
                if (it.key == Key.DirectionUp && it.type == KeyEventType.KeyDown) {
                    searchRequester.requestFocus()
                    true
                } else false
            },
        autoScrollDurationMillis = 7000,
    ) { index ->
        val movie = movies[index]

        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = movie.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f), Color.Black), startY = 200f)))
            Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(colors = listOf(Color.Black.copy(alpha = 0.9f), Color.Transparent), endX = 1200f)))

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 50.dp, bottom = 40.dp)
                    .fillMaxWidth(0.6f)
            ) {
                Text(movie.title, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (movie.year.isNotEmpty()) {
                        Badge(text = movie.year, color = Color(0xFF607D8B))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    movie.languages.forEach { lang ->
                        val color = when(lang.lowercase()) {
                            "tamil" -> Color(0xFF2196F3)
                            "telugu" -> Color(0xFFD32F2F)
                            "hindi" -> Color(0xFFFF9800)
                            else -> Color(0xFF9E9E9E)
                        }
                        Badge(text = lang, color = color)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (movie.rating.isNotEmpty() && movie.rating != "N/A") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.background(Color.DarkGray, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Star, null, tint = Color.Yellow, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = movie.rating.replace("★", "").trim(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(text = movie.synopsis, color = Color(0xFFDDDDDD), fontSize = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 24.sp)
                Spacer(modifier = Modifier.height(24.dp))

                Row {
                    NetflixButton(
                        text = "Play",
                        icon = Icons.Default.PlayArrow,
                        isPrimary = true,
                        onClick = { onMovieClick(URLEncoder.encode(movie.videoPageUrl, StandardCharsets.UTF_8.toString())) }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    NetflixButton(
                        text = "More Info",
                        icon = Icons.Default.Info,
                        isPrimary = false,
                        onClick = { onMovieClick(URLEncoder.encode(movie.videoPageUrl, StandardCharsets.UTF_8.toString())) }
                    )
                }
            }
        }
    }
}

@Composable
fun NetflixButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isPrimary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1.0f, label = "scale")
    val backgroundColor = if (isFocused) Color.White else if (isPrimary) Color.White else Color(0xFF555555)
    val contentColor = if (isFocused) Color.Black else if (isPrimary) Color.Black else Color.White

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor, contentColor = contentColor),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier.onFocusChanged { isFocused = it.isFocused }.scale(scale)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Badge(text: String, color: Color, textColor: Color = Color.White) {
    Text(
        text = text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor,
        modifier = Modifier.background(color, RoundedCornerShape(2.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun ContentRow(title: String, movies: List<Movie>, onMovieClick: (String) -> Unit) {
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = Color(0xFFE5E5E5), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 50.dp, bottom = 12.dp))

        // Fixed Height Container (Bounce Fix)
        Box(modifier = Modifier.height(300.dp)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 50.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(movies) { movie -> MovieCard(movie, onMovieClick) }
            }
        }
    }
}

@Composable
fun MovieCard(movie: Movie, onMovieClick: (String) -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    // Scale slightly more aggressive for clarity
    val scale by animateFloatAsState(if (isFocused) 1.15f else 1f, label = "scale")

    Box(
        modifier = Modifier
            .width(140.dp)
            .aspectRatio(2f / 3f)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, if (isFocused) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable {
                try { onMovieClick(URLEncoder.encode(movie.videoPageUrl, StandardCharsets.UTF_8.toString())) }
                catch (e: Exception) { e.printStackTrace() }
            }
    ) {
        AsyncImage(model = movie.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Warning, null, tint = Color.Red, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(message, color = Color.White)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}