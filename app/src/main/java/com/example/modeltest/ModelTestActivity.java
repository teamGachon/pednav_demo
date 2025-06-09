package com.example.modeltest;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
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
import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import okhttp3.*;

public class ModelTestActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION_CODE = 100;
    private static final int SAMPLE_RATE = 48000;
    private static final int AUDIO_BUFFER_SIZE = SAMPLE_RATE * 2;

    private Interpreter tflite;
    private boolean isRecording = true;
    private Vibrator vibrator;

    private TextView resultTextView, vehicleDetectedTextView, scoreTextView;
    private long startTime;

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

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_PERMISSION_CODE);
        } else {
            initTFLite();
            startAudioRecording();
        }

        btnStartDetection.setOnClickListener(v -> {
            if (!isRecording) {
                isRecording = true;
                startAudioRecording();
                resultTextView.setText("탐지 시작...");
            }
        });

        btnStopDetection.setOnClickListener(v -> {
            if (isRecording) {
                isRecording = false;
                resultTextView.setText("탐지 중지됨");
            }
        });
    }

    private void startMyForegroundService() {
        Intent serviceIntent = new Intent(this, ForegroundService.class);
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

    private void initTFLite() {
        try {
            FileInputStream fis = new FileInputStream(getAssets().openFd("car_detection_raw_audio_model.tflite").getFileDescriptor());
            FileChannel channel = fis.getChannel();
            long offset = getAssets().openFd("car_detection_raw_audio_model.tflite").getStartOffset();
            long length = getAssets().openFd("car_detection_raw_audio_model.tflite").getDeclaredLength();
            ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, offset, length);
            tflite = new Interpreter(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startAudioRecording() {
        new Thread(() -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(() -> resultTextView.setText("Audio recording permission not granted."));
                return;
            }

            try {
                AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, AUDIO_BUFFER_SIZE);

                short[] buffer = new short[AUDIO_BUFFER_SIZE / 2];
                recorder.startRecording();

                while (isRecording) {
                    startTime = SystemClock.elapsedRealtime();
                    int read = recorder.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        float[][][] input = new float[1][96000][1];
                        for (int i = 0; i < Math.min(read, 96000); i++) {
                            input[0][i][0] = buffer[i] / 32768.0f;
                        }

                        float[][] output = new float[1][1];
                        tflite.run(input, output);

                        float score = output[0][0];
                        handleCase(score);
                    }
                }

                recorder.stop();
                recorder.release();
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> resultTextView.setText("녹음 중 오류 발생"));
            }
        }).start();
    }

    private void handleCase(float detectionValue) {
        int currentCase = WebSocketManager.getCurrentCase();
        long timestamp = System.currentTimeMillis();

        if (currentCase == 1 || currentCase == 3) {
            try {
                JSONObject json = new JSONObject();
                json.put("timestamp", timestamp);
                json.put("sound_detected", detectionValue);
                WebSocketManager.send(json.toString());
            } catch (Exception e) {
                Log.e("WebSocket", "전송 실패", e);
            }
            updateUI(detectionValue < 0.5, detectionValue);

        } else if (currentCase == 2 || currentCase == 4) {
            new Thread(() -> {
                byte[] pcm = recordPcm();
                uploadPcmToServer(pcm, currentCase);
                updateUI(detectionValue < 0.5, detectionValue);
            }).start();
        }
    }

    private byte[] recordPcm() {
        int bufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "🚫 RECORD_AUDIO 권한이 없습니다.");
            return new byte[0];
        }
        AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                44100, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize);

        short[] buffer = new short[44100 * 2];
        byte[] pcm = new byte[buffer.length * 2];

        recorder.startRecording();
        int samples = recorder.read(buffer, 0, buffer.length);
        for (int i = 0; i < samples; i++) {
            pcm[i * 2] = (byte) (buffer[i] & 0xFF);
            pcm[i * 2 + 1] = (byte) ((buffer[i] >> 8) & 0xFF);
        }
        recorder.stop();
        recorder.release();
        return pcm;
    }

    private void uploadPcmToServer(byte[] pcm, int caseId) {
        String url = (caseId == 2)
                ? "http://3.34.129.82:3000/api/danger/case2"
                : "http://3.34.129.82:3000/api/danger/case4";

        OkHttpClient client = new OkHttpClient();
        RequestBody audioBody = RequestBody.create(pcm, MediaType.parse("audio/pcm"));
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("radar_detected", "0.91") // radar는 실제론 ESP32 전용
                .addFormDataPart("audio_file", "audio.pcm", audioBody)
                .build();

        Request request = new Request.Builder().url(url).post(body).build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                Log.e("CaseUpload", "업로드 실패", e);
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                Log.d("CaseUpload", "서버 응답: " + response.body().string());
            }
        });
    }

    private void updateUI(boolean detected, float score) {
        runOnUiThread(() -> {
            resultTextView.setText(detected ? "Car Detected" : "No Car Sound");
            vehicleDetectedTextView.setText("차량 감지 여부: " + (detected ? "감지됨" : "미감지"));
            scoreTextView.setText(String.format("Detection Score: %.4f", score));

            if (detected) {
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
                initTFLite();
                startAudioRecording();
            } else {
                resultTextView.setText("Audio recording permission is required.");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRecording = false;
        if (tflite != null) tflite.close();
    }
}
