package com.example.micrecorder

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* izin sonucu */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // İzinleri iste
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permLauncher.launch(perms.toTypedArray())

        val status = findViewById<TextView>(R.id.txtStatus)
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)

        btnStart.setOnClickListener {
            val i = Intent(this, RecorderService::class.java).apply {
                action = RecorderService.ACTION_START
            }
            ContextCompat.startForegroundService(this, i)
            status.text = "Kayıt: BAŞLADI"
        }

        btnStop.setOnClickListener {
            val i = Intent(this, RecorderService::class.java).apply {
                action = RecorderService.ACTION_STOP
            }
            ContextCompat.startForegroundService(this, i)
            status.text = "Kayıt: DURDU"
        }
    }
}
