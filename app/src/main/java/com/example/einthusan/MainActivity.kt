package com.example.einthusan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import coil.ImageLoader
import coil.compose.LocalImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import androidx.compose.runtime.CompositionLocalProvider
import com.example.einthusan.ui.screens.HomeScreen
import com.example.einthusan.ui.screens.PlayerScreen
import com.example.einthusan.ui.screens.SearchScreen
import com.example.einthusan.ui.theme.EinthusanTheme
import com.example.einthusan.ui.screens.DetailsScreen
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Configure Persistent Coil Image Loader for robust aggressive caching
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .crossfade(true)
            .build()
            
        setContent {
            CompositionLocalProvider(LocalImageLoader provides imageLoader) {
                EinthusanTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RectangleShape,
                        colors = SurfaceDefaults.colors(containerColor = Color.Black)
                    ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "home") {

                        // 1. HOME
                        composable("home") {
                            HomeScreen(
                                onMovieClick = { encodedUrl ->
                                    navController.navigate("details/$encodedUrl")
                                },
                                onSearchClick = {
                                    navController.navigate("search")
                                }
                            )
                        }

                        // 2. SEARCH
                        composable("search") {
                            SearchScreen(
                                onMovieClick = { encodedUrl ->
                                    navController.navigate("details/$encodedUrl")
                                }
                            )
                        }

                        // 3. DETAILS
                        composable(
                            route = "details/{videoUrl}",
                            arguments = listOf(navArgument("videoUrl") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val videoUrl = backStackEntry.arguments?.getString("videoUrl") ?: ""
                            DetailsScreen(
                                videoUrl = videoUrl,
                                onPlayClick = { streamUrl ->
                                    // streamUrl is the RAW scraped .m3u8 link.
                                    // We must encode it before passing it to the navigation route.
                                    val encoded = URLEncoder.encode(streamUrl, StandardCharsets.UTF_8.toString())
                                    navController.navigate("player/$encoded")
                                }
                            )
                        }

                        // 4. PLAYER
                        composable(
                            route = "player/{videoUrl}",
                            arguments = listOf(navArgument("videoUrl") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val videoUrl = backStackEntry.arguments?.getString("videoUrl") ?: ""

                            PlayerScreen(streamUrl = videoUrl)
                        }
                    }
                }
            }
            }
        }
    }
}