package com.example.micrecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.Environment
import androidx.core.app.NotificationCompat
import java.io.File

class RecorderService : Service() {

    companion object {
        const val ACTION_START = "com.example.micrecorder.ACTION_START"
        const val ACTION_STOP  = "com.example.micrecorder.ACTION_STOP"

        private const val NOTIF_CHANNEL_ID = "recorder_channel"
        private const val NOTIF_ID = 1001
    }

    private var recorder: MediaRecorder? = null
    private var currentPath: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundIfNeeded()
                startRecording()
            }
            ACTION_STOP -> {
                stopRecording()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(NOTIF_CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        NOTIF_CHANNEL_ID,
                        "Arka Plan Ses Kaydı",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
            val notif: Notification = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_mic) // drawable/ic_mic.xml var
                .setContentTitle("Ses kaydı açık")
                .setContentText("Mikrofondan kayıt yapılıyor…")
                .setOngoing(true)
                .build()
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun startRecording() {
        if (recorder != null) return

        // Uyum derdi olmasın diye app’in özel klasörüne yazıyoruz (izin gerektirmez)
        val dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: filesDir
        val appDir = File(dir, "SesKaydediciBG").apply { if (!exists()) mkdirs() }
        val fileName = "REC_${System.currentTimeMillis()}.m4a"
        val out = File(appDir, fileName)
        currentPath = out.absolutePath

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(currentPath)

            prepare()
            start()
        }
    }

    private fun stopRecording() {
        recorder?.run {
            try { stop() } catch (_: Exception) {}
            release()
        }
        recorder = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
