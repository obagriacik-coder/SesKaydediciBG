package com.example.micrecorder

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.micrecorder.databinding.ActivityMainBinding
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentOutputFile: File? = null
    private var isRecording = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNeededPermissions()
        checkBatteryOptimization()

        binding.startButton.setOnClickListener {
            if (isRecording) return@setOnClickListener
            startForegroundRecording()
        }

        binding.stopButton.setOnClickListener {
            if (!isRecording) return@setOnClickListener
            stopForegroundRecording()
        }
    }

    private fun requestNeededPermissions() {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
        permissionLauncher.launch(perms)
    }

    private fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            val pkg = packageName
            if (!pm.isIgnoringBatteryOptimizations(pkg)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$pkg")
                    }
                    startActivity(intent)
                    Toast.makeText(this, "Lütfen pil optimizasyonunu kapatın", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun startForegroundRecording() {
        if (!hasRecordPermission()) {
            Toast.makeText(this, "Mikrofon izni gerekiyor", Toast.LENGTH_SHORT).show()
            requestNeededPermissions()
            return
        }

        val fileName = "rec_${System.currentTimeMillis()}.m4a"
        val base = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        val dir = File(base, "SesKaydediciBG").apply { if (!exists()) mkdirs() }
        val out = File(dir, fileName)

        currentOutputFile = out

        // Java fonksiyonu: named argument yok
        RecorderService.start(this, out.absolutePath, false)

        isRecording = true
        binding.statusText.text = "Kayıt başladı: $fileName (arka planda sürüyor)"
        Toast.makeText(this, "Kayıt başladı (ekran kilidinde de devam eder)", Toast.LENGTH_SHORT).show()
    }

    private fun stopForegroundRecording() {
        RecorderService.stop(this)
        isRecording = false
        binding.statusText.text = "Kayıt durduruluyor..."
        Toast.makeText(this, "Kayıt durduruluyor", Toast.LENGTH_SHORT).show()

        val src = currentOutputFile
        currentOutputFile = null

        if (src != null) {
            // 🔒 ÖNEMLİ: Dosyanın tamamen kapanmasını bekle (moov atomu yazılsın)
            waitForFileFinalized(src)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val publicUri = insertIntoPublicMusic(src.name)
                    if (publicUri != null) {
                        copyFileToUri(src, publicUri)
                        finalizePendingAudio(publicUri)
                        Toast.makeText(this, "Müzik/SesKaydediciBG içine taşındı", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "MediaStore eklenemedi", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Kopyalama hatası", Toast.LENGTH_SHORT).show()
                }
            } else {
                try {
                    val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                    val dstDir = File(base, "SesKaydediciBG").apply { if (!exists()) mkdirs() }
                    val dst = File(dstDir, src.name)
                    src.copyTo(dst, overwrite = true)
                    Toast.makeText(this, "Müzik/SesKaydediciBG içine kopyalandı", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        binding.statusText.text = "Kayıt bekleniyor..."
    }

    // ---- MediaStore yardımcıları (Android 10+) ----
    private fun insertIntoPublicMusic(displayName: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            // MIME'i m4a olarak yaz (bazı oynatıcılar için daha uyumlu)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/m4a")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/SesKaydediciBG")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        return contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun copyFileToUri(src: File, dstUri: Uri) {
        val out: OutputStream = contentResolver.openOutputStream(dstUri, "w")
            ?: throw IllegalStateException("OutputStream açılamadı")
        FileInputStream(src).use { input ->
            out.use { output -> input.copyTo(output) }
        }
    }

    private fun finalizePendingAudio(uri: Uri) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.IS_PENDING, 0)
        }
        contentResolver.update(uri, values, null, null)
    }

    // ---- Dosya finalize bekleme: boyut sabitlenene kadar bekle + kısa gecikme ----
    private fun waitForFileFinalized(f: File) {
        var last = -1L
        var sameCount = 0
        // en fazla ~3sn bekle (30 * 100ms)
        repeat(30) {
            val len = f.length()
            if (len > 0 && len == last) {
                sameCount++
                if (sameCount >= 2) return  // iki ardışık ölçüm aynıysa yeter
            } else {
                sameCount = 0
            }
            last = len
            try { Thread.sleep(100) } catch (_: InterruptedException) {}
        }
        // ekstra küçük bir gecikme
        try { Thread.sleep(150) } catch (_: InterruptedException) {}
    }
}
