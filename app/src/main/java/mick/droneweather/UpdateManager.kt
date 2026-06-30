/*
 * Copyright (C) 2026 Mick
 */

package mick.droneweather

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateManager(private val context: Context) {

    suspend fun checkForUpdates(currentVersionCode: Long): GitHubRelease? {
        return withContext(Dispatchers.IO) {
            try {
                val latest = RetrofitInstance.githubApi.getLatestRelease()
                
                // 1. Try to extract version code from tag
                // If tag is "v1.7", we want "1.7". If it's "v8", we want "8"
                val tagClean = latest.tagName.lowercase().removePrefix("v").trim()
                
                // Try direct numeric conversion (for tags like "8")
                val latestVersionCode = tagClean.toLongOrNull() ?: 0L
                
                Log.d("UpdateManager", "DEBUG_UPDATE: Local=$currentVersionCode, GitHubTag=$tagClean (ExtractedCode=$latestVersionCode)")

                // 2. Compare Numerically first (recommended)
                if (latestVersionCode > 0) {
                    if (latestVersionCode > currentVersionCode) {
                        Log.d("UpdateManager", "DEBUG_UPDATE: New version found (Numeric)")
                        return@withContext latest
                    } else {
                        Log.d("UpdateManager", "DEBUG_UPDATE: Already up to date (Numeric)")
                        return@withContext null
                    }
                }

                // 3. Fallback: Semantic comparison for strings like "1.7" vs local info
                val currentVersionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
                val localClean = currentVersionName.lowercase().replace("beta", "").trim()
                
                Log.d("UpdateManager", "DEBUG_UPDATE: Fallback semantic: GitHub=$tagClean, Local=$localClean")
                
                if (tagClean != localClean && tagClean > localClean) {
                    Log.d("UpdateManager", "DEBUG_UPDATE: New version found (Semantic)")
                    latest
                } else {
                    Log.d("UpdateManager", "DEBUG_UPDATE: Already up to date (Semantic)")
                    null
                }
            } catch (e: Exception) {
                Log.e("UpdateManager", "Check failed: ${e.message}")
                null
            }
        }
    }

    suspend fun downloadAndInstall(release: GitHubRelease, onProgress: (Float) -> Unit) {
        val apkAsset = release.assets.find { it.name.endsWith(".apk") } ?: throw Exception("No APK found in release")
        
        withContext(Dispatchers.IO) {
            val destination = File(context.externalCacheDir ?: context.cacheDir, "update.apk")
            if (destination.exists()) destination.delete()

            val url = URL(apkAsset.downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()

            val fileLength = connection.contentLength
            val input = connection.inputStream
            val output = destination.outputStream()

            val data = ByteArray(4096)
            var total: Long = 0
            var count: Int
            while (input.read(data).also { count = it } != -1) {
                total += count
                if (fileLength > 0) {
                    onProgress(total.toFloat() / fileLength.toFloat())
                }
                output.write(data, 0, count)
            }

            output.flush()
            output.close()
            input.close()

            withContext(Dispatchers.Main) {
                installApk(destination)
            }
        }
    }

    private fun installApk(file: File) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Check if we can install from unknown sources on Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = "package:${context.packageName}".toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                // We can't immediately install, the user has to come back.
                // But we could also try to start the install intent after they come back.
                // For now, let's just start both or hope the system handles it.
                // Actually, it's better to just start the install intent and let the system prompt.
            }
        }

        context.startActivity(intent)
    }
}
