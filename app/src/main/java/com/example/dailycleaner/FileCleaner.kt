package com.example.dailycleaner

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.util.concurrent.atomic.AtomicLong

object FileCleaner {
    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true
    }

    fun cleanAccessibleJunk(context: Context): Long {
        val freed = AtomicLong(0)
        val root = Environment.getExternalStorageDirectory()
        val targets = mutableListOf<File>()
        targets.add(File(root, "DCIM/.thumbnails"))
        targets.add(File(root, "Download"))
        targets.add(File(root, "Pictures/.thumbnails"))
        if (hasAllFilesAccess()) {
            targets.add(File(root, "Android/data"))
            targets.add(File(root, "Android/media"))
        }
        targets.forEach { file ->
            if (file.exists()) {
                if (file.isDirectory) {
                    if (file.name.equals(".thumbnails", true)) {
                        freed.addAndGet(deleteDir(file))
                    } else if (file.absolutePath.endsWith("/Android/data")) {
                        freed.addAndGet(cleanPerAppCache(file))
                    } else if (file.absolutePath.endsWith("/Android/media")) {
                        freed.addAndGet(deleteByPatterns(file, setOf("cache", "temp", "tmp")))
                    } else if (file.name.equals("Download", true)) {
                        freed.addAndGet(deleteByExtensions(file, setOf("tmp", "log", "cache")))
                    }
                } else {
                    freed.addAndGet(deleteFile(file))
                }
            }
        }
        freed.addAndGet(cleanOwnCache(context))
        return freed.get()
    }

    private fun cleanPerAppCache(dataDir: File): Long {
        var freed = 0L
        dataDir.listFiles()?.forEach { app ->
            val cache = File(app, "cache")
            if (cache.exists()) freed += deleteDir(cache)
        }
        return freed
    }

    private fun deleteByExtensions(dir: File, exts: Set<String>): Long {
        var freed = 0L
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
