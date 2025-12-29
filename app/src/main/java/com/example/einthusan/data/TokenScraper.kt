package com.example.einthusan.data

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TokenScraper(private val context: Context) {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun scrapeStreamUrl(videoPageUrl: String): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            // Initialize invisible WebView
            val webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = Constants.USER_AGENT

                // 1x1 pixel size effectively invisible but still renders
                layoutParams = ViewGroup.LayoutParams(1, 1)
            }

            var resultFound = false

            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): android.webkit.WebResourceResponse? {
                    val url = request?.url.toString()

                    // The Intercept Condition
                    if (url.contains(".m3u8") && url.contains("cdn") && !resultFound) {
                        resultFound = true

                        // We found it! Resume the coroutine
                        continuation.resume(url)

                        // Cleanup on Main thread
                        view?.post {
                            view.stopLoading()
                            view.destroy()
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }

            // Start loading
            webView.loadUrl(videoPageUrl)

            // Safety timeout (optional but recommended in production) could be added here

            // Handle coroutine cancellation to clean up WebView
            continuation.invokeOnCancellation {
                webView.stopLoading()
                webView.destroy()
            }
        }
    }
}