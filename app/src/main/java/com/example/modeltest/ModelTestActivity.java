package com.example.modeltest;

import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class ModelTestActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION_CODE = 100; // 권한 요청 코드
    private static final int SAMPLE_RATE = 48000; // 샘플링 레이트 (48kHz)
    private static final int AUDIO_BUFFER_SIZE = SAMPLE_RATE * 2; // 오디오 버퍼 크기

    private static final String CHANNEL_ID = "ForegroundServiceChannel"; // 알림 채널 ID

    private Interpreter tflite; // TensorFlow Lite 모델 해석기
    private TextView resultTextView, vehicleDetectedTextView; // UI 텍스트뷰
    private boolean isRecording = true; // 오디오 녹음 상태 변수
    private Vibrator vibrator; // 진동 기능 객체

    private static final String TAG = "차량 감지 로그"; // 로그 태그
    private long startTime; // 레이턴시 시작 시간

    // 변수 추가
    private TextView scoreTextView; // TensorFlow 수치 값 출력용

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model_test);

        TextView btnStartDetection = findViewById(R.id.btnStartDetection);
        TextView btnStopDetection = findViewById(R.id.btnStopDetection);

        // UI 컴포넌트 초기화
        resultTextView = findViewById(R.id.resultTextView);
        vehicleDetectedTextView = findViewById(R.id.vehicleDetectedView);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        scoreTextView = findViewById(R.id.scoreTextView); // TensorFlow 수치 값 출력용 추가


        // Foreground Service 시작
        startMyForegroundService();

        // RECORD_AUDIO 권한 확인 및 요청
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_PERMISSION_CODE);
        } else {
            initTFLite(); // TensorFlow Lite 모델 초기화
            startAudioRecording(); // 오디오 녹음 시작
        }

        // Start 버튼 이벤트
        btnStartDetection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRecording) {
                    isRecording = true;
                    startAudioRecording(); // 오디오 녹음 시작
                    resultTextView.setText("탐지 시작...");
                }
            }
        });

        // Stop 버튼 이벤트
        btnStopDetection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRecording) {
                    isRecording = false; // 오디오 녹음 중지
                    resultTextView.setText("탐지 중지됨");
                }
            }
        });

    }

    // Foreground Service 시작 메서드
    private void startMyForegroundService() {
        Intent serviceIntent = new Intent(this, ForegroundService.class);

        // Foreground Service 권한 확인 및 시작
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
            } catch (SecurityException e) {
                e.printStackTrace();
                resultTextView.setText("Foreground Service 실행 권한이 거부되었습니다.");
            }
        } else {
            resultTextView.setText("Foreground Service 권한이 필요합니다.");
        }
    }

    // TensorFlow Lite 모델 초기화
    private void initTFLite() {
        try {
            FileInputStream fis = new FileInputStream(
                    getAssets().openFd("car_detection_raw_audio_model.tflite").getFileDescriptor());
            FileChannel fileChannel = fis.getChannel();
            long startOffset = getAssets().openFd("car_detection_raw_audio_model.tflite").getStartOffset();
            long declaredLength = getAssets().openFd("car_detection_raw_audio_model.tflite").getDeclaredLength();
            ByteBuffer modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);

            tflite = new Interpreter(modelBuffer); // 모델 로드
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 오디오 녹음 시작 메서드
    private void startAudioRecording() {
        new Thread(() -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(() -> resultTextView.setText("Audio recording permission is not granted."));
                return;
            }

            try {
                // AudioRecord 객체 초기화
                AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, AUDIO_BUFFER_SIZE);

                short[] audioData = new short[AUDIO_BUFFER_SIZE / 2];
                recorder.startRecording(); // 오디오 녹음 시작

                while (isRecording) {
                    startTime = SystemClock.elapsedRealtime(); // 레이턴시 시작 시간 기록
                    int result = recorder.read(audioData, 0, audioData.length);
                    if (result > 0) {
                        runOnUiThread(() -> detectSound(audioData)); // 오디오 데이터 분석
                    }
                }

                recorder.stop(); // 녹음 중지
                recorder.release();
            } catch (SecurityException e) {
                e.printStackTrace();
                runOnUiThread(() -> resultTextView.setText("Permission denied to record audio."));
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> resultTextView.setText("Error starting audio recording."));
            }
        }).start();
    }

    private void detectSound(short[] audioData) {
        float[][][] input = new float[1][96000][1];
        int length = Math.min(audioData.length, 96000);

        for (int i = 0; i < length; i++) {
            input[0][i][0] = audioData[i] / 32768.0f; // 데이터 정규화;
        }

        float[][] output = new float[1][1];
        tflite.run(input, output);

        long endTime = SystemClock.elapsedRealtime(); // 레이턴시 종료 시간 기록
        long latency = endTime - startTime; // 레이턴시 계산

        float detectionValue = output[0][0]; // 모델 출력값
        boolean vehicleDetected = detectionValue < 0.5; // 감지 기준

        Log.d(TAG, "결과: " + detectionValue);
        Log.d(TAG, "레이턴시(ms): " + latency);

        runOnUiThread(() -> {
            resultTextView.setText(vehicleDetected ? "Car Detected" : "No Car Sound");
            vehicleDetectedTextView.setText("차량 감지 여부: " + (vehicleDetected ? "감지됨" : "미감지"));

            // TensorFlow 수치 값을 출력
            scoreTextView.setText(String.format("Detection Score: %.4f", detectionValue));

            // 차량 감지 시 진동
            if (vehicleDetected) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(500);
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initTFLite(); // 권한 승인 후 모델 초기화
                startAudioRecording();
            } else {
                resultTextView.setText("Audio recording permission is required.");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRecording = false; // 녹음 상태 종료
        if (tflite != null) {
            tflite.close(); // 모델 닫기
        }
    }

}