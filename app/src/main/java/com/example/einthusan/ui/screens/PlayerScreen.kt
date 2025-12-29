package com.example.einthusan.ui.screens

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.example.einthusan.data.Constants

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    videoPageUrl: String,
    viewModel: PlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val streamUrl by viewModel.streamUrl.collectAsState()
    val status by viewModel.status.collectAsState()

    // Lifecycle owner to detect Home press vs Back press
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(videoPageUrl) {
        viewModel.loadVideo(videoPageUrl)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (streamUrl == null) {
            Text(text = status, color = Color.White)
        } else {
            // We remember the player instance so it survives recompositions but not disposals
            val player = remember {
                ExoPlayer.Builder(context).build().apply {
                    playWhenReady = true
                }
            }

            // 1. SETUP PLAYER (Runs once when URL is ready)
            LaunchedEffect(streamUrl) {
                val dataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(Constants.USER_AGENT)
                    .setDefaultRequestProperties(mapOf("Referer" to Constants.BASE_URL))

                val mediaSource = DefaultMediaSourceFactory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(streamUrl!!))

                player.setMediaSource(mediaSource)
                player.prepare()
            }

            // 2. MANAGE LIFECYCLE (Pause on Home, Release on Back)
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> player.pause()
                        Lifecycle.Event.ON_RESUME -> player.play()
                        Lifecycle.Event.ON_STOP -> player.pause() // Extra safety
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)

                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    player.release() // Critical: Kills audio when backing out
                }
            }

            // 3. RENDER VIEW
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}