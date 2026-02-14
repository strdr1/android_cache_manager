package com.example.dailycleaner

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LogActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.setPadding(24,24,24,24)
        val lines = LogStore.readAll(this)
        tv.text = if (lines.isEmpty()) "Журнал пуст" else lines.joinToString("\n")
        setContentView(tv)
    }
}
