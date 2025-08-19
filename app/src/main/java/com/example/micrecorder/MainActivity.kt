package com.example.micrecorder

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.micrecorder.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    // Çoklu izin launcher
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val allGranted = grants.all { it.value }
            if (allGranted) {
                startRecorderService()
            } else {
                Toast.makeText(this, getString(R.string.perm_required), Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Başlangıç statüsü
        b.statusText.text = getString(R.string.status_idle)

        // Başlat
        b.startButton.setOnClickListener {
            requestNeededPermissionsAndStart()
        }

        // Durdur
        b.stopButton.setOnClickListener {
            stopService(Intent(this, RecorderService::class.java))
            b.statusText.text = getString(R.string.status_stopped)
        }
    }

    private fun requestNeededPermissionsAndStart() {
        val req = mutableListOf(Manifest.permission.RECORD_AUDIO)

        // Android 13+ bildirim izni (servis bildirimi için)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            req += Manifest.permission.POST_NOTIFICATIONS
        }
        // Android 9 ve altı için harici depolama yazma izni
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            req += Manifest.permission.WRITE_EXTERNAL_STORAGE
            req += Manifest.permission.READ_EXTERNAL_STORAGE
        }

        permissionLauncher.launch(req.toTypedArray())
    }

    private fun startRecorderService() {
        val intent = Intent(this, RecorderService::class.java)
        // Foreground service’i güvenli başlat
        ContextCompat.startForegroundService(this, intent)
        b.statusText.text = getString(R.string.status_recording)
    }
}
