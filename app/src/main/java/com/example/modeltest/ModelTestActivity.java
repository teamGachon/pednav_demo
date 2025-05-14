package com.example.modeltest;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

public class ModelTestActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION_CODE = 100;
    private static final String TAG = "차량 감지 로그";

    private TextView resultTextView, vehicleDetectedTextView, scoreTextView;
    private boolean isRecording = false;
    private Vibrator vibrator;

    private YamnetClassifier yamnet;
    private AudioCapture audioCapture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model_test);

        TextView btnStartDetection = findViewById(R.id.btnStartDetection);
        TextView btnStopDetection = findViewById(R.id.btnStopDetection);
        resultTextView = findViewById(R.id.resultTextView);
        vehicleDetectedTextView = findViewById(R.id.vehicleDetectedView);
        scoreTextView = findViewById(R.id.scoreTextView);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        startMyForegroundService();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_PERMISSION_CODE);
        } else {
            initYamnet();
        }

        btnStartDetection.setOnClickListener(v -> {
            if (!isRecording) {
                isRecording = true;
                startAudioCapture();
                resultTextView.setText("탐지 시작...");
            }
        });

        btnStopDetection.setOnClickListener(v -> {
            if (isRecording) {
                isRecording = false;
                if (audioCapture != null) audioCapture.stop();
                resultTextView.setText("탐지 중지됨");
            }
        });
    }

    private void initYamnet() {
        try {
            yamnet = new YamnetClassifier(this);
            startAudioCapture();
        } catch (Exception e) {
            Log.e(TAG, "YAMNet 모델 초기화 실패", e);
            Toast.makeText(this, "모델 로드 실패", Toast.LENGTH_SHORT).show();
        }
    }

    private void startAudioCapture() {
        audioCapture = new AudioCapture();
        audioCapture.start(audioData -> {
            final float[] copiedData = audioData.clone(); // 명시적 final 변수
            runOnUiThread(() -> detectSound(copiedData));
        });
    }

    private void detectSound(float[] audioData) {
        float[] probs = yamnet.runInference(audioData);

        int[] vehicleIndices = java.util.stream.IntStream.range(300, 322).toArray();
        float vehicleProbSum = 0f;
        for (int idx : vehicleIndices) {
            vehicleProbSum += probs[idx];
        }

        final float vehicleProb = vehicleProbSum; // ✅ final로 복사
        float threshold = 22f / 521f;
        boolean vehicleDetected = vehicleProb > threshold;

        long timestamp = SystemClock.elapsedRealtime();
        WebSocketManager.onReady(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("timestamp", timestamp);
                json.put("vehicle_detected", vehicleProb); // ✅ 이제 오류 없음
                WebSocketManager.send(json.toString());
            } catch (Exception e) {
                Log.e(TAG, "WebSocket JSON 전송 실패", e);
            }
        });

        resultTextView.setText(vehicleDetected ? "Car Detected" : "No Car Sound");
        vehicleDetectedTextView.setText("차량 감지 여부: " + (vehicleDetected ? "감지됨" : "미감지"));
        scoreTextView.setText(String.format("Detection Score: %.4f", vehicleProb));

        if (vehicleDetected) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(500);
            }
        }
    }

    private void startMyForegroundService() {
        Intent serviceIntent = new Intent(this, ForegroundService.class);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initYamnet();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRecording = false;
        if (audioCapture != null) audioCapture.stop();
    }
}