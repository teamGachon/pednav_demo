package com.example.modeltest;

import android.content.Context;
import android.os.BatteryManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONObject;

import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

public class MetricSender {

    private static final String TAG = "📡 MetricSender";
    private final Context context;
    private Timer timer;

    public MetricSender(Context context) {
        this.context = context;
    }

    public void startSendingMetrics() {
        Log.d(TAG, "🟢 MetricSender.startSendingMetrics() 호출됨");
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "🟢 TimerTask 실행됨");  // ← 여기가 제일 중요
                sendMetrics();
            }
        }, 0, 3000);  // 3초 주기
    }

    public void stop() {
        if (timer != null) {
            timer.cancel();
        }
    }

    private void sendMetrics() {
        Log.d(TAG, "📡 sendMetrics() 호출됨");
        try {
            double cpu = getCpuUsage();
            double battery = getBatteryLevel();
            double latency = measureLatency();

            JSONObject json = new JSONObject();
            json.put("timestamp", System.currentTimeMillis());
            json.put("deviceId", "android-001");
            json.put("deviceType", "ANDROID");
            json.put("cpuLoad", cpu);
            json.put("batteryLevel", battery);
            json.put("latency", latency);

            WebSocketManager.send(json.toString());
            Log.d(TAG, "✅ 메트릭 전송됨: " + json.toString());

        } catch (Exception e) {
            Log.e(TAG, "❌ 메트릭 전송 실패", e);
        }
    }

    private double getCpuUsage() {
        // Android 10 이상에서 /proc/stat 접근 제한되므로 임의의 난수로 대체
        double randomCpuLoad = Math.random();  // 0.0 ~ 1.0 사이의 난수
        Log.d(TAG, "⚠️ /proc/stat 접근 불가로 난수 CPU 사용률 반환: " + randomCpuLoad);
        return randomCpuLoad;
    }

    private double getBatteryLevel() {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int percent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            return percent / 100.0;
        }
        return 0.5;
    }

    private double measureLatency() {
        try (Socket socket = new Socket()) {
            long start = System.currentTimeMillis();
            socket.connect(new InetSocketAddress("3.34.129.82", 3000), 1000);  // WebSocket 주소로 ping
            long end = System.currentTimeMillis();
            return (end - start);
        } catch (Exception e) {
            Log.e(TAG, "Latency 측정 실패", e);
            return 999;
        }
    }
}
