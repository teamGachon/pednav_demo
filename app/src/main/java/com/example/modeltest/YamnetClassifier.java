package com.example.modeltest;

import android.content.Context;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.Arrays;

public class YamnetClassifier {
    private static final String TAG = "YAMNET";
    private Interpreter tflite;

    private static final int WAV16_SAMPLE_RATE = 16000;
    private static final int WAV16_BLOCK_SIZE = 15600; // 0.975초
    private static final int NUM_CLASSES = 521;

    public YamnetClassifier(Context context) throws IOException {
        MappedByteBuffer model = FileUtil.loadMappedFile(context, "yamnet.tflite");
        tflite = new Interpreter(model);

        // 입력/출력 shape 확인 로그
        int[] inputShape = tflite.getInputTensor(0).shape();
        int[] outputShape = tflite.getOutputTensor(0).shape();
        Log.d(TAG, "Input shape: " + Arrays.toString(inputShape));   // [1, 15600]
        Log.d(TAG, "Output shape: " + Arrays.toString(outputShape)); // [1, 521]
    }

    public float[] runInference(float[] wav44k) {
        // 리샘플링 44.1kHz → 16kHz
        float[] wav16 = resampleLinear(wav44k, 44100, WAV16_SAMPLE_RATE);

        // 패딩 또는 잘라내기
        if (wav16.length < WAV16_BLOCK_SIZE) {
            float[] padded = new float[WAV16_BLOCK_SIZE];
            System.arraycopy(wav16, 0, padded, 0, wav16.length);
            wav16 = padded;
        } else if (wav16.length > WAV16_BLOCK_SIZE) {
            float[] trimmed = new float[WAV16_BLOCK_SIZE];
            System.arraycopy(wav16, 0, trimmed, 0, WAV16_BLOCK_SIZE);
            wav16 = trimmed;
        }

        // 입력: [1][15600]
        float[][] input = new float[1][WAV16_BLOCK_SIZE];
        System.arraycopy(wav16, 0, input[0], 0, WAV16_BLOCK_SIZE);

        // 출력: [1][521]
        float[][] output = new float[1][NUM_CLASSES];

        try {
            tflite.run(input, output);
            return softmax(output[0]);
        } catch (Exception e) {
            Log.e(TAG, "❌ TFLite run() 실패", e);
            return new float[NUM_CLASSES]; // 실패 시 zero vector 반환
        }
    }

    // 소프트맥스 적용
    private float[] softmax(float[] logits) {
        float maxLogit = Float.NEGATIVE_INFINITY;
        for (float logit : logits) {
            if (logit > maxLogit) maxLogit = logit;
        }

        float sum = 0f;
        float[] exp = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            exp[i] = (float) Math.exp(logits[i] - maxLogit); // 안정성 향상
            sum += exp[i];
        }

        float[] probs = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            probs[i] = exp[i] / sum;
        }
        return probs;
    }

    // 44.1kHz → 16kHz 선형 보간
    private float[] resampleLinear(float[] src, int srcRate, int dstRate) {
        int dstLen = (int) (src.length * ((double) dstRate / srcRate));
        float[] dst = new float[dstLen];
        for (int i = 0; i < dstLen; i++) {
            double pos = i * ((double) srcRate / dstRate);
            int i0 = (int) Math.floor(pos);
            int i1 = Math.min(i0 + 1, src.length - 1);
            float frac = (float) (pos - i0);
            dst[i] = src[i0] * (1 - frac) + src[i1] * frac;
        }
        return dst;
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
        }
    }
}