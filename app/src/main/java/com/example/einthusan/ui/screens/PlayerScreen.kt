package com.example.einthusan.ui.screens

import android.util.Log
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable(),
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
                }
                player.addListener(listener)
                onDispose { player.removeListener(listener) }
            }

            LaunchedEffect(currentStreamUrl) {
                val dataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(Constants.USER_AGENT)
                    .setConnectTimeoutMs(Constants.TIMEOUT)
                    .setReadTimeoutMs(Constants.TIMEOUT)
                    .setAllowCrossProtocolRedirects(true)

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
                        this.useController = true
                        this.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        this.isFocusable = true
                        this.isFocusableInTouchMode = true
                        this.requestFocus()
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .onKeyEvent { false }
            )
        }
    }
}