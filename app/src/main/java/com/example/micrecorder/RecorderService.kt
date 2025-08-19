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
import androidx.core.app.NotificationCompat
import java.io.File

class RecorderService : Service() {

    companion object {
        const val ACTION_START = "com.example.micrecorder.action.START"
        const val ACTION_STOP  = "com.example.micrecorder.action.STOP"

        private const val NOTIF_ID = 42
        private const val CHANNEL_ID = "rec_channel"
        private const val CHANNEL_NAME = "Kayıt"
    }

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP  -> stopRecording()
        }
        return START_STICKY
    }

    private fun startRecording() {
        if (recorder != null) return

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Kayıt devam ediyor"))

        // Kaydedilecek dosya
        val dir = File(getExternalFilesDir(null), "records").apply { mkdirs() }
        outputFile = File(dir, "rec_${System.currentTimeMillis()}.m4a")

        // Basit MediaRecorder kurulumu (SDK 31+ için geçerli yaklaşım)
        val r = MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(128_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(outputFile!!.absolutePath)
        r.prepare()
        r.start()
        recorder = r
    }

    private fun stopRecording() {
        try {
            recorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (_: Exception) { /* yut */ }
        recorder = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
                )
                nm.createNotificationChannel(ch)
            }
        }
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }
}
