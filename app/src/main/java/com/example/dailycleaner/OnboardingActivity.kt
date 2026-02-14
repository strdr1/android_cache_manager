package com.example.dailycleaner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox

class OnboardingActivity : AppCompatActivity() {
    private lateinit var chkNotif: MaterialCheckBox
    private lateinit var chkAll: MaterialCheckBox
    private lateinit var chkData: MaterialCheckBox
    private lateinit var chkMedia: MaterialCheckBox

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        updateState()
    }
    private val storagePerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        updateState()
    }
    private val pickDataTree = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = res.data?.data ?: return@registerForActivityResult
            val flags = (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(uri, flags)
            Prefs.setDataTreeUri(this, uri.toString())
        }
        updateState()
    }
    private val pickMediaTree = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = res.data?.data ?: return@registerForActivityResult
            val flags = (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(uri, flags)
            Prefs.setMediaTreeUri(this, uri.toString())
        }
        updateState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        chkNotif = findViewById(R.id.chkNotif)
        chkAll = findViewById(R.id.chkAllFiles)
        chkData = findViewById(R.id.chkData)
        chkMedia = findViewById(R.id.chkMedia)

        findViewById<MaterialButton>(R.id.btnNotif).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            else Toast.makeText(this, getString(R.string.onb_not_needed), Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.btnAllFiles).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val uri = Uri.parse("package:$packageName")
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            } else {
                storagePerm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        findViewById<MaterialButton>(R.id.btnPickData).setOnClickListener {
            openTreePicker("primary:Android/data") { pickDataTree.launch(it) }
        }
        findViewById<MaterialButton>(R.id.btnPickMedia).setOnClickListener {
            openTreePicker("primary:Android/media") { pickMediaTree.launch(it) }
        }
        findViewById<MaterialButton>(R.id.btnDone).setOnClickListener {
            Prefs.setOnboardDone(this, true)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        updateState()
    }

    private fun updateState() {
        val notifOk = if (Build.VERSION.SDK_INT >= 33) shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS).not() else true
        val allOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) android.os.Environment.isExternalStorageManager() else true
        val dataOk = Prefs.getDataTreeUri(this) != null
        val mediaOk = Prefs.getMediaTreeUri(this) != null

        chkNotif.isChecked = notifOk
        chkAll.isChecked = allOk
        chkData.isChecked = dataOk
        chkMedia.isChecked = mediaOk

        findViewById<MaterialButton>(R.id.btnDone).isEnabled = allOk && dataOk && mediaOk
    }

    private fun openTreePicker(initialDoc: String, launch: (Intent) -> Unit) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        intent.putExtra("android.provider.extra.SHOW_ADVANCED", true)
        if (Build.VERSION.SDK_INT >= 26) {
            val initUri = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", initialDoc)
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initUri)
        }
        try { intent.setPackage("com.android.documentsui") } catch (_: Exception) {}
        launch(intent)
    }
}
