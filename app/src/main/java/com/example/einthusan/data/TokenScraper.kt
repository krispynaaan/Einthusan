package com.example.einthusan.data

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "TokenScraper"

class TokenScraper(private val context: Context) {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun scrapeStreamUrl(videoPageUrl: String): String = withContext(Dispatchers.Main) {
        Log.d(TAG, "Starting scrape for: $videoPageUrl")

        val result = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<String> { continuation ->
                val webView = WebView(context).apply {
                    setLayerType(View.LAYER_TYPE_HARDWARE, null) // Hardware acceleration enabled for better perf unless crash

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = Constants.USER_AGENT

                    // WebView must have a size to process pages, even if off-screen
                    layoutParams = ViewGroup.LayoutParams(1, 1)
                }

                var resultFound = false

                webView.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): android.webkit.WebResourceResponse? {
                        val url = request?.url.toString()

                        // Look for the HLS stream URL (m3u8) served from the CDN
                        if (url.contains(".m3u8") && url.contains("cdn") && !resultFound) {
                            Log.i(TAG, "TOKEN FOUND! URL: $url")
                            resultFound = true

                            // Resume the coroutine with the found URL
                            if (continuation.isActive) {
                                continuation.resume(url)
                            }

                            // Cleanup on Main thread
                            view?.post {
                                try {
                                    view.stopLoading()
                                    view.clearHistory()
                                    view.removeAllViews()
                                    view.destroy()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error destroying WebView", e)
                                }
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                }

                Log.d(TAG, "Loading URL into WebView...")
                webView.loadUrl(videoPageUrl)

                // Cleanup if the coroutine is cancelled (e.g., user presses Back or timeouts)
                continuation.invokeOnCancellation {
                    Log.w(TAG, "Scrape Cancelled/Timeout. Cleaning up.")
                    webView.stopLoading()
                    webView.destroy()
                }
            }
        }
        
        result ?: throw Exception("Timeout waiting for video stream")
    }
}