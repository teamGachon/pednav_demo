package com.example.modeltest;

import android.util.Log;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class WebSocketManager {
    private static WebSocketClient client;
    private static boolean connected = false;
    private static final LinkedBlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private static final List<Runnable> onReadyCallbacks = new ArrayList<>();
    private static boolean isConnecting = false;

    public static synchronized void connect(String serverUri) {
        Log.d("WebSocket", "connect() 진입: " + serverUri);

        if (connected || isConnecting) {
            Log.d("WebSocket", " Already connected or connecting...");
            return;
        }

        isConnecting = true;

        URI uri = URI.create(serverUri);
        client = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                connected = true;
                isConnecting = false;
                Log.d("WebSocket", " 연결 성공");

                while (!messageQueue.isEmpty()) {
                    try {
                        String msg = messageQueue.poll();
                        if (msg != null && client.isOpen()) {
                            client.send(msg);
                            Log.d("WebSocket", " 큐에서 전송됨: " + msg);
                        }
                    } catch (Exception e) {
                        Log.e("WebSocket", "❌ 큐 메시지 전송 실패", e);
                    }
                }

                for (Runnable callback : onReadyCallbacks) {
                    callback.run();
                }
                onReadyCallbacks.clear();
            }

            @Override
            public void onMessage(String message) {
                Log.d("WebSocket", "\uD83D\uDCE5 Received: " + message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                connected = false;
                isConnecting = false;
                Log.w("WebSocket", "❌ Closed: " + reason + " code=" + code);
            }

            @Override
            public void onError(Exception ex) {
                connected = false;
                isConnecting = false;
                Log.e("WebSocket", "❌ Error", ex);
            }
        };

        try {
            client.connect();
            Log.d("WebSocket", " Connecting to WebSocket...");
        } catch (Exception e) {
            Log.e("WebSocket", " connect() failed", e);
        }
    }

    public static void send(String message) {
        Log.d("WebSocket", " send() 호출됨: " + message);
        if (client != null && client.isOpen()) {
            Log.d("WebSocket", " 실제 전송: " + message);
            client.send(message);
        } else {
            Log.w("WebSocket", "❗ 연결 안 됨 or 닫힘. 큐에 저장: " + message);
            messageQueue.offer(message);
        }
    }

    public static void onReady(Runnable callback) {
        if (isConnected()) {
            callback.run();
        } else {
            onReadyCallbacks.add(callback);
        }
    }

    public static boolean isConnected() {
        return client != null && client.isOpen();
    }
}