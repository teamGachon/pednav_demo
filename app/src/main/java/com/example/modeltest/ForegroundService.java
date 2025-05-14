package com.example.modeltest;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ForegroundService extends Service {
    private static final String CHANNEL_ID = "ForegroundServiceChannel";
    private static final String TAG = "차량 감지 로그";
    private static final int SAMPLE_RATE = 44100;
    private YamnetClassifier yamnet;
    private boolean isRecording = true;
    private PowerManager.WakeLock wakeLock;
    private ExecutorService executorService;

    @Override
    public void onCreate() {
        super.onCreate();

        new Handler(getMainLooper()).postDelayed(() -> {
            WebSocketManager.connect("ws://3.39.233.144:3000/data");
        }, 500);

        createNotificationChannel();
        acquireWakeLock();
        initTFLite();
        executorService = Executors.newSingleThreadExecutor();
        Log.d(TAG, "ForegroundService 시작됨.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, createNotification());
        executorService.execute(this::startAudioRecording);
        return START_STICKY;
    }

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::AudioDetectionWakeLock");
        wakeLock.acquire();
        Log.d(TAG, "WakeLock 획득됨.");
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("차량 감지 서비스")
                .setContentText("백그라운드에서 차량 감지를 실행 중입니다.")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Audio Detection Service", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void initTFLite() {
        try {
            yamnet = new YamnetClassifier(getApplicationContext());
            Log.d(TAG, "Yamnet 모델 초기화 성공.");
        } catch (Exception e) {
            Log.e(TAG, "Yamnet 모델 초기화 실패", e);
        }
    }

    private void startAudioRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO 권한이 없습니다.");
            return;
        }

        executorService.execute(() -> {
            try {
                int blockSize = (int) (0.96 * SAMPLE_RATE);
                AudioRecord recorder = new AudioRecord.Builder()
                        .setAudioSource(MediaRecorder.AudioSource.MIC)
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                .build())
                        .setBufferSizeInBytes(blockSize * Float.BYTES)
                        .build();

                float[] buffer = new float[blockSize];
                recorder.startRecording();
                Log.d(TAG, "오디오 녹음 시작됨.");

                while (isRecording) {
                    int read = recorder.read(buffer, 0, blockSize, AudioRecord.READ_BLOCKING);
                    if (read > 0) {
                        detectSound(buffer);
                    }
                }

                recorder.stop();
                recorder.release();
                Log.d(TAG, "오디오 녹음 중지됨.");
            } catch (Exception e) {
                Log.e(TAG, "오디오 녹음 오류", e);
            }
        });
    }

    private void detectSound(float[] audioData) {
        float[] clipProb = yamnet.runInference(audioData);

        float vehicleProb = 0f;
        for (int i = 300; i <= 321; i++) {
            vehicleProb += clipProb[i];
        }

        boolean isVehicle = vehicleProb > (22f / 521f);
        sendDetectionResult(vehicleProb);
        Log.d(TAG, "🚘 차량 감지 결과: " + vehicleProb + " → " + isVehicle);
    }

    private void sendDetectionResult(float score) {
        long timestamp = SystemClock.elapsedRealtime();
        try {
            JSONObject json = new JSONObject();
            json.put("timestamp", timestamp);
            json.put("vehicle_detected", score);

            WebSocketManager.onReady(() -> {
                WebSocketManager.send(json.toString());
                Log.d("WebSocketSend", "🚀 전송: " + json);
            });
        } catch (Exception e) {
            Log.e("WebSocketSend", "JSON 생성 실패", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRecording = false;
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "WakeLock 해제됨.");
        }
        executorService.shutdownNow();
        if (yamnet != null) yamnet.close();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
