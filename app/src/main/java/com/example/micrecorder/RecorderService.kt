package com.example.micrecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RecorderService : Service() {

    companion object {
        const val ACTION_START = "START_RECORDING"
        const val ACTION_STOP = "STOP_RECORDING"
        private const val CHANNEL_ID = "recorder_channel"
    }

    private var recorder: MediaRecorder? = null
    private var outFile: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (recorder != null) return // zaten çalışıyorsa tekrar başlama

        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ses Kaydedici")
            .setContentText("Kayıt yapılıyor...")
            .setSmallIcon(R.drawable.ic_mic) // drawable'da küçük ikon olmalı
            .build()
        startForeground(1, notification)

        try {
            val dir = getExternalFilesDir(null)  // uygulamaya özel klasör (garanti)
            if (dir != null && !dir.exists()) dir.mkdirs()

            val fileName = "BG_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(Date()) + ".m4a"

            outFile = File(dir, fileName)

            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outFile!!.absolutePath)
                prepare()
                start()
            }

            Log.d("RecorderService", "Kayıt başladı: ${outFile!!.absolutePath}")

        } catch (e: Exception) {
            Log.e("RecorderService", "Kayıt başlatılamadı", e)
            stopSelf()
        }
    }

    private fun stopRecording() {
        try {
            recorder?.apply {
                stop()
                release()
            }
            Log.d("RecorderService", "Kayıt durdu: ${outFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e("RecorderService", "Kapatılırken hata", e)
        } finally {
            recorder = null
            stopForeground(true)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Recorder Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
