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
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ForegroundService extends Service {
    private static final String CHANNEL_ID = "ForegroundServiceChannel"; // 알림 채널 ID
    private static final String TAG = "차량 감지 로그"; // 로그 태그
    private static final int SAMPLE_RATE = 48000; // 오디오 샘플링 레이트 (48kHz)
    private static final int AUDIO_BUFFER_SIZE = SAMPLE_RATE * 2; // 오디오 버퍼 크기

    private Interpreter tflite; // TensorFlow Lite 모델 인터프리터
    private boolean isRecording = true; // 녹음 상태를 나타내는 변수
    private PowerManager.WakeLock wakeLock; // CPU를 유지하기 위한 WakeLock
    private ExecutorService executorService; // 백그라운드 작업을 실행할 ExecutorService

    @Override
    public void onCreate() {
        super.onCreate();
        WebSocketManager.connect("ws://3.39.233.144:3000/data");
        createNotificationChannel(); // 알림 채널 생성
        acquireWakeLock(); // WakeLock 획득
        initTFLite(); // TensorFlow Lite 모델 초기화
        executorService = Executors.newSingleThreadExecutor(); // 단일 스레드 ExecutorService 초기화
        Log.d(TAG, "ForegroundService 시작됨.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, createNotification()); // Foreground Service 시작 및 알림 표시
        executorService.execute(this::startAudioRecording); // 오디오 녹음 시작
        return START_STICKY; // 서비스가 중단되어도 자동으로 재시작
    }

    // CPU를 유지하기 위해 WakeLock을 획득하는 메서드
    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::AudioDetectionWakeLock");
        wakeLock.acquire(); // WakeLock 시작
        Log.d(TAG, "WakeLock 획득됨.");
    }

    // Foreground Service를 위한 알림 생성
    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("차량 감지 서비스")
                .setContentText("백그라운드에서 차량 감지를 실행 중입니다.")
                .setSmallIcon(android.R.drawable.ic_media_play) // 알림 아이콘 설정
                .setPriority(NotificationCompat.PRIORITY_LOW) // 알림 우선순위 낮음으로 설정
                .build();
    }

    // 알림 채널을 생성하는 메서드 (Android 8.0 이상)
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Audio Detection Service", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    // TensorFlow Lite 모델 초기화 메서드
    private void initTFLite() {
        try {
            FileInputStream fis = new FileInputStream(getAssets().openFd("car_detection_raw_audio_model.tflite").getFileDescriptor());
            FileChannel fileChannel = fis.getChannel();
            long startOffset = getAssets().openFd("car_detection_raw_audio_model.tflite").getStartOffset();
            long declaredLength = getAssets().openFd("car_detection_raw_audio_model.tflite").getDeclaredLength();
            ByteBuffer modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
            tflite = new Interpreter(modelBuffer); // 모델 로드
            Log.d(TAG, "TFLite 모델 초기화 성공.");
        } catch (IOException e) {
            Log.e(TAG, "TFLite 모델 초기화 실패", e);
        }
    }

    // 오디오 녹음을 시작하는 메서드
    private void startAudioRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO 권한이 없습니다. 오디오 녹음을 시작할 수 없습니다.");
            return;
        }

        executorService.execute(() -> {
            try {
                // AudioRecord 객체 초기화
                AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, AUDIO_BUFFER_SIZE);

                if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord 초기화 실패");
                    return;
                }

                short[] audioData = new short[AUDIO_BUFFER_SIZE / 2];
                recorder.startRecording(); // 오디오 녹음 시작
                Log.d(TAG, "오디오 녹음 시작됨.");

                while (isRecording) {
                    long startTime = SystemClock.elapsedRealtime(); // 레이턴시 시작 시간 기록
                    int result = recorder.read(audioData, 0, audioData.length);

                    if (result > 0) {
                        detectSound(audioData, startTime); // TensorFlow 모델을 사용하여 소리 감지
                    }
                }

                recorder.stop(); // 녹음 중지
                recorder.release(); // 리소스 해제
                Log.d(TAG, "오디오 녹음 중지됨.");
            } catch (Exception e) {
                Log.e(TAG, "오디오 녹음 중 오류 발생", e);
            }
        });
    }

    // TensorFlow Lite 모델을 사용하여 소리 감지 메서드
    private void detectSound(short[] audioData, long startTime) {
        float[][][] input = new float[1][96000][1]; // 모델 입력 데이터 초기화
        int length = Math.min(audioData.length, 96000);

        for (int i = 0; i < length; i++) {
            input[0][i][0] = audioData[i] / 32768.0f; // PCM 데이터를 정규화
        }

        float[][] output = new float[1][1];
        tflite.run(input, output); // 모델 실행

        sendDetectionResult(output[0][0]);


        boolean vehicleDetected = output[0][0] < 0.5;

        Log.d(TAG, "결과: " + vehicleDetected);
    }

    private void sendDetectionResult(float score) {

        long timestamp = System.currentTimeMillis();

        try {
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("timestamp", timestamp);
            json.put("vehicle_detected", score);
            WebSocketManager.send(json.toString());
            Log.d("WebSocketSend", json.toString());
        } catch (Exception e) {
            Log.e("WebSocketSend", "JSON 생성 실패", e);
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        isRecording = false; // 녹음 상태 중단
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release(); // WakeLock 해제
            Log.d(TAG, "WakeLock 해제됨.");
        }
        executorService.shutdownNow(); // 스레드 종료
        if (tflite != null) tflite.close(); // TensorFlow Lite 리소스 해제
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // 바인딩하지 않음
    }



}
