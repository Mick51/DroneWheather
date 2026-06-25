package com.example.droneweather

import android.content.Context
import android.util.Log
import org.orekit.data.DataContext
import org.orekit.data.DirectoryCrawler
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

object OrekitInitializer {
    fun init(context: Context) {
        val dataDir = File(context.filesDir, "orekit-data")
        if (!dataDir.exists()) {
            dataDir.mkdirs()
        }

        // Sentinel check: if tai-utc.dat exists, we assume data is already there
        if (File(dataDir, "tai-utc.dat").exists() || File(dataDir, "orekit-data-main/tai-utc.dat").exists()) {
            Log.d("OrekitInit", "Orekit data already initialized, skipping copy.")
            setupDataProvider(dataDir)
            return
        }

        val assetManager = context.assets
        
        try {
            val assetsList = assetManager.list("") ?: emptyArray()
            Log.d("OrekitInit", "Assets found: ${assetsList.joinToString()}")
            
            // Always copy individual assets first
            Log.d("OrekitInit", "Copying individual assets")
            copyAssetsToInternal(context, "", dataDir)
            
            // Then extract zip if present (it might contain more or newer data)
            if (assetsList.contains("orekit-data.zip")) {
                Log.d("OrekitInit", "Extracting orekit-data.zip")
                extractZip(assetManager.open("orekit-data.zip"), dataDir)
            }
            
            // Log what was copied
            val files = dataDir.listFiles()
            Log.d("OrekitInit", "Internal files after init: ${files?.map { it.name }?.joinToString()}")
        } catch (e: Exception) {
            Log.e("OrekitInit", "Error during initialization", e)
        }
        
        setupDataProvider(dataDir)
    }

    private fun setupDataProvider(dataDir: File) {
        val dataProvidersManager = DataContext.getDefault().dataProvidersManager
        if (dataProvidersManager.providers.isEmpty()) {
            dataProvidersManager.addProvider(DirectoryCrawler(dataDir))
            dataDir.listFiles()?.filter { it.isDirectory }?.forEach { 
                dataProvidersManager.addProvider(DirectoryCrawler(it))
            }
        }
    }

    private fun copyAssetsToInternal(context: Context, path: String, targetDir: File) {
        val assetManager = context.assets
        val assets = assetManager.list(path) ?: return
        
        for (asset in assets) {
            if (path.isEmpty() && (asset == "images" || asset == "webkit" || asset == "sounds")) continue
            
            val fullAssetPath = if (path.isEmpty()) asset else "$path/$asset"
            val targetFile = File(targetDir, fullAssetPath)
            
            val subAssets = assetManager.list(fullAssetPath)
            if (!subAssets.isNullOrEmpty()) {
                targetFile.mkdirs()
                copyAssetsToInternal(context, fullAssetPath, targetDir)
            } else {
                try {
                    assetManager.open(fullAssetPath).use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {}
            }
        }
    }

    private fun extractZip(zipStream: InputStream, targetDir: File) {
        ZipInputStream(zipStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
