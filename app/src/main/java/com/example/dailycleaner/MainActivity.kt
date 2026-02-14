package com.example.dailycleaner

import android.Manifest
import android.app.Activity
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
import android.provider.DocumentsContract
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
    private val pickDataTree = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = res.data?.data ?: return@registerForActivityResult
            val flags = (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(uri, flags)
            Prefs.setDataTreeUri(this, uri.toString())
            Toast.makeText(this, "Подключена папка Android/data", Toast.LENGTH_SHORT).show()
        }
    }
    private val pickMediaTree = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = res.data?.data ?: return@registerForActivityResult
            val flags = (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(uri, flags)
            Prefs.setMediaTreeUri(this, uri.toString())
            Toast.makeText(this, "Подключена папка Android/media", Toast.LENGTH_SHORT).show()
        }
    }

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
        val btnPickData = findViewById<MaterialButton>(R.id.btnPickData)
        val btnPickMedia = findViewById<MaterialButton>(R.id.btnPickMedia)

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
        btnPickData.setOnClickListener { openTreePicker(initialDoc = "primary:Android/data") { pickDataTree.launch(it) } }
        btnPickMedia.setOnClickListener { openTreePicker(initialDoc = "primary:Android/media") { pickMediaTree.launch(it) } }

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
        askAllPermissionsAtEntry()
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

    private fun openTreePicker(initialDoc: String, launch: (Intent) -> Unit) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        intent.putExtra("android.provider.extra.SHOW_ADVANCED", true)
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true)
        if (Build.VERSION.SDK_INT >= 26) {
            val initUri = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", initialDoc)
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initUri)
        }
        // Попробуем принудительно открыть системный DocumentsUI (обходит файловый менеджер производителя)
        try {
            intent.setPackage("com.android.documentsui")
        } catch (_: Exception) { /* ignore */ }
        launch(intent)
    }

    private fun askAllPermissionsAtEntry() {
        if (Build.VERSION.SDK_INT >= 33) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                requestAllFilesAccess()
            }
        } else {
            storagePerm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        val dataUri = Prefs.getDataTreeUri(this)
        val mediaUri = Prefs.getMediaTreeUri(this)
        if (dataUri == null) {
            window.decorView.post {
                openTreePicker(initialDoc = "primary:Android/data") { pickDataTree.launch(it) }
            }
        } else if (mediaUri == null) {
            window.decorView.post {
                openTreePicker(initialDoc = "primary:Android/media") { pickMediaTree.launch(it) }
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
