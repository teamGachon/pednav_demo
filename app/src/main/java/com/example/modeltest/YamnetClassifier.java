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

    // YAMNet 기본 입력: 0.975초 × 16kHz = 15600 샘플
    private static final int WAV16_SAMPLE_RATE = 16000;
    private static final int WAV16_BLOCK_SIZE = 15600;
    private static final int NUM_CLASSES = 521;

    public YamnetClassifier(Context context) throws IOException {
        MappedByteBuffer model = FileUtil.loadMappedFile(context, "yamnet.tflite");
        tflite = new Interpreter(model);

        // 모델 입력 shape 확인
        int[] inputShape = tflite.getInputTensor(0).shape();
        int[] outputShape = tflite.getOutputTensor(0).shape();
        Log.d("YAMNET", "Input shape: " + Arrays.toString(inputShape));
        Log.d("YAMNET", "Output shape: " + Arrays.toString(outputShape));
    }

    public float[] runInference(float[] wav44k) {
        float[] wav16 = resampleLinear(wav44k, 44100, WAV16_SAMPLE_RATE);

        if (wav16.length < WAV16_BLOCK_SIZE) {
            float[] padded = new float[WAV16_BLOCK_SIZE];
            System.arraycopy(wav16, 0, padded, 0, wav16.length);
            wav16 = padded;
        } else if (wav16.length > WAV16_BLOCK_SIZE) {
            float[] trimmed = new float[WAV16_BLOCK_SIZE];
            System.arraycopy(wav16, 0, trimmed, 0, WAV16_BLOCK_SIZE);
            wav16 = trimmed;
        }

        float[][] input = new float[1][WAV16_BLOCK_SIZE];
        System.arraycopy(wav16, 0, input[0], 0, WAV16_BLOCK_SIZE);

        float[][] output = new float[1][NUM_CLASSES];  // ✅ 모델 출력은 [1, 521]

        try {
            tflite.run(input, output);
            return output[0];  // ✅ [1, 521] 중 첫 번째 행 반환
        } catch (Exception e) {
            Log.e(TAG, "❌ TFLite run() 실패", e);
            return new float[NUM_CLASSES];
        }
    }

    // 44.1kHz → 16kHz 선형 보간 리샘플러
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
