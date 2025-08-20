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

    @Override public void onCreate() { super.onCreate(); createChannel(this); }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
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

    @Override public void onDestroy() { super.onDestroy(); stopRecording(); }

    private void startRecording(@Nullable String outputPath, boolean useVoiceRecognition) {
        stopRecording();

        recorder = new MediaRecorder();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            recorder.setAudioSource(useVoiceRecognition
                    ? MediaRecorder.AudioSource.VOICE_RECOGNITION
                    : MediaRecorder.AudioSource.MIC);
        } else {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        }

        // ✅ Evrensel çalışacak ayar: MPEG_4 + AAC (96 kbps, 44.1 kHz)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setAudioEncodingBitRate(96_000);   // 96 kbps
        recorder.setAudioSamplingRate(44_100);      // 44.1 kHz

        // Eğer illa .3gp uyumu istiyorsan, yukarıdakini kapatıp şunu açarsın:
        /*
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        */

        if (outputPath != null) recorder.setOutputFile(outputPath);

        try {
            recorder.prepare();
            recorder.start();
        } catch (Exception e) {
            e.printStackTrace();
            stopSelf();
        }
    }

    private void stopRecording() {
        if (recorder != null) {
            try { recorder.stop(); } catch (Exception ignored) {}
            try { recorder.reset(); recorder.release(); } catch (Exception ignored) {}
            recorder = null;
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
