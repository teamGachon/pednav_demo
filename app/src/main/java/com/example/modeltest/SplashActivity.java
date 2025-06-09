package com.example.modeltest;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class SplashActivity extends AppCompatActivity {

    private static final String Tag = "SplashActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);


        WebSocketManager.connect("ws://3.34.129.82:3000/data");
        Log.d("WebSocket", "🎯 SplashActivity에서 connect() 호출됨");


        startLoading();
    }

    private void startLoading(){
        Handler handler = new Handler();
        handler.postDelayed(new Runnable(){
           @Override
           public void run(){

               Log.e(Tag, "Application Running.....");
               // Splash Screen이 뜨고 나서 실행될 Activity 연결
               startActivity(new Intent(getApplicationContext(), MainActivity.class));
               finish();
           }
        }, 2000);
    }
}
