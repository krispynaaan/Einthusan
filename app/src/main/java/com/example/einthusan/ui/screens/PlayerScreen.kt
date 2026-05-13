package com.example.einthusan.ui.screens

import android.util.Log
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
// ... existing imports ...
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.example.einthusan.data.Constants
import android.webkit.CookieManager

private const val TAG = "ExoPlayerEvent"

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    streamUrl: String,
    viewModel: PlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentStreamUrl by viewModel.streamUrl.collectAsState()
    val status by viewModel.status.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Keep screen on
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(streamUrl) {
        viewModel.playStream(streamUrl)
    }

    var isPlaying by remember { mutableStateOf(true) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(1L) }

    LaunchedEffect(isControlsVisible, isPlaying) {
        if (isControlsVisible && isPlaying) {
            delay(4000)
            isControlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                        android.view.KeyEvent.KEYCODE_ENTER -> {
                            if (!isControlsVisible) {
                                isControlsVisible = true
                            } else {
                                isPlaying = !isPlaying
                            }
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                            isControlsVisible = true
                            currentPosition = (currentPosition - 15000).coerceAtLeast(0L)
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            isControlsVisible = true
                            currentPosition = (currentPosition + 15000).coerceAtMost(duration)
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_UP,
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            isControlsVisible = true
                            true
                        }
                        else -> false
                    }
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        if (currentStreamUrl == null) {
            Text(text = status, color = Color.White)
        } else {
            val player: ExoPlayer = remember {
                val loadControl = DefaultLoadControl.Builder()
                    .setAllocator(DefaultAllocator(true, 64 * 1024))
                    .setBufferDurationsMs(
                        DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                        DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()

                val trackSelector = DefaultTrackSelector(context, AdaptiveTrackSelection.Factory())
                trackSelector.setParameters(
                    trackSelector.buildUponParameters()
                        .setTunnelingEnabled(false)
                )

                val renderersFactory = DefaultRenderersFactory(context)
                    .setEnableDecoderFallback(true)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

                ExoPlayer.Builder(context)
                    .setRenderersFactory(renderersFactory)
                    .setLoadControl(loadControl)
                    .setTrackSelector(trackSelector)
                    .build().apply {
                        playWhenReady = true
                    }
            }

            DisposableEffect(player) {
                val listener = object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "Player Error: ${error.message}", error)
                    }
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            duration = player.duration.coerceAtLeast(1L)
                        }
                    }
                }
                player.addListener(listener)
                onDispose { player.removeListener(listener) }
            }

            // Sync seek events to ExoPlayer
            LaunchedEffect(currentPosition) {
                if (kotlin.math.abs(player.currentPosition - currentPosition) > 1000) {
                    player.seekTo(currentPosition)
                }
            }

            // Update ticker timeline visually when controls are visible
            LaunchedEffect(isPlaying, isControlsVisible) {
                if (isControlsVisible) {
                    while (true) {
                        currentPosition = player.currentPosition
                        delay(1000)
                    }
                }
            }

            // Sync Play/Pause explicitly
            LaunchedEffect(isPlaying) {
                if (isPlaying && !player.isPlaying) {
                    player.play()
                } else if (!isPlaying && player.isPlaying) {
                    player.pause()
                }
            }

            LaunchedEffect(currentStreamUrl) {
                // Ensure we forward cookies and referer that the WebView used when scraping
                val cookieString = try {
                    CookieManager.getInstance().getCookie(currentStreamUrl ?: "")
                } catch (e: Exception) {
                    null
                }

                val defaultHeaders = mutableMapOf<String, String>()
                if (!cookieString.isNullOrBlank()) {
                    defaultHeaders["Cookie"] = cookieString
                }
                // Some CDNs require a Referer header matching the originating site
                defaultHeaders["Referer"] = Constants.BASE_URL

                val dataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(Constants.USER_AGENT)
                    .setConnectTimeoutMs(Constants.TIMEOUT)
                    .setReadTimeoutMs(Constants.TIMEOUT)
                    .setAllowCrossProtocolRedirects(true)
                    .setDefaultRequestProperties(defaultHeaders)

                val mediaSource = DefaultMediaSourceFactory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(currentStreamUrl!!))

                player.setMediaSource(mediaSource)
                player.prepare()
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> player.pause()
                        Lifecycle.Event.ON_RESUME -> player.play()
                        Lifecycle.Event.ON_DESTROY -> player.release()
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    player.release()
                }
            }

            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        this.keepScreenOn = true
                        this.useController = false // Disable native Android TV view
                        this.isFocusable = false
                        this.isFocusableInTouchMode = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Compose Netflix-style Overlay Layer
            AnimatedVisibility(
                visible = isControlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Dark Bottom Gradient
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp, vertical = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isPlaying) "⏸ PAUSE" else "▶ PLAY",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            
                            Spacer(modifier = Modifier.padding(12.dp))
                            
                            Text(
                                text = "Use DPAD L/R: Scrub ±15s   •   DPAD CENTER: Play/Pause",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 16.sp
                            )
                        }

                        // Progress Timeline
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = formatTime(currentPosition), color = Color.White, fontSize = 14.sp)
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 16.dp)
                                    .height(6.dp)
                                    .background(Color.DarkGray)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f))
                                        .height(6.dp)
                                        .background(Color.Red) // Netflix Red
                                )
                            }
                            Text(text = formatTime(duration), color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

// Helper to format Milliseconds -> HH:MM:SS
private fun formatTime(ms: Long): String {
    val tSecs = ms / 1000
    val h = tSecs / 3600
    val m = (tSecs % 3600) / 60
    val s = tSecs % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}