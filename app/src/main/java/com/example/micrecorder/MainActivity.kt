package com.example.micrecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.micrecorder.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            // izin sonucu geldi; burada ekstra bir şey yapmaya gerek yok
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNeededPermissions()

        binding.startButton.setOnClickListener {
            binding.statusText.text = "Kayıt başlatılıyor…"
            val intent = Intent(this, RecorderService::class.java).apply {
                action = "ACTION_START"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
        }

        binding.stopButton.setOnClickListener {
            binding.statusText.text = "Kayıt durduruluyor…"
            val intent = Intent(this, RecorderService::class.java).apply {
                action = "ACTION_STOP"
            }
            startService(intent)
        }
    }

    private fun requestNeededPermissions() {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.FOREGROUND_SERVICE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()

        val need = perms.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need) permissionLauncher.launch(perms)
    }
}
