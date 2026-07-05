package com.example.garminpos

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val p = Prefs(this)

        val ip = findViewById<EditText>(R.id.ip).apply { setText(p.laptopIp) }
        val port = findViewById<EditText>(R.id.port).apply { setText(p.port.toString()) }
        val device = findViewById<EditText>(R.id.device).apply { setText(p.device) }

        findViewById<Button>(R.id.save).setOnClickListener {
            p.laptopIp = ip.text.toString().trim()
            p.port = port.text.toString().trim().toIntOrNull() ?: 8000
            p.device = device.text.toString().trim()
            Toast.makeText(this, "저장됨", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.test).setOnClickListener {
            p.laptopIp = ip.text.toString().trim()
            p.port = port.text.toString().trim().toIntOrNull() ?: 8000
            p.device = device.text.toString().trim()
            ScanSender(p.baseUrl, p.device).send("PING", System.currentTimeMillis())
            Toast.makeText(this, "PING 전송 — 노트북 창 확인", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.openA11y).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}
