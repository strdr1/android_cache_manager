package com.example.dailycleaner

import android.content.Context
import java.io.File

object LogStore {
    private const val FILE_NAME = "cleaner_log.csv"
    fun append(ctx: Context, report: CleanReport) {
        val file = File(ctx.filesDir, FILE_NAME)
        file.appendText(report.asCsv() + "\n")
    }
    fun readAll(ctx: Context): List<String> {
        val file = File(ctx.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return file.readLines()
    }
}
