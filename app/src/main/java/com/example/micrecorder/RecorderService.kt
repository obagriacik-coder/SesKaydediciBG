package com.example.micrecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class RecorderService : Service() {

    companion object {
        const val ACTION_START = "com.example.micrecorder.START"
        const val ACTION_STOP  = "com.example.micrecorder.STOP"
        private const val CH_ID = "recorder_ch"
        private const val CH_NAME = "Kayıt"
        private const val NOTIF_ID = 1001
    }

    private var recorder: MediaRecorder? = null
    private var lastOutputUri: Uri? = null

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

        createChannelIfNeeded()
        startForeground(NOTIF_ID, buildNotification("Kayıt başlatılıyor…"))

        val fileStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val displayName = "REC_${fileStamp}.m4a"

        try {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128_000)
            r.setAudioSamplingRate(44_100)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: MediaStore -> Music/Recordings
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Recordings")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                    put(MediaStore.Audio.Media.IS_MUSIC, 1)
                }
                val uri = contentResolver.insert(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values
                ) ?: throw IllegalStateException("MediaStore insert failed")
                lastOutputUri = uri

                val pfd = contentResolver.openFileDescriptor(uri, "w")
                    ?: throw IllegalStateException("openFileDescriptor failed")
                r.setOutputFile(pfd.fileDescriptor)
                pfd.close()
            } else {
                // Android 9 ve altı: /Music/Recordings/REC_*.m4a
                val music = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val dir = File(music, "Recordings").apply { mkdirs() }
                val out = File(dir, displayName)
                r.setOutputFile(out.absolutePath)
            }

            r.prepare()
            r.start()
            recorder = r

            updateNotification("Kayıt devam ediyor…")
        } catch (e: Exception) {
            Log.e("RecorderService", "startRecording error", e)
            cleanupFailedStart()
        }
    }

    private fun stopRecording() {
        try {
            recorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.w("RecorderService", "stopRecording warning", e)
        } finally {
            recorder = null
            // Android 10+: IS_PENDING=0 yap ki Dosyalar/Müzik uygulamalarında görünsün
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                lastOutputUri?.let { uri ->
                    try {
                        val values = ContentValues().apply {
                            put(MediaStore.Audio.Media.IS_PENDING, 0)
                        }
                        contentResolver.update(uri, values, null, null)
                    } catch (_: Exception) {}
                }
            }
            lastOutputUri = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun cleanupFailedStart() {
        // Başlatma başarısızsa oluşturulmuş kaydı sil
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            lastOutputUri?.let {
                try { contentResolver.delete(it, null, null) } catch (_: Exception) {}
            }
        }
        lastOutputUri = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CH_ID) == null) {
                val ch = NotificationChannel(CH_ID, CH_NAME, NotificationManager.IMPORTANCE_LOW)
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(R.drawable.ic_mic) // drawable/ic_mic.xml mevcut olmalı
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }
}
