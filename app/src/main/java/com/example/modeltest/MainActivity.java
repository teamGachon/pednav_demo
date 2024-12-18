package com.example.modeltest;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMapSdk;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        NaverMapSdk.getInstance(this).setClient(
                new NaverMapSdk.NaverCloudPlatformClient("dexbyijg2d")
        );

        setContentView(R.layout.activity_main);

        // Navigate to Navigation Test Activity
        TextView btnNavigation = findViewById(R.id.btnNavigation);
        btnNavigation.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                Intent intent = new Intent(MainActivity.this, MapFragmentActivity.class);
                startActivity(intent);
            }
        });

        // Navigate to Detection Test Activity
        TextView btnVehicleDetection = findViewById(R.id.btnVehicleDetection);
        btnVehicleDetection.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                Intent intent = new Intent(MainActivity.this, ModelTestActivity.class);
                startActivity(intent);
            }
        });


        // Navigate to Setting Test Activity
        TextView btnSettings = findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                Intent intent = new Intent(MainActivity.this, SettingActivity.class);
                startActivity(intent);
            }
        });
    }
}