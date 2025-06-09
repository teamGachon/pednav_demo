package com.example.modeltest;

import android.util.Log;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class WebSocketManager {
    private static final String TAG = "WebSocket";
    private static WebSocketClient client;
    private static boolean connected = false;
    private static boolean isConnecting = false;

    private static final LinkedBlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private static final List<Runnable> onReadyCallbacks = new ArrayList<>();

    private static int currentCase = 1;  // 기본값: CASE 1

    public static synchronized void connect(String serverUri) {
        if (connected || isConnecting) return;
        isConnecting = true;

        client = new WebSocketClient(URI.create(serverUri)) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                connected = true;
                isConnecting = false;
                Log.d(TAG, "✅ WebSocket 연결 성공");

                // 장치 타입 전송
                send("{\"deviceType\":\"ANDROID\"}");

                // 큐 처리
                while (!messageQueue.isEmpty()) {
                    String msg = messageQueue.poll();
                    if (msg != null) client.send(msg);
                }

                // 대기 콜백 처리
                for (Runnable callback : onReadyCallbacks) {
                    callback.run();
                }
                onReadyCallbacks.clear();
            }

            @Override
            public void onMessage(String message) {
                Log.d(TAG, "📥 서버 수신: " + message);
                try {
                    JSONObject json = new JSONObject(message);
                    if (json.has("case")) {
                        currentCase = json.getInt("case");
                        Log.d(TAG, "🎯 case 업데이트: " + currentCase);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ 메시지 파싱 오류", e);
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                connected = false;
                isConnecting = false;
                Log.w(TAG, "🔌 연결 종료: " + reason + " (code: " + code + ")");
            }

            @Override
            public void onError(Exception ex) {
                connected = false;
                isConnecting = false;
                Log.e(TAG, "❌ 연결 오류", ex);
            }
        };

        try {
            client.connect();
        } catch (Exception e) {
            Log.e(TAG, "❌ WebSocket 연결 실패", e);
        }
    }

    public static void send(String message) {
        if (client != null && client.isOpen()) {
            client.send(message);
        } else {
            Log.w(TAG, "큐에 메시지 저장됨 (연결 안 됨): " + message);
            messageQueue.offer(message);
        }
    }

    public static void onReady(Runnable callback) {
        if (isConnected()) callback.run();
        else onReadyCallbacks.add(callback);
    }

    public static boolean isConnected() {
        return client != null && client.isOpen();
    }

    public static int getCurrentCase() {
        return currentCase;
    }

    public static void sendMetric(double cpu, double battery, double latency) {
        try {
            JSONObject json = new JSONObject();
            json.put("deviceId", "android-001");
            json.put("deviceType", "ANDROID");
            json.put("latency", latency);
            json.put("cpuLoad", cpu);
            json.put("batteryLevel", battery);

            send(json.toString());
            Log.d(TAG, "📤 메트릭 전송: " + json);
        } catch (Exception e) {
            Log.e(TAG, "❌ 메트릭 전송 실패", e);
        }
    }
}
