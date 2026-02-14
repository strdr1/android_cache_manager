package com.example.dailycleaner

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.ForegroundServiceType
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class DailyCleanWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val chanId = "cleaner"
        ensureChannel(chanId)
        setForeground(createForegroundInfo(chanId))
        val freed = FileCleaner.cleanAccessibleJunk(applicationContext)
        val text = "Освобождено: " + human(freed)
        NotificationManagerCompat.from(applicationContext).notify(2001, NotificationCompat.Builder(applicationContext, chanId).setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("Очистка завершена").setContentText(text).build())
        return Result.success()
    }

    private fun ensureChannel(id: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(id, "Cleaner", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
    }

    private fun createForegroundInfo(channelId: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, channelId).setSmallIcon(android.R.drawable.ic_menu_delete).setContentTitle("Очистка").setContentText("Удаление мусора").setOngoing(true).build()
        return ForegroundInfo(2000, notification, if (Build.VERSION.SDK_INT >= 34) ForegroundServiceType.dataSync else 0)
    }

    private fun human(bytes: Long): String {
        if (bytes < 1024) return "$bytes Б"
        val z = (63 - java.lang.Long.numberOfLeadingZeros(bytes)) / 10
        return String.format("%.1f %cБ", bytes.toDouble() / (1L shl (z * 10)).toDouble(), " КМГТПЭ"[z])
    }
}
