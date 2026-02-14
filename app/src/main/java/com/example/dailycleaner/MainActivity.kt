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
import android.widget.Toast
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
    private val storagePerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) Toast.makeText(this, "Доступ к файлам разрешен", Toast.LENGTH_SHORT).show()
        else Toast.makeText(this, "Доступ к файлам отклонен", Toast.LENGTH_SHORT).show()
    }
    // SAF не используется — автоматическая очистка с MANAGE_EXTERNAL_STORAGE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        txt = findViewById(R.id.txtResult)
        val btnPermission = findViewById<MaterialButton>(R.id.btnPermission)
        val switchSchedule = findViewById<MaterialCheckBox>(R.id.chkSchedule)
        val btnClean = findViewById<MaterialButton>(R.id.btnClean)
        val btnLog = findViewById<MaterialButton>(R.id.btnLog)
        val spInterval = findViewById<AutoCompleteTextView>(R.id.spInterval)
        val chkThumbs = findViewById<MaterialCheckBox>(R.id.chkThumbs)
        val chkDownload = findViewById<MaterialCheckBox>(R.id.chkDownload)
        val chkData = findViewById<MaterialCheckBox>(R.id.chkData)
        val chkMedia = findViewById<MaterialCheckBox>(R.id.chkMedia)
        val chkApp = findViewById<MaterialCheckBox>(R.id.chkApp)
        // кнопок выбора SAF нет

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
        switchSchedule.isChecked = Prefs.isAutoEnabled(this)

        btnPermission.setOnClickListener {
            requestAllFilesAccess()
        }
        // ничего не делаем — автоматический режим

        btnClean.setOnClickListener {
            if (!FileCleaner.hasAllFilesAccess(this)) {
                Toast.makeText(this, "Нет доступа к файлам! Нажмите 'Разрешить доступ'", Toast.LENGTH_LONG).show()
            }
            val report = FileCleaner.cleanAccessibleJunk(this, readConfigAndPersist(chkThumbs, chkDownload, chkData, chkMedia, chkApp))
            LogStore.append(this, report)
            txt.text = getString(R.string.last_result, human(report.totalFreed))
            if (report.totalFreed == 0L && FileCleaner.hasAllFilesAccess(this)) {
                 Toast.makeText(this, "Мусор не найден или доступ ограничен системой (Android 11+)", Toast.LENGTH_SHORT).show()
            }
        }

        switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setAutoEnabled(this, isChecked)
            if (isChecked) schedule() else cancel()
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

    override fun onStart() {
        super.onStart()
        if (!Prefs.isOnboardDone(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            return
        }
        ensureDataMediaAccessIfNeeded()
    }

    private fun schedule() {
        val hours = Prefs.getIntervalHours(this)
        val req = PeriodicWorkRequestBuilder<DailyCleanWorker>(hours, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("daily_clean", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    private fun ensureDataMediaAccessIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && android.os.Environment.isExternalStorageManager()) {
            val root = android.os.Environment.getExternalStorageDirectory()
            val dataDir = java.io.File(root, "Android/data")
            val mediaDir = java.io.File(root, "Android/media")
            val canListData = dataDir.exists() && (runCatching { dataDir.listFiles() }.getOrNull() != null)
            val canListMedia = mediaDir.exists() && (runCatching { mediaDir.listFiles() }.getOrNull() != null)
            if (!canListData && Prefs.getDataTreeUri(this) == null) {
                openTreePicker(initialDoc = "primary:Android/data") { pickDataTree.launch(it) }
            } else if (!canListMedia && Prefs.getMediaTreeUri(this) == null) {
                openTreePicker(initialDoc = "primary:Android/media") { pickMediaTree.launch(it) }
            }
        }
    }

    private fun cancel() {
        WorkManager.getInstance(this).cancelUniqueWork("daily_clean")
    }

    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val uri = Uri.parse("package:$packageName")
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri))
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION))
                } catch (e2: Exception) {
                    Toast.makeText(this, "Не удалось открыть настройки доступа", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            storagePerm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    // нет SAF

    // онбординг берет на себя запрос прав

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
