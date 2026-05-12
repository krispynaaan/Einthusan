package com.example.einthusan.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File

object UpdateManager {

    private const val GITHUB_USERNAME = "krispynaaan"
    private const val GITHUB_REPO = "Einthusan"
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$GITHUB_USERNAME/$GITHUB_REPO/releases/latest"
    
    // Simple class to parse minimum required fields from JSON
    data class GitHubRelease(val tag_name: String, val assets: List<GitHubAsset>)
    data class GitHubAsset(val name: String, val browser_download_url: String)

    suspend fun checkForUpdates(context: Context) = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(LATEST_RELEASE_URL).build()
            val response: Response = client.newCall(request).execute()

            if (response.isSuccessful) {
                response.body?.string()?.let { responseBody ->
                    val release = Gson().fromJson(responseBody, GitHubRelease::class.java)
                    
                    val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    val latestVersion = release.tag_name.removePrefix("v")

                    // Simple version check (can be expanded for semantic versioning)
                    if (latestVersion != currentVersion) {
                        val apkAsset = release.assets.find { it.name.endsWith(".apk") }
                        apkAsset?.browser_download_url?.let { downloadUrl ->
                            downloadAndInstall(context, downloadUrl, "Einthusan Update")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun downloadAndInstall(context: Context, url: String, title: String) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(url)
        val fileName = "app-update.apk"
        val destinationFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val request = DownloadManager.Request(uri)
            .setTitle(title)
            .setDescription("Downloading latest version...")
            .setDestinationUri(Uri.fromFile(destinationFile))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/vnd.android.package-archive")

        val downloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk(context, destinationFile)
                    context.unregisterReceiver(this)
                }
            }
        }
        
        context.registerReceiver(
            receiver, 
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_EXPORTED
        )
    }

    private fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}