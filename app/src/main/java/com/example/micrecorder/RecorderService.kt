package com.example.micrecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecorderService : Service() {

    companion object {
        const val ACTION_START = "com.example.micrecorder.START"
        const val ACTION_STOP  = "com.example.micrecorder.STOP"
        private const val CHANNEL_ID = "rec_channel"
        private const val NOTIF_ID   = 1001
    }

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRecording()
            ACTION_START, null -> startRecording()
        }
        return START_STICKY
    }

    private fun startRecording() {
        if (recorder != null) return

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Kaydediliyor…"))

        val dir = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "recordings").apply { mkdirs() }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        outputFile = File(dir, "rec_$ts.m4a")

        recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
        recorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(outputFile!!.absolutePath)
            prepare()
            start()
        }

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification("Kaydediliyor… (bildirimden durdur)"))
    }

    private fun stopRecording() {
        try {
            recorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (_: Exception) {}
        recorder = null

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification("Kayıt durdu"))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(content: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val piOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RecorderService::class.java).apply { action = ACTION_STOP }
        val piStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Mic Recorder")
            .setContentText(content)
            .setContentIntent(piOpen)
            .setOngoing(true)
            .addAction(0, "DURDUR", piStop)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Kayıt", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(ch)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
    }
}
