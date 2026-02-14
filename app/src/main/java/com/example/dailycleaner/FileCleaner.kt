package com.example.dailycleaner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CleanerConfig(
    val thumbnails: Boolean,
    val downloadTemps: Boolean,
    val androidDataCaches: Boolean,
    val androidMediaCaches: Boolean,
    val appCache: Boolean
)

data class CleanReport(
    val timestamp: Long,
    val totalFreed: Long,
    val thumbnailsFreed: Long,
    val downloadTempsFreed: Long,
    val dataCachesFreed: Long,
    val mediaCachesFreed: Long,
    val appCacheFreed: Long
) {
    fun asCsv(): String {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val ts = df.format(Date(timestamp))
        return listOf(
            ts,
            totalFreed,
            thumbnailsFreed,
            downloadTempsFreed,
            dataCachesFreed,
            mediaCachesFreed,
            appCacheFreed
        ).joinToString(",")
    }
}

object FileCleaner {
    fun hasAllFilesAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun cleanAccessibleJunk(context: Context, config: CleanerConfig): CleanReport {
        val root = Environment.getExternalStorageDirectory()
        var thumbs = 0L
        var dltmp = 0L
        var data = 0L
        var media = 0L
        var app = 0L

        if (config.thumbnails) {
            thumbs += deleteDir(File(root, "DCIM/.thumbnails"))
            thumbs += deleteDir(File(root, "Pictures/.thumbnails"))
        }
        if (config.downloadTemps) {
            dltmp += deleteByExtensions(File(root, "Download"), setOf("tmp", "log", "cache"))
        }
        val dataDir = File(root, "Android/data")
        val mediaDir = File(root, "Android/media")
        val canFileData = hasAllFilesAccess(context) && dataDir.exists() && dataDir.listFiles() != null
        val canFileMedia = hasAllFilesAccess(context) && mediaDir.exists() && mediaDir.listFiles() != null
        if (config.androidDataCaches) {
            data += if (canFileData) {
                cleanPerAppCache(dataDir)
            } else {
                SAFCleaner.cleanAndroidData(context, Prefs.getDataTreeUri(context))
            }
        }
        if (config.androidMediaCaches) {
            media += if (canFileMedia) {
                deleteByPatterns(mediaDir, setOf("cache", "temp", "tmp"))
            } else {
                SAFCleaner.cleanAndroidMedia(context, Prefs.getMediaTreeUri(context))
            }
        }
        if (config.appCache) {
            app += cleanOwnCache(context)
        }
        val total = thumbs + dltmp + data + media + app
        return CleanReport(System.currentTimeMillis(), total, thumbs, dltmp, data, media, app)
    }

    private fun cleanPerAppCache(dataDir: File): Long {
        var freed = 0L
        if (!dataDir.exists()) return 0L
        val dirNames = setOf(
            "cache","caches",".cache",".caches",
            "tmp","temp",".tmp",".temp",
            "okhttp","glide","glide_cache","coil","coil_cache",
            "image_cache","video_cache","mediacache","exoplayer","exoplayer-cache",
            "thumb","thumbs","thumbnails",".thumbnails",
            "logs","log","crash","reports","bugreports",
            "httpcache","webview","webview_cache","volley","picasso","mediastore"
        )
        val fileExts = setOf("tmp","log","cache","bak","old","part","partial","crdownload","download","journal")
        dataDir.listFiles()?.forEach { app ->
            // классический cache
            val cache = File(app, "cache")
            if (cache.exists()) freed += deleteDir(cache)
            // часто встречается внутри files/
            val filesCache = File(app, "files")
            if (filesCache.exists()) {
                freed += deleteDirsByName(filesCache, dirNames)
                freed += deleteFilesByExt(filesCache, fileExts)
            }
            // на всякий случай пройдёмся по корню папки приложения
            freed += deleteDirsByName(app, dirNames)
            freed += deleteFilesByExt(app, fileExts)
            freed += deleteFilesByNameContains(app, setOf("cache","tmp","temp","log","journal",".tmp",".log"))
        }
        return freed
    }

    private fun deleteByExtensions(dir: File, exts: Set<String>): Long {
        var freed = 0L
        if (!dir.exists()) return 0L
        dir.listFiles()?.forEach {
            if (it.isDirectory) freed += deleteByExtensions(it, exts)
            else {
                val ext = it.extension.lowercase()
                if (exts.contains(ext)) freed += deleteFile(it)
            }
        }
        return freed
    }

    private fun deleteByPatterns(dir: File, names: Set<String>): Long {
        var freed = 0L
        if (!dir.exists()) return 0L
        dir.listFiles()?.forEach { sub ->
            if (sub.isDirectory) {
                if (names.contains(sub.name.lowercase())) {
                    freed += deleteDir(sub)
                } else {
                    freed += deleteByPatterns(sub, names)
                }
            }
        }
        return freed
    }

    private fun deleteDirsByName(base: File, names: Set<String>): Long {
        var freed = 0L
        base.listFiles()?.forEach { f ->
            if (f.isDirectory) {
                val n = f.name.lowercase()
                if (names.contains(n)) {
                    freed += deleteDir(f)
                } else {
                    freed += deleteDirsByName(f, names)
                }
            }
        }
        return freed
    }

    private fun deleteFilesByExt(base: File, exts: Set<String>): Long {
        var freed = 0L
        base.listFiles()?.forEach { f ->
            if (f.isDirectory) {
                freed += deleteFilesByExt(f, exts)
            } else {
                val e = f.extension.lowercase()
                if (exts.contains(e)) freed += deleteFile(f)
            }
        }
        return freed
    }

    private fun deleteFilesByNameContains(base: File, tokens: Set<String>): Long {
        var freed = 0L
        base.listFiles()?.forEach { f ->
            if (f.isDirectory) {
                freed += deleteFilesByNameContains(f, tokens)
            } else {
                val n = f.name.lowercase()
                if (tokens.any { n.contains(it) }) {
                    freed += deleteFile(f)
                }
            }
        }
        return freed
    }

    private fun cleanOwnCache(context: Context): Long {
        val cache = context.cacheDir
        return deleteDir(cache)
    }

    private fun deleteDir(dir: File): Long {
        if (!dir.exists()) return 0L
        var freed = 0L
        dir.listFiles()?.forEach { child ->
            freed += if (child.isDirectory) deleteDir(child) else deleteFile(child)
        }
        dir.delete()
        return freed
    }

    private fun deleteFile(file: File): Long {
        val size = runCatching { file.length() }.getOrDefault(0L)
        file.delete()
        return size
    }
}
