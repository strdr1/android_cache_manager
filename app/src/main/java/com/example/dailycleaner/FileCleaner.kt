package com.example.dailycleaner

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

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
    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true
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
        if (hasAllFilesAccess()) {
            if (config.androidDataCaches) {
                data += cleanPerAppCache(File(root, "Android/data"))
            }
            if (config.androidMediaCaches) {
                media += deleteByPatterns(File(root, "Android/media"), setOf("cache", "temp", "tmp"))
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
        if (dataDir.exists()) {
            dataDir.listFiles()?.forEach { app ->
                val cache = File(app, "cache")
                if (cache.exists()) freed += deleteDir(cache)
            }
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
