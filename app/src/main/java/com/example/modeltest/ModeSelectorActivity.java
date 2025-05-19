package com.example.modeltest;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.List;

public class ModeSelectorActivity extends AppCompatActivity {
    private ViewPager2 viewPager;
    private TextView tvSelectedMode;
    private List<Mode> modes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mode_selector);

        viewPager = findViewById(R.id.viewPager);
        tvSelectedMode = findViewById(R.id.tv_selected_mode);

        // 모드 목록 초기화
        modes = new ArrayList<>();
        modes.add(new Mode("골목길모드", R.drawable.ic_alley));
        modes.add(new Mode("공사장모드", R.drawable.ic_construction));
        modes.add(new Mode("보행자모드", R.drawable.ic_pedestrian));

        // 어댑터 설정
        ModeAdapter adapter = new ModeAdapter(modes);
        viewPager.setAdapter(adapter);

        // 선택 이벤트
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                String selectedMode = modes.get(position).getName();
                tvSelectedMode.setText(selectedMode);

//                // 보행자모드면 NavigationActivity로 이동
//                if (selectedMode.equals("보행자모드")) {
//                    Intent intent = new Intent(ModeSelectorActivity.this, NavigationActivity.class);
//                    startActivity(intent);
//                }
            }
        });
    }
}
