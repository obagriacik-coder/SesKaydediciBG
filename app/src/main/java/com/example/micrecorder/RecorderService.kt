package com.example.micrecorder

import android.app.*
import android.content.ContentValues
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RecorderService : Service() {

    private var recorder: MediaRecorder? = null
    private var recording = false
    private var savedUri = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_START" -> startRecording()
            "ACTION_STOP"  -> stopRecording()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startRecording() {
        if (recording) return
        createNotification()

        val fileName = "BG_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".m4a"

        val outputFd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: MediaStore → Music/MicRecorder
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/MicRecorder")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            savedUri = uri?.toString().orEmpty()
            contentResolver.openFileDescriptor(uri!!, "w")!!.fileDescriptor.also {
                // IS_PENDING=0 ile yayına al
                values.clear(); values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            }
        } else {
            // Android 9 ve altı: /Music/MicRecorder klasörü
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "MicRecorder")
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, fileName)
            outFile.absolutePath.also { savedUri = it }
            @Suppress("DEPRECATION")
            outFile
        }

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setOutputFile(outputFd)
            } else {
                @Suppress("DEPRECATION")
                setOutputFile(savedUri)
            }

            prepare()
            start()
        }
        recording = true
    }

    private fun stopRecording() {
        if (!recording) { stopForeground(true); stopSelf(); return }
        try {
            recorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (_: Exception) { /* yut */ }
        recorder = null
        recording = false
        stopForeground(true)
        stopSelf()
    }

    private fun createNotification() {
        val channelId = "rec_ch"
        val channelName = "Mikrofon Kaydı"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }

        val stopIntent = Intent(this, RecorderService::class.java).apply { action = "ACTION_STOP" }
        val stopPi = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.presence_audio_online)
            .setContentTitle("Kayıt sürüyor…")
            .setContentText("Müzik/MicRecorder klasörüne kaydediliyor")
            .setOngoing(true)
            .addAction(0, "Durdur", stopPi)
            .build()

        startForeground(1123, notif)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        recording = false
    }
}
