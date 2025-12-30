package com.example.einthusan.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import com.example.einthusan.data.CastMember
import com.example.einthusan.data.MovieDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailsScreen(
    videoUrl: String,
    onPlayClick: (String) -> Unit,
    viewModel: DetailsViewModel = viewModel()
) {
    val details by viewModel.details.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val playButtonRequester = remember { FocusRequester() }

    var isPlayLoading by remember { mutableStateOf(false) }

    LaunchedEffect(videoUrl) {
        viewModel.loadDetails(videoUrl)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 50.dp, top = 50.dp, bottom = 50.dp)
            ) {
                item {
                    MovieInfoSection(
                        movie = movie,
                        onPlayClick = { url ->
                            isPlayLoading = true
                            onPlayClick(url)
                        },
                        focusRequester = playButtonRequester,
                        isPlayLoading = isPlayLoading
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(30.dp))
                    Text("Cast", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 15.dp))
                    CastRow(cast = movie.cast)
                }
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
    Column(modifier = Modifier.width(600.dp)) {
        Text(text = movie.title, style = MaterialTheme.typography.displayMedium, color = Color.White, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(16.dp))

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

        Spacer(modifier = Modifier.height(16.dp))
        if (movie.genres.isNotEmpty()) {
            Text(text = movie.genres.joinToString(" | "), color = Color(0xFFCCCCCC), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(text = movie.synopsis, color = Color.LightGray, style = MaterialTheme.typography.bodyLarge, maxLines = 4)
        Spacer(modifier = Modifier.height(24.dp))

        if (isPlayLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Color.Red, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text("Loading...", color = Color.White, fontWeight = FontWeight.Bold)
            }
        } else {
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
                        onClick = { onPlayClick(url) },
                        modifier = (if (index == 0) Modifier.focusRequester(focusRequester) else Modifier)
                            .onFocusChanged { isFocused = it.isFocused }
                            .scale(scale),
                        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor)
                    ) {
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
        // Fixed PaddingValues by using consistent arguments
        contentPadding = PaddingValues(top = 20.dp, bottom = 20.dp, end = 50.dp)
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
        Box(modifier = Modifier.size(80.dp).border(2.dp, borderColor, CircleShape).clip(CircleShape).background(Color.DarkGray)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(member.imageUrl).crossfade(true).transformations(SharpenTransformation(LocalContext.current)).build(),
                contentDescription = member.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = member.name, color = if (isFocused) Color.White else Color.LightGray, fontSize = 12.sp, maxLines = 1, fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(text = member.role, color = Color.Gray, fontSize = 10.sp, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

class SharpenTransformation(private val context: android.content.Context) : Transformation {
    override val cacheKey: String = "sharpen_upscale"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap = withContext(Dispatchers.IO) {
        val width = input.width; val height = input.height
        val scaleFactor = if (width < 100) 2.0f else 1.0f
        if (scaleFactor == 1.0f) return@withContext input
        val newWidth = (width * scaleFactor).toInt(); val newHeight = (height * scaleFactor).toInt()
        val scaledBitmap = Bitmap.createScaledBitmap(input, newWidth, newHeight, true)
        return@withContext applySharpenEffect(scaledBitmap)
    }
    private fun applySharpenEffect(src: Bitmap): Bitmap {
        val width = src.width; val height = src.height
        val config = src.config ?: Bitmap.Config.ARGB_8888
        val result = Bitmap.createBitmap(width, height, config)
        val pixels = IntArray(width * height); src.getPixels(pixels, 0, width, 0, 0, width, height)
        val newPixels = IntArray(width * height)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val centerIndex = y * width + x; val top = (y - 1) * width + x; val bottom = (y + 1) * width + x; val left = y * width + (x - 1); val right = y * width + (x + 1)
                val pCenter = pixels[centerIndex]; val pTop = pixels[top]; val pBottom = pixels[bottom]; val pLeft = pixels[left]; val pRight = pixels[right]
                var r = (AndroidColor.red(pCenter) * 5) - (AndroidColor.red(pTop) + AndroidColor.red(pBottom) + AndroidColor.red(pLeft) + AndroidColor.red(pRight))
                var g = (AndroidColor.green(pCenter) * 5) - (AndroidColor.green(pTop) + AndroidColor.green(pBottom) + AndroidColor.green(pLeft) + AndroidColor.green(pRight))
                var b = (AndroidColor.blue(pCenter) * 5) - (AndroidColor.blue(pTop) + AndroidColor.blue(pBottom) + AndroidColor.blue(pLeft) + AndroidColor.blue(pRight))
                r = r.coerceIn(0, 255); g = g.coerceIn(0, 255); b = b.coerceIn(0, 255)
                newPixels[centerIndex] = AndroidColor.rgb(r, g, b)
            }
        }
        result.setPixels(newPixels, 0, width, 0, 0, width, height)
        return result
    }
}