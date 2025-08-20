package com.example.micrecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> /* kullanıcı yanıtladı */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        startBtn   = findViewById(R.id.startButton)
        stopBtn    = findViewById(R.id.stopButton)

        requestNeededPermissions()

        startBtn.setOnClickListener {
            statusText.text = "Kayıt başlatılıyor…"
            val i = Intent(this, RecorderService::class.java).setAction(RecorderService.ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ContextCompat.startForegroundService(this, i)
            else
                startService(i)
            Toast.makeText(this, "Kayıt başladı", Toast.LENGTH_SHORT).show()
        }

        stopBtn.setOnClickListener {
            statusText.text = "Kayıt durduruluyor…"
            val i = Intent(this, RecorderService::class.java).setAction(RecorderService.ACTION_STOP)
            startService(i)
            Toast.makeText(this, "Kayıt durduruldu", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestNeededPermissions() {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()

        val need = perms.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need) permLauncher.launch(perms)
    }
}
