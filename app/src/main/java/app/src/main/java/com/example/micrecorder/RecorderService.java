package com.example.micrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;

/**
 * Manifest ile uyumlu servis:
 *
 * <service
 *     android:name=".RecorderService"
 *     android:exported="false"
 *     android:foregroundServiceType="microphone" />
 *
 * Gerekli izinler (Manifest'te olmalı):
 * - android.permission.RECORD_AUDIO
 * - android.permission.FOREGROUND_SERVICE
 * - (Önerilir, Android 14+) android.permission.FOREGROUND_SERVICE_MICROPHONE
 */
public class RecorderService extends Service {

    // Dışarıdan kullanım için sabitler
    public static final String EXTRA_OUTPUT_PATH = "outputPath";
    public static final String EXTRA_SOURCE_MODE = "sourceMode"; // "mic" | "vr"
    public static final String ACTION_START = "com.example.micrecorder.action.START";
    public static final String ACTION_STOP  = "com.example.micrecorder.action.STOP";

    // Bildirim
    private static final String CHANNEL_ID = "record_channel_id";
    private static final int NOTIF_ID = 1001;

    private MediaRecorder recorder;

    /**
     * Kolay başlatma yardımcıları (MainActivity'den çağırmak için)
     */
    public static void start(Context ctx, String outputPath, boolean useVoiceRecognition) {
        Intent i = new Intent(ctx, RecorderService.class);
        i.setAction(ACTION_START);
        i.putExtra(EXTRA_OUTPUT_PATH, outputPath);
        i.putExtra(EXTRA_SOURCE_MODE, useVoiceRecognition ? "vr" : "mic");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }

    public static void stop(Context ctx) {
        Intent i = new Intent(ctx, RecorderService.class);
        i.setAction(ACTION_STOP);
        ctx.startService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannelIfNeeded(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String outputPath = intent != null ? intent.getStringExtra(EXTRA_OUTPUT_PATH) : null;
        String sourceMode = intent != null ? intent.getStringExtra(EXTRA_SOURCE_MODE) : "mic";

        // Servisi foreground olarak başlat (bildirim zorunlu)
        Notification notification = buildRecordingNotification();
        startForeground(NOTIF_ID, notification);

        // Kayıt başlat
        startRecording(outputPath, "vr".equalsIgnoreCase(sourceMode));

        // Servis öldürülürse yeniden başlatmayı dene
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // Bound service değil
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRecording();
    }

    // ----- Kayıt Mantığı -----

    private void startRecording(@Nullable String outputPath, boolean useVoiceRecognition) {
        stopRecording(); // varsa eski kaydı kapat

        recorder = new MediaRecorder();

        // Bazı cihazlarda ekran kilidinde MIC sessiz kalabiliyor; VOICE_RECOGNITION daha stabil olabilir
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            recorder.setAudioSource(useVoiceRecognition
                    ? MediaRecorder.AudioSource.VOICE_RECOGNITION
                    : MediaRecorder.AudioSource.MIC);
        } else {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        }

        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        // Uyumlu ve yeterli kalite: 44.1kHz, mono, ~96kbps
        recorder.setAudioSamplingRate(44100);
        recorder.setAudioEncodingBitRate(96000);
        recorder.setAudioChannels(1);

        if (outputPath != null) {
            recorder.setOutputFile(outputPath);
        }

        try {
            recorder.prepare();
            recorder.start();
        } catch (IOException | IllegalStateException e) {
            e.printStackTrace();
            // Hata olursa servisi kapat
            stopSelf();
        }
    }

    private void stopRecording() {
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (Exception ignored) {
            }
            try {
                recorder.reset();
                recorder.release();
            } catch (Exception ignored) {
            }
            recorder = null;
        }
    }

    // ----- Bildirim / Kanal -----

    private Notification buildRecordingNotification() {
        // Bildirime tıklandığında uygulamayı açmak istersen:
        PendingIntent contentIntent = null;
        try {
            Intent openApp = new Intent(this, MainActivity.class);
            openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    : PendingIntent.FLAG_UPDATE_CURRENT;
            contentIntent = PendingIntent.getActivity(this, 0, openApp, flags);
        } catch (Exception ignored) {}

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Kayıt yapılıyor")
                .setContentText("Ekran kilitli olsa da kayıt devam ediyor")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true);

        if (contentIntent != null) b.setContentIntent(contentIntent);

        // Durdurma aksiyonu (bildirimden durdurmak istersen)
        Intent stopI = new Intent(this, RecorderService.class).setAction(ACTION_STOP);
        int pFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent stopPI = PendingIntent.getService(this, 1, stopI, pFlags);
        b.addAction(0, "Durdur", stopPI);

        return b.build();
    }

    private static void createNotificationChannelIfNeeded(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "Kayıt Kanalı",
                    NotificationManager.IMPORTANCE_LOW
            );
            ch.setDescription("Arka plan ses kaydı için bildirim kanalı");
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(ch);
        }
    }
}
