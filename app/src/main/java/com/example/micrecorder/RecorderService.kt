package com.example.micrecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File

class RecorderService : Service() {

    private var recorder: MediaRecorder? = null
    private val channelId = "recorder_channel"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_START" -> startRecording()
            "ACTION_STOP" -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (recorder != null) return

        // Kayıt klasörünü oluştur
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val appDir = File(musicDir, "SesKaydediciBG")
        if (!appDir.exists()) appDir.mkdirs()

        val fileName = "REC_${System.currentTimeMillis()}.m4a"
        val outputFile = File(appDir, fileName).absolutePath

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }

        recorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(outputFile)

            prepare()
            start()
        }

        startForeground(1, buildNotification("Kayıt sürüyor: $fileName"))
    }

    private fun stopRecording() {
        recorder?.apply {
            try {
                stop()
                release()
            } catch (_: Exception) { }
        }
        recorder = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Recorder", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Ses Kaydedici")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
