package com.example.micrecorder

import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecorderService : Service() {

    companion object {
        const val ACTION_START = "com.example.micrecorder.START"
        const val ACTION_STOP = "com.example.micrecorder.STOP"
        private const val CH_ID = "recorder_ch"
        private const val CH_NAME = "Kayıt"
        private const val NOTIF_ID = 1001
    }

    private var recorder: MediaRecorder? = null
    private var currentUri: Uri? = null

    override fun onCreate() {
        super.onCreate()
        createNotifChannel()
        startForeground(NOTIF_ID, buildNotif("Kayıt beklemede"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP  -> stopRecording()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------- KAYIT BAŞLAT ----------
    private fun startRecording() {
        // Çift başlatmayı engelle
        if (recorder != null) return

        val fileName = "BG_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .format(Date()) + ".m4a"

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }

        try {
            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // MediaStore ile Music/SesKaydediciBG altına yaz
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_MUSIC + "/SesKaydediciBG"
                        )
                        put(MediaStore.Audio.Media.IS_MUSIC, 1)
                    }
                    val uri = contentResolver.insert(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values
                    )
                    requireNotNull(uri) { "Dosya oluşturulamadı" }
                    currentUri = uri
                    val pfd = contentResolver.openFileDescriptor(uri, "w")!!
                    setOutputFile(pfd.fileDescriptor)
                    pfd.close()
                } else {
                    // Android 9 ve altı: Ortak Müzik klasörü
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_MUSIC
                        ),
                        "SesKaydediciBG"
                    )
                    if (!dir.exists()) dir.mkdirs()
                    val out = File(dir, fileName)
                    setOutputFile(out.absolutePath)
                }

                prepare()
                start()
            }

            // Bildirim metnini güncelle
            updateNotif("Kayıt devam ediyor…")
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    // ---------- KAYIT DURDUR ----------
    private fun stopRecording() {
        try {
            recorder?.apply {
                stop()
                reset()
                release()
            }
            recorder = null

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                // Eski sürümlerde medya tarayıcıya bildir
                sendBroadcast(
                    Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                        // Son kaydedilen dosyayı klasör bazında taratmak için klasör URI'si yeterli
                        data = Uri.fromFile(
                            Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_MUSIC
                            )
                        )
                    }
                )
            }

            updateNotif("Kayıt durdu")
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            stopForeground(false)
        }
    }

    // ---------- Bildirim yardımcıları ----------
    private fun createNotifChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CH_ID, CH_NAME, NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun buildNotif(text: String): Notification {
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Ses Kaydedici BG")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotif(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotif(text))
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }
}
