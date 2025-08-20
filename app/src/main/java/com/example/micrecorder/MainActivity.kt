package com.example.micrecorder

import android.Manifest
import android.content.ContentValues
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
    private var onStopRecording: (() -> Unit)? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNeededPermissions()

        binding.startButton.setOnClickListener {
            if (isRecording) return@setOnClickListener
            startRecording()
        }

        binding.stopButton.setOnClickListener {
            if (!isRecording) return@setOnClickListener
            stopRecording()
        }
    }

    private fun requestNeededPermissions() {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_AUDIO) // okuma için; yazma MediaStore ile
            } else {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
        permissionLauncher.launch(perms)
    }

    private fun startRecording() {
        val fileName = "rec_${System.currentTimeMillis()}.m4a"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ : MediaStore -> Music/SesKaydediciBG
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/SesKaydediciBG")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values
            ) ?: run {
                Toast.makeText(this, "MediaStore'a yazılamadı!", Toast.LENGTH_SHORT).show()
                return
            }
            val pfd = contentResolver.openFileDescriptor(uri, "w") ?: run {
                Toast.makeText(this, "Dosya açılamadı!", Toast.LENGTH_SHORT).show()
                return
            }

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()
            try {
                recorder?.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(128_000)
                    setAudioSamplingRate(44_100)
                    setOutputFile(pfd.fileDescriptor)
                    prepare()
                    start()
                }
                isRecording = true
                binding.statusText.text = "Kayıt başladı: $fileName"
                Toast.makeText(this, "Müzik/SesKaydediciBG içine kaydediliyor", Toast.LENGTH_SHORT).show()

                onStopRecording = {
                    try {
                        pfd.close()
                        values.clear()
                        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                        contentResolver.update(uri, values, null, null)
                    } catch (_: Exception) { }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Kayıt başlatılamadı!", Toast.LENGTH_SHORT).show()
                onStopRecording = null
                recorder?.release(); recorder = null
            }
        } else {
            // Android 9 ve altı: /Music/SesKaydediciBG
            val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val dir = File(base, "SesKaydediciBG").apply { if (!exists()) mkdirs() }
            val out = File(dir, fileName)

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()
            try {
                recorder?.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(128_000)
                    setAudioSamplingRate(44_100)
                    setOutputFile(out.absolutePath)
                    prepare()
                    start()
                }
                isRecording = true
                binding.statusText.text = "Kayıt başladı: ${out.name}"
                Toast.makeText(this, "Müzik/SesKaydediciBG içine kaydediliyor", Toast.LENGTH_SHORT).show()
                onStopRecording = null
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Kayıt başlatılamadı!", Toast.LENGTH_SHORT).show()
                recorder?.release(); recorder = null
            }
        }
    }

    private fun stopRecording() {
        try {
            recorder?.apply {
                stop()
                release()
            }
            Toast.makeText(this, "Kayıt durduruldu", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Kayıt durdurulamadı", Toast.LENGTH_SHORT).show()
        } finally {
            isRecording = false
            recorder = null
            onStopRecording?.invoke()
            onStopRecording = null
            binding.statusText.text = "Kayıt bekleniyor..."
        }
    }
}
