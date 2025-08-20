package com.example.micrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class RecorderService extends Service {

    public static final String EXTRA_OUTPUT_PATH = "outputPath";
    public static final String EXTRA_SOURCE_MODE = "sourceMode"; // "mic" | "vr"
    public static final String ACTION_START = "com.example.micrecorder.action.START";
    public static final String ACTION_STOP  = "com.example.micrecorder.action.STOP";

    private static final String CHANNEL_ID = "record_channel_id";
    private static final int NOTIF_ID = 1001;

    private MediaRecorder recorder;

    public static void start(Context ctx, String outputPath, boolean useVoiceRecognition) {
        Intent i = new Intent(ctx, RecorderService.class);
        i.setAction(ACTION_START);
        i.putExtra(EXTRA_OUTPUT_PATH, outputPath);
        i.putExtra(EXTRA_SOURCE_MODE, useVoiceRecognition ? "vr" : "mic");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i);
        else ctx.startService(i);
    }

    public static void stop(Context ctx) {
        Intent i = new Intent(ctx, RecorderService.class);
        i.setAction(ACTION_STOP);
        ctx.startService(i);
    }

    @Override public void onCreate() {
        super.onCreate();
        createChannel(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 🔒 Durdurma talebi geldiyse önce kaydı düzgün finalize et
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopRecording();          // moov atomu yazılsın
            stopSelf();               // sonra servisi kapat
            return START_NOT_STICKY;
        }

        String outputPath = intent != null ? intent.getStringExtra(EXTRA_OUTPUT_PATH) : null;
        String sourceMode = intent != null ? intent.getStringExtra(EXTRA_SOURCE_MODE) : "mic";

        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Kayıt yapılıyor")
                .setContentText("Arka planda sürüyor")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build();
        startForeground(NOTIF_ID, n);

        startRecording(outputPath, "vr".equalsIgnoreCase(sourceMode));
        return START_STICKY;
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        super.onDestroy();
        stopRecording(); // güvene al
    }

    private void startRecording(@Nullable String outputPath, boolean useVoiceRecognition) {
        stopRecording(); // varsa önceki oturumu kapat

        recorder = new MediaRecorder();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            recorder.setAudioSource(useVoiceRecognition
                    ? MediaRecorder.AudioSource.VOICE_RECOGNITION
                    : MediaRecorder.AudioSource.MIC);
        } else {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        }

        // ✅ AAC/M4A — yüksek uyumluluk + kalite
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setAudioEncodingBitRate(96_000);  // 96 kbps
        recorder.setAudioSamplingRate(44_100);     // 44.1 kHz
        // (AMR_NB/3GP istersen yukarıyı yorumlayıp THREE_GPP + AMR_NB yaparsın)

        if (outputPath != null) recorder.setOutputFile(outputPath);

        try {
            recorder.prepare();
            recorder.start();
        } catch (Exception e) {
            e.printStackTrace();
            stopRecording();
            stopSelf();
        }
    }

    // 🔧 Finalize garantili durdurma
    private void stopRecording() {
        MediaRecorder r = recorder;
        recorder = null; // başka yerden erişilip state bozulmasın

        if (r != null) {
            try {
                r.stop(); // bazı cihazlarda IllegalStateException atabilir
            } catch (Throwable ignored) {
                // stop() başarısız olsa da release mutlaka denenecek
            } finally {
                try { r.reset(); }   catch (Throwable ignored) {}
                try { r.release(); } catch (Throwable ignored) {}
            }
            // moov yazımı için minik nefes
            try { android.os.SystemClock.sleep(150); } catch (Throwable ignored) {}
        }
    }

    private static void createChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Kayıt Kanalı", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(ch);
        }
    }
}
