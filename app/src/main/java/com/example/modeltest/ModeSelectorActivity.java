// ModeSelectorActivity.java
package com.example.modeltest;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.List;

public class ModeSelectorActivity extends AppCompatActivity {
    private ViewPager2 viewPager;
    private TextView tvSelectedMode;
    private List<Mode> modes;
    private ImageView ivLeftArrow, ivRightArrow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mode_selector);

        viewPager = findViewById(R.id.viewPager);
        tvSelectedMode = findViewById(R.id.tv_selected_mode);
        ivLeftArrow = findViewById(R.id.iv_left_arrow);
        ivRightArrow = findViewById(R.id.iv_right_arrow);

        // 모드 목록 초기화
        modes = new ArrayList<>();
        modes.add(new Mode("골목길모드", R.drawable.ic_alley, "좁은 골목에서의 안전 보행 모드입니다."));
        modes.add(new Mode("공사장모드", R.drawable.ic_construction, "공사장 주변에서 경고 알림이 강화됩니다."));
        modes.add(new Mode("보행자모드", R.drawable.ic_pedestrian, "일반적인 도보 이동을 위한 모드입니다."));

        // ViewPager 어댑터 설정
        ModeAdapter adapter = new ModeAdapter(modes, this::showModeDialog);
        viewPager.setAdapter(adapter);

        // 좌우 스와이프 버튼 기능 연결
        ivLeftArrow.setOnClickListener(v -> {
            int current = viewPager.getCurrentItem();
            int previous = (current - 1 + modes.size())%modes.size(); // 순환
            viewPager.setCurrentItem(previous, true);
        });

        ivRightArrow.setOnClickListener(v -> {
            int current = viewPager.getCurrentItem();
            int next = (current + 1) % modes.size(); // 순환
            viewPager.setCurrentItem(next, true);
        });

        // ViewPager 변경 시 텍스트 갱신
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback(){
            @Override
            public void onPageSelected(int position){
                super.onPageSelected(position);
                tvSelectedMode.setText(modes.get(position).getName());
            }
        });
    }

    private void showModeDialog(Mode mode) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_mode_description, null);
        TextView description = dialogView.findViewById(R.id.tv_mode_description);
        Button confirm = dialogView.findViewById(R.id.btn_confirm);
        ImageView close = dialogView.findViewById(R.id.iv_close);

        description.setText(mode.getDescription());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        confirm.setOnClickListener(v -> {
            Intent intent = new Intent(this, MapFragmentActivity.class);
            intent.putExtra("mode", mode.getName());
            startActivity(intent);
            dialog.dismiss();
        });

        close.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}