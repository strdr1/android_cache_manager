package com.example.dailycleaner

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {
    private lateinit var txt: TextView
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        txt = findViewById(R.id.txtResult)
        val btnPermission = findViewById<MaterialButton>(R.id.btnPermission)
        val switchSchedule = findViewById<MaterialSwitch>(R.id.switchSchedule)
        val btnClean = findViewById<MaterialButton>(R.id.btnClean)
        val btnLog = findViewById<MaterialButton>(R.id.btnLog)
        val spInterval = findViewById<AutoCompleteTextView>(R.id.spInterval)
        val chkThumbs = findViewById<MaterialCheckBox>(R.id.chkThumbs)
        val chkDownload = findViewById<MaterialCheckBox>(R.id.chkDownload)
        val chkData = findViewById<MaterialCheckBox>(R.id.chkData)
        val chkMedia = findViewById<MaterialCheckBox>(R.id.chkMedia)
        val chkApp = findViewById<MaterialCheckBox>(R.id.chkApp)

        val intervals = listOf(getString(R.string.interval_6h), getString(R.string.interval_12h), getString(R.string.interval_1d), getString(R.string.interval_3d), getString(R.string.interval_7d))
        spInterval.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, intervals))
        val hoursSaved = Prefs.getIntervalHours(this)
        spInterval.setText(intervals[indexForHours(hoursSaved)], false)

        val cfg = Prefs.loadConfig(this)
        chkThumbs.isChecked = cfg.thumbnails
        chkDownload.isChecked = cfg.downloadTemps
        chkData.isChecked = cfg.androidDataCaches
        chkMedia.isChecked = cfg.androidMediaCaches
        chkApp.isChecked = cfg.appCache

        btnPermission.setOnClickListener {
            requestAllFilesAccess()
        }

        btnClean.setOnClickListener {
            val report = FileCleaner.cleanAccessibleJunk(this, readConfigAndPersist(chkThumbs, chkDownload, chkData, chkMedia, chkApp))
            LogStore.append(this, report)
            txt.text = getString(R.string.last_result, human(report.totalFreed))
        }

        switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) schedule()
            else cancel()
        }

        spInterval.setOnItemClickListener { _, _, position, _ ->
            val h = hoursForIndex(position)
            Prefs.setIntervalHours(this@MainActivity, h)
            if (switchSchedule.isChecked) schedule()
        }

        btnLog.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }

        if (Build.VERSION.SDK_INT >= 33) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun schedule() {
        val hours = Prefs.getIntervalHours(this)
        val req = PeriodicWorkRequestBuilder<DailyCleanWorker>(hours, TimeUnit.HOURS).build()
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

    private fun readConfigAndPersist(chkThumbs: MaterialCheckBox, chkDownload: MaterialCheckBox, chkData: MaterialCheckBox, chkMedia: MaterialCheckBox, chkApp: MaterialCheckBox): CleanerConfig {
        val c = CleanerConfig(
            thumbnails = chkThumbs.isChecked,
            downloadTemps = chkDownload.isChecked,
            androidDataCaches = chkData.isChecked,
            androidMediaCaches = chkMedia.isChecked,
            appCache = chkApp.isChecked
        )
        Prefs.saveConfig(this, c)
        return c
    }

    private fun hoursForIndex(i: Int): Long = when (i) {
        0 -> 6L
        1 -> 12L
        2 -> 24L
        3 -> 72L
        else -> 168L
    }

    private fun indexForHours(h: Long): Int = when (h) {
        6L -> 0
        12L -> 1
        24L -> 2
        72L -> 3
        else -> 4
    }

    private fun human(bytes: Long): String {
        if (bytes < 1024) return "$bytes Б"
        val z = (63 - java.lang.Long.numberOfLeadingZeros(bytes)) / 10
        return String.format("%.1f %cБ", bytes.toDouble() / (1L shl (z * 10)).toDouble(), " КМГТПЭ"[z])
    }
}
