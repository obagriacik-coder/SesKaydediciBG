package com.example.micrecorder

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* izin sonucu handle edilebilir */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // İzinleri iste
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permLauncher.launch(perms.toTypedArray())

        // activity_main.xml'deki id'ler ile eşleşecek
        val statusText = findViewById<TextView>(R.id.statusText) // küçük etiket
        val stateText  = findViewById<TextView>(R.id.stateText)  // büyük durum yazısı
        val btnStart   = findViewById<Button>(R.id.btnStart)
        val btnStop    = findViewById<Button>(R.id.btnStop)

        // Başlangıç metinleri
        statusText.text = getString(R.string.status_label)
        stateText.text  = getString(R.string.status_stopped)

        btnStart.setOnClickListener {
            val i = Intent(this, RecorderService::class.java).apply {
                action = RecorderService.ACTION_START
            }
            ContextCompat.startForegroundService(this, i)
            stateText.text = getString(R.string.status_recording)
        }

        btnStop.setOnClickListener {
            val i = Intent(this, RecorderService::class.java).apply {
                action = RecorderService.ACTION_STOP
            }
            ContextCompat.startForegroundService(this, i)
            stateText.text = getString(R.string.status_stopped)
        }
    }
}
