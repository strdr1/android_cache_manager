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

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        updateState()
    }
    private val storagePerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        updateState()
    }
    // выбор SAF убран — используется автоматическая очистка при наличии полного доступа

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        chkNotif = findViewById(R.id.chkNotif)
        chkAll = findViewById(R.id.chkAllFiles)

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
        chkNotif.isChecked = notifOk
        chkAll.isChecked = allOk
        // кнопка всегда активна, но рекомендуем выдать полный доступ
        findViewById<MaterialButton>(R.id.btnDone).isEnabled = true
    }

    // SAF не используется
}
