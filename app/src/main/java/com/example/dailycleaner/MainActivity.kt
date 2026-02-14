package com.example.dailycleaner

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private lateinit var txt: TextView
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        txt = findViewById(R.id.txtResult)
        val btnPermission = findViewById<Button>(R.id.btnPermission)
        val switchSchedule = findViewById<Switch>(R.id.switchSchedule)
        val btnClean = findViewById<Button>(R.id.btnClean)

        btnPermission.setOnClickListener {
            requestAllFilesAccess()
        }

        btnClean.setOnClickListener {
            val freed = FileCleaner.cleanAccessibleJunk(this)
            txt.text = getString(R.string.last_result, human(freed))
        }

        switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) schedule()
            else cancel()
        }

        if (Build.VERSION.SDK_INT >= 33) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun schedule() {
        val req = PeriodicWorkRequestBuilder<DailyCleanWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("daily_clean", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    private fun cancel() {
        WorkManager.getInstance(this).cancelUniqueWork("daily_clean")
    }

    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                val uri = Uri.parse("package:$packageName")
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri))
            }
        }
    }

    private fun human(bytes: Long): String {
        if (bytes < 1024) return "$bytes Б"
        val z = (63 - java.lang.Long.numberOfLeadingZeros(bytes)) / 10
        return String.format("%.1f %cБ", bytes.toDouble() / (1L shl (z * 10)).toDouble(), " КМГТПЭ"[z])
    }
}
