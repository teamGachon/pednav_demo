package com.example.modeltest;

import android.util.Log;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

public class WebSocketManager {
    private static WebSocketClient client;

    public static void connect(String serverUri) {
        URI uri = URI.create(serverUri);
        client = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                Log.d("WebSocket", "Connected");
            }

            @Override
            public void onMessage(String message) {
                Log.d("WebSocket", "Message received: " + message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                Log.d("WebSocket", "Closed: " + reason);
            }

            @Override
            public void onError(Exception ex) {
                Log.e("WebSocket", "Error", ex);
            }
        };
        client.connect();
    }

    public static void send(String message) {
        if (client != null && client.isOpen()) {
            client.send(message);
        } else {
            Log.w("WebSocket", "Not connected");
        }
    }
}
