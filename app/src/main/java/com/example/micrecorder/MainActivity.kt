package com.example.micrecorder

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
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
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var currentOutputFile: File? = null
    private var isRecording = false

    // Sayaç
    private val timerHandler = Handler(Looper.getMainLooper())
    private var startElapsed = 0L
    private val timerTick = object : Runnable {
        override fun run() {
            val elapsed = SystemClock.elapsedRealtime() - startElapsed
            binding.timerText.text = formatElapsed(elapsed)
            timerHandler.postDelayed(this, 1000)
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNeededPermissions()
        checkBatteryOptimization()

        // Başlangıçta ikon ve sayaç gizli
        setRecordingUI(false)

        binding.startButton.setOnClickListener {
            if (isRecording) return@setOnClickListener
            startForegroundRecording()
        }
        binding.stopButton.setOnClickListener {
            if (!isRecording) return@setOnClickListener
            stopForegroundRecording()
        }
    }

    // ---- İzinler ----
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

    private fun hasRecordPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    // ---- Doze / Pil Optimizasyonu ----
    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    // ---- UI yardımcıları ----
    private fun setRecordingUI(recording: Boolean) {
        isRecording = recording
        binding.recIcon.visibility = if (recording) android.view.View.VISIBLE else android.view.View.GONE
        binding.timerText.visibility = if (recording) android.view.View.VISIBLE else android.view.View.GONE
        if (recording) {
            startElapsed = SystemClock.elapsedRealtime()
            binding.timerText.text = "00:00:00"
            timerHandler.removeCallbacksAndMessages(null)
            timerHandler.postDelayed(timerTick, 1000)
        } else {
            timerHandler.removeCallbacksAndMessages(null)
        }
    }

    private fun formatElapsed(ms: Long): String {
        val h = TimeUnit.MILLISECONDS.toHours(ms)
        val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    // ---- Kayıt Başlat/Durdur ----
    private fun startForegroundRecording() {
        if (!hasRecordPermission()) {
            Toast.makeText(this, "Mikrofon izni gerekiyor", Toast.LENGTH_SHORT).show()
            requestNeededPermissions(); return
        }

        val fileName = "rec_${System.currentTimeMillis()}.m4a" // AAC/M4A
        val base = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        val dir = File(base, "SesKaydediciBG").apply { if (!exists()) mkdirs() }
        val out = File(dir, fileName)
        currentOutputFile = out

        RecorderService.start(this, out.absolutePath, false)
        setRecordingUI(true) // ikon + sayaç aç
        Toast.makeText(this, "Recording…", Toast.LENGTH_SHORT).show()
    }

    private fun stopForegroundRecording() {
        RecorderService.stop(this)
        setRecordingUI(false) // ikon + sayaç kapat

        val src = currentOutputFile
        currentOutputFile = null
        if (src == null) return

        // Servisin finalize etmesi için küçük tampon, ardından dosya boyutu sabitlenmesini bekle
        Handler(Looper.getMainLooper()).postDelayed({
            waitForFileFinalized(src)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val publicUri = insertIntoPublicMusic(src.name)
                    if (publicUri != null) {
                        copyFileToUri(src, publicUri)
                        finalizePendingAudio(publicUri)
                        Toast.makeText(this, "Music/SesKaydediciBG'ye kaydedildi", Toast.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) { }
            } else {
                try {
                    val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                    val dstDir = File(base, "SesKaydediciBG").apply { if (!exists()) mkdirs() }
                    val dst = File(dstDir, src.name)
                    src.copyTo(dst, overwrite = true)
                } catch (_: Exception) { }
            }
        }, 250)
    }

    // ---- MediaStore yardımcıları ----
    private fun insertIntoPublicMusic(displayName: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
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
        val values = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
        contentResolver.update(uri, values, null, null)
    }

    private fun waitForFileFinalized(f: File) {
        var last = -1L
        var same = 0
        repeat(30) {
            val len = f.length()
            if (len > 0 && len == last) { same++; if (same >= 2) return }
            else same = 0
            last = len
            try { Thread.sleep(100) } catch (_: InterruptedException) {}
        }
        try { Thread.sleep(150) } catch (_: InterruptedException) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacksAndMessages(null)
    }
}
