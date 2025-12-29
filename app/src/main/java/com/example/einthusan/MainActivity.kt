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
import com.example.einthusan.ui.screens.HomeScreen
import com.example.einthusan.ui.screens.PlayerScreen
import com.example.einthusan.ui.screens.SearchScreen
import com.example.einthusan.ui.theme.EinthusanTheme
import com.example.einthusan.ui.screens.DetailsScreen

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
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
                                // CLICKING A MOVIE NOW GOES TO DETAILS, NOT PLAYER
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
                                // CLICKING A MOVIE IN SEARCH ALSO GOES TO DETAILS
                                onMovieClick = { encodedUrl ->
                                    navController.navigate("details/$encodedUrl")
                                }
                            )
                        }

                        // 3. DETAILS (NEW!)
                        composable(
                            route = "details/{videoUrl}",
                            arguments = listOf(navArgument("videoUrl") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val videoUrl = backStackEntry.arguments?.getString("videoUrl") ?: ""
                            DetailsScreen(
                                videoUrl = videoUrl,
                                onPlayClick = { url ->
                                    // Encoding again might be double-encoding, usually safe to pass 'url' 
                                    // if the player route expects encoded string.
                                    // Since 'url' comes from our scraped data (unencoded), 
                                    // we should encode it before passing to route 'player/{url}'.
                                    val encoded = java.net.URLEncoder.encode(url, "UTF-8")
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
                            PlayerScreen(videoPageUrl = videoUrl)
                        }
                    }
                }
            }
        }
    }
}