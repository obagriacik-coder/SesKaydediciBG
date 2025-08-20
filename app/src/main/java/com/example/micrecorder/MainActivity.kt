package com.example.micrecorder

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.micrecorder.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var recorder: MediaRecorder? = null
    private var isRecording = false

    private val micPermission = Manifest.permission.RECORD_AUDIO
    private val askMicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startRecording()
            else Toast.makeText(this, "Mikrofon izni gerekli!", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Başlat
        binding.startButton.setOnClickListener {
            if (!isRecording) {
                checkMicAndStart()
            } else {
                Toast.makeText(this, "Zaten kayıt yapılıyor", Toast.LENGTH_SHORT).show()
            }
        }

        // Durdur
        binding.stopButton.setOnClickListener {
            if (isRecording) stopRecording()
            else Toast.makeText(this, "Şu an aktif kayıt yok", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkMicAndStart() {
        when {
            ContextCompat.checkSelfPermission(this, micPermission) == PackageManager.PERMISSION_GRANTED ->
                startRecording()

            shouldShowRequestPermissionRationale(micPermission) -> {
                Toast.makeText(this, "Kayıt için mikrofon izni gerekli.", Toast.LENGTH_SHORT).show()
                askMicPermission.launch(micPermission)
            }

            else -> askMicPermission.launch(micPermission)
        }
    }

    private fun startRecording() {
        // Uygulamanın kendi klasörü: Android/data/<package>/files/Recordings
        val dir = File(getExternalFilesDir(null), "Recordings").apply { mkdirs() }
        val outFile = File(dir, "rec_${System.currentTimeMillis()}.m4a")

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()

        try {
            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4) // .m4a için MPEG_4 + AAC
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            binding.statusText.text = "Kayıt başladı: ${outFile.name}"
            Toast.makeText(this, "Kayıt başladı", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            binding.statusText.text = "Kayıt başlatılamadı"
            Toast.makeText(this, "Kayıt başlatılamadı!", Toast.LENGTH_SHORT).show()
            cleanupRecorder()
        }
    }

    private fun stopRecording() {
        try {
            recorder?.apply {
                stop()
                release()
            }
            binding.statusText.text = "Kayıt durduruldu"
            Toast.makeText(this, "Kayıt durduruldu", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Durdurulurken hata!", Toast.LENGTH_SHORT).show()
        } finally {
            cleanupRecorder()
            isRecording = false
        }
    }

    private fun cleanupRecorder() {
        recorder?.release()
        recorder = null
    }

    override fun onDestroy() {
        if (isRecording) {
            // Güvenli kapatma
            try { recorder?.stop() } catch (_: Exception) {}
        }
        cleanupRecorder()
        super.onDestroy()
    }
}
