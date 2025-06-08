package com.example.modeltest;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.util.function.Consumer;

public class AudioCapture {
    private static final int SAMPLE_RATE = 44100; // YAMNet 입력 샘플레이트
    private AudioRecord recorder;
    private int blockSize;
    private Thread recordingThread;

    public AudioCapture() {
        blockSize = (int) (0.96 * SAMPLE_RATE);

        int minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
        );

        try {
            recorder = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build())
                    .setBufferSizeInBytes(Math.max(minBuf, blockSize * Float.BYTES))
                    .build();
        } catch (SecurityException e) {
            Log.e("AudioCapture", "오디오 권한이 없어서 AudioRecord 생성 실패", e);
            recorder = null;
        }
    }

    public void start(Consumer<float[]> onAudioData) {
        if (recorder != null) {
            try {
                recorder.startRecording();
            } catch (SecurityException e) {
                Log.e("AudioCapture", "startRecording() 실패 - 권한 문제", e);
                return;
            }
        } else {
            Log.e("AudioCapture", "AudioRecord 초기화 실패로 녹음을 시작할 수 없음");
            return;
        }

        recordingThread = new Thread(() -> {
            float[] buffer = new float[blockSize];
            while (!Thread.interrupted()) {
                int read = recorder.read(buffer, 0, blockSize, AudioRecord.READ_BLOCKING);
                if (read > 0) {
                    onAudioData.accept(buffer.clone());
                }
            }
        });
        recordingThread.start();
    }

    public void stop() {
        if (recordingThread != null) {
            recordingThread.interrupt();
            recordingThread = null;
        }

        if (recorder != null) {
            if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop();
            }
            recorder.release();
        }
    }
}