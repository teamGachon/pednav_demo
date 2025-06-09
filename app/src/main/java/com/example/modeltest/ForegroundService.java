package com.example.modeltest;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.*;
import android.os.*;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.*;
import androidx.core.content.ContextCompat;

import org.tensorflow.lite.Interpreter;
import org.json.JSONObject;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.concurrent.*;
import okhttp3.*;

public class ForegroundService extends Service {

    private static final String CHANNEL_ID = "ForegroundServiceChannel";
    private static final String TAG = "ForegroundService";
    private static final int SAMPLE_RATE = 48000;
    private static final int AUDIO_BUFFER_SIZE = SAMPLE_RATE * 2;

    private Interpreter tflite;
    private boolean isRecording = true;
    private ExecutorService executorService;
    private PowerManager.WakeLock wakeLock;
    private MetricSender metricSender;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("MetricDebug", "🟡 ForegroundService onCreate 시작됨");


        metricSender = new MetricSender(this);
        Log.d("MetricSender", "🟡 startSendingMetrics() 호출 직전");
        metricSender.startSendingMetrics();
        Log.d("MetricSender", "🟢 startSendingMetrics() 호출됨");

        new Handler(getMainLooper()).postDelayed(() ->
                WebSocketManager.connect("ws://3.34.129.82:3000/data"), 500);

        createNotificationChannel();
        acquireWakeLock();
        initTFLite();
        executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, createNotification());
        executorService.execute(this::startAudioRecording);
        return START_STICKY;
    }

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::WakeLock");
        wakeLock.acquire();
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("차량 감지 실행 중")
                .setContentText("소리 감지 및 데이터 전송 중")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Detection Channel", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void initTFLite() {
        try {
            AssetFileDescriptor fd = getAssets().openFd("car_detection_raw_audio_model.tflite");
            FileInputStream fis = new FileInputStream(fd.getFileDescriptor());
            FileChannel fileChannel = fis.getChannel();
            ByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
            tflite = new Interpreter(buffer);
            Log.d(TAG, "✅ TFLite 모델 로드 완료");
        } catch (IOException e) {
            Log.e(TAG, "❌ TFLite 모델 로드 실패", e);
        }
    }

    private void detectSound(short[] audioData, long startTime) {
        float[][][] input = new float[1][96000][1];
        int length = Math.min(audioData.length, 96000);

        for (int i = 0; i < length; i++) {
            input[0][i][0] = audioData[i] / 32768.0f;
        }

        float[][] output = new float[1][1];
        try {
            tflite.run(input, output);
            float score = output[0][0];
            Log.d(TAG, "📡 detectSound() → score: " + score);

            handleCase(score); // ✅ 이걸 빠뜨리면 아무 행동도 안 함!
        } catch (Exception e) {
            Log.e(TAG, "❌ TFLite 감지 실패", e);
        }
    }



    private void startAudioRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "🚫 RECORD_AUDIO 권한이 없습니다. 오디오 녹음을 시작할 수 없습니다.");
            return;
        }

        executorService.execute(() -> {
            AudioRecord recorder = null;

            try {
                recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        AUDIO_BUFFER_SIZE);

                if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord 초기화 실패");
                    return;
                }

                short[] audioData = new short[AUDIO_BUFFER_SIZE / 2];
                recorder.startRecording(); // 🔴 녹음 시작
                Log.d(TAG, "🎙️ 오디오 녹음 시작됨.");

                while (isRecording) {
                    long startTime = SystemClock.elapsedRealtime();
                    int result = recorder.read(audioData, 0, audioData.length);

                    if (result > 0) {
                        detectSound(audioData, startTime); // 🔁 감지 호출
                    }
                }

            } catch (SecurityException se) {
                Log.e(TAG, "🚨 권한 오류 발생", se);
            } catch (Exception e) {
                Log.e(TAG, "❌ 오디오 녹음 중 오류 발생", e);
            } finally {
                if (recorder != null) {
                    try {
                        recorder.stop();
                        recorder.release();
                        Log.d(TAG, "🛑 오디오 녹음 종료됨.");
                    } catch (Exception e) {
                        Log.e(TAG, "⚠️ 녹음 중지 중 예외 발생", e);
                    }
                }
            }
        });
    }



    private void handleCase(float score) {
        int currentCase = WebSocketManager.getCurrentCase();
        switch (currentCase) {
            case 1:
            case 3:
                sendSoundDetected(score);
                break;
            case 2:
            case 4:
                byte[] pcm = recordPcm();
                uploadPcm(pcm, currentCase);
                break;
            default:
                Log.w(TAG, "❓ 알 수 없는 CASE: " + currentCase);
        }
    }

    private void sendSoundDetected(float score) {
        try {
            JSONObject json = new JSONObject();
            json.put("timestamp", System.currentTimeMillis());
            json.put("sound_detected", score);

            WebSocketManager.send(json.toString());
            Log.d(TAG, "📤 CASE 1/3 - sound_detected 전송됨: " + json);
        } catch (Exception e) {
            Log.e(TAG, "❌ sound_detected 전송 실패", e);
        }
    }

    private byte[] recordPcm() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "🚫 RECORD_AUDIO 권한이 없습니다.");
            return new byte[0];
        }
        final int bufferSize = AudioRecord.getMinBufferSize(
                44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord recorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                44100,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
        );

        short[] audioBuffer = new short[44100 * 2];
        byte[] pcmData = new byte[audioBuffer.length * 2];

        recorder.startRecording();
        Log.d(TAG, "🎤 2초 PCM 녹음 시작");

        int samples = recorder.read(audioBuffer, 0, audioBuffer.length);
        for (int i = 0; i < samples; i++) {
            pcmData[i * 2] = (byte) (audioBuffer[i] & 0xFF);
            pcmData[i * 2 + 1] = (byte) ((audioBuffer[i] >> 8) & 0xFF);
        }

        recorder.stop();
        recorder.release();
        return pcmData;
    }

    private void uploadPcm(byte[] pcmData, int caseId) {
        String url = caseId == 2
                ? "http://3.34.129.82:3000/api/danger/case2"
                : "http://3.34.129.82:3000/api/danger/case4";

        OkHttpClient client = new OkHttpClient();
        RequestBody audioBody = RequestBody.create(pcmData, MediaType.parse("audio/pcm"));

        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("radar_detected", "0.91")  // 임시 하드코딩
                .addFormDataPart("audio_file", "audio.pcm", audioBody)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                Log.e(TAG, "❌ PCM 업로드 실패", e);
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                Log.d(TAG, "✅ 서버 응답: " + response.body().string());
            }
        });
    }

    @Override
    public void onDestroy() {
        isRecording = false;
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (executorService != null) executorService.shutdownNow();
        if (tflite != null) tflite.close();
        if (metricSender != null) metricSender.stop();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
