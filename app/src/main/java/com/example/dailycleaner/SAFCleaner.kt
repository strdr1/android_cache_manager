package com.example.dailycleaner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

object SAFCleaner {
    private val dirNames = setOf(
        "cache","caches",".cache",".caches",
        "tmp","temp",".tmp",".temp",
        "okhttp","glide","glide_cache","coil","coil_cache",
        "image_cache","video_cache","mediacache","exoplayer","exoplayer-cache",
        "thumb","thumbs","thumbnails",".thumbnails",
        "logs","log","crash","reports","bugreports",
        "httpcache","webview","webview_cache","volley","picasso","mediastore"
    )
    private val fileExts = setOf("tmp","log","cache","bak","old","part","partial","crdownload","download","journal")
    private val nameTokens = setOf("cache","tmp","temp","log","journal",".tmp",".log")

    fun cleanAndroidData(context: Context, uriStr: String?): Long {
        if (uriStr.isNullOrEmpty()) return 0L
        val root = DocumentFile.fromTreeUri(context, Uri.parse(uriStr)) ?: return 0L
        var freed = 0L
        root.listFiles().forEach { app ->
            if (app.isDirectory) {
                freed += deleteDirsByName(app)
                freed += deleteFilesByExt(app)
                freed += deleteFilesByNameContains(app)
            }
        }
        return freed
    }

    fun cleanAndroidMedia(context: Context, uriStr: String?): Long {
        if (uriStr.isNullOrEmpty()) return 0L
        val root = DocumentFile.fromTreeUri(context, Uri.parse(uriStr)) ?: return 0L
        var freed = 0L
        root.listFiles().forEach { app ->
            if (app.isDirectory) {
                freed += deleteDirsByName(app)
                freed += deleteFilesByExt(app)
                freed += deleteFilesByNameContains(app)
            }
        }
        return freed
    }

    private fun deleteDirsByName(base: DocumentFile): Long {
        var freed = 0L
        base.listFiles().forEach { f ->
            if (f.isDirectory) {
                val n = f.name?.lowercase() ?: ""
                if (dirNames.contains(n)) {
                    freed += deleteDir(f)
                } else {
                    freed += deleteDirsByName(f)
                }
            }
        }
        return freed
    }

    private fun deleteFilesByExt(base: DocumentFile): Long {
        var freed = 0L
        base.listFiles().forEach { f ->
            if (f.isDirectory) {
                freed += deleteFilesByExt(f)
            } else {
                val e = f.name?.substringAfterLast('.', "")?.lowercase() ?: ""
                if (fileExts.contains(e)) {
                    freed += f.length()
                    f.delete()
                }
            }
        }
        return freed
    }

    private fun deleteFilesByNameContains(base: DocumentFile): Long {
        var freed = 0L
        base.listFiles().forEach { f ->
            if (f.isDirectory) {
                freed += deleteFilesByNameContains(f)
            } else {
                val n = f.name?.lowercase() ?: ""
                if (nameTokens.any { n.contains(it) }) {
                    freed += f.length()
                    f.delete()
                }
            }
        }
        return freed
    }

    private fun deleteDir(dir: DocumentFile): Long {
        var freed = 0L
        dir.listFiles().forEach { child ->
            freed += if (child.isDirectory) deleteDir(child) else {
                val sz = child.length()
                child.delete()
                sz
            }
        }
        dir.delete()
        return freed
    }
}
