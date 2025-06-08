package com.example.modeltest;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.res.ColorStateList;


public class SettingActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1; // 위치 권한 요청 코드
    private static final int MICROPHONE_PERMISSION_REQUEST_CODE = 2; // 마이크 권한 요청 코드


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting_page);

        // Switches
        SwitchCompat switchGPS = findViewById(R.id.switch_gps);
        SwitchCompat switchBackgroundRun = findViewById(R.id.switch_background_run);
        SwitchCompat switchVehicleAlerm = findViewById(R.id.switch_vehicle_detection);
        SwitchCompat switchMicrophone = findViewById(R.id.switch_microphone);
        SwitchCompat switchAutoNoise = findViewById(R.id.switch_auto_noise);

        // Back Button
        ImageView btnBack = findViewById(R.id.btn_back);

        // 메인 페이지로 되돌아가기
        btnBack.setOnClickListener(v ->{
            Intent intent = new Intent(this, MapFragmentActivity.class);
            startActivity(intent);
        });

        // Listener for toggling states
        setToggleColor(switchGPS);
        setToggleColor(switchBackgroundRun);
        setToggleColor(switchAutoNoise);
        setToggleColor(switchMicrophone);
        setToggleColor(switchVehicleAlerm);

        // 위치 권한이 이미 허용되어 있는지 확인하고, 허용된 경우 switchGPS를 체크 상태로 설정
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            switchGPS.setChecked(true); // 권한이 허용되어 있으면 switchGPS를 체크 상태로 설정
        }

        // 마이크 권한이 이미 허용되어 있는지 확인하고, 허용된 경우 switchMicrophone을 체크 상태로 설정
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            switchMicrophone.setChecked(true); // 권한이 허용되어 있으면 switchMicrophone을 체크 상태로 설정
        }

        // switchGPS 상태에 따라 위치 권한 요청/해제 처리
        switchGPS.setOnCheckedChangeListener((buttonView, isChecked) -> {

            if (isChecked) {
                switchGPS.setThumbTintList(ColorStateList.valueOf(getResources().getColor(R.color.green)));
                switchGPS.setTrackTintList(ColorStateList.valueOf(getResources().getColor(R.color.green)));
            } else {
                switchGPS.setThumbTintList(ColorStateList.valueOf(getResources().getColor(R.color.gray)));
                switchGPS.setTrackTintList(ColorStateList.valueOf(getResources().getColor(R.color.gray)));
            }

            if (isChecked) {
                // GPS가 켜지면 위치 권한 요청
                checkLocationPermission();
            } else {
                // GPS가 꺼지면 권한을 해제할 수 없지만, 사용자가 설정 화면으로 안내
                guideToPermissionSettings();
            }
        });

        switchBackgroundRun.setOnCheckedChangeListener((buttonView, isChecked) -> {

            if (isChecked) {
                switchBackgroundRun.setThumbTintList(ColorStateList.valueOf(getResources().getColor(R.color.green)));
                switchBackgroundRun.setTrackTintList(ColorStateList.valueOf(getResources().getColor(R.color.green)));
            } else {
                switchBackgroundRun.setThumbTintList(ColorStateList.valueOf(getResources().getColor(R.color.gray)));
                switchBackgroundRun.setTrackTintList(ColorStateList.valueOf(getResources().getColor(R.color.gray)));
            }

            if (isChecked) {
                Toast.makeText(this, "백그라운드 실행이 허용되었습니다.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "백그라운드 실행이 취소되었습니다.", Toast.LENGTH_SHORT).show();
            }
        });

        switchMicrophone.setOnCheckedChangeListener((buttonView, isChecked) -> {

            if (isChecked) {
                switchMicrophone.setThumbTintList(ColorStateList.valueOf(getResources().getColor(R.color.green)));
                switchMicrophone.setTrackTintList(ColorStateList.valueOf(getResources().getColor(R.color.green)));
            } else {
                switchMicrophone.setThumbTintList(ColorStateList.valueOf(getResources().getColor(R.color.gray)));
                switchMicrophone.setTrackTintList(ColorStateList.valueOf(getResources().getColor(R.color.gray)));
            }

            if (isChecked) {
                requestMicrophonePermission();
                Toast.makeText(this, "마이크 기능이 허용되었습니다", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "마이크 권한이 해제되었습니다. 권한을 다시 요청하려면 앱을 재시작해야 합니다.", Toast.LENGTH_SHORT).show();
            }
        });

        switchVehicleAlerm.setOnCheckedChangeListener((buttonView, isChecked) -> {

            if (isChecked) {
                switchVehicleAlerm.setThumbTintList(ColorStateList.valueOf(getResources().getColor(R.color.green)));
                switchVehicleAlerm.setTrackTintList(ColorStateList.valueOf(getResources().getColor(R.color.green)));
            } else {
                switchVehicleAlerm.setThumbTintList(ColorStateList.valueOf(getResources().getColor(R.color.gray)));
                switchVehicleAlerm.setTrackTintList(ColorStateList.valueOf(getResources().getColor(R.color.gray)));
            }

            if (isChecked) {
                Toast.makeText(this, "소리 알람 기능이 허용되었습니다", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "소리 알람 기능이 해제되었습니다.", Toast.LENGTH_SHORT).show();
            }
        });

        switchAutoNoise.setOnCheckedChangeListener((buttonView, isChecked) -> {

            if (isChecked) {
                switchAutoNoise.setThumbTintList(ColorStateList.valueOf(getResources().getColor(R.color.green)));
                switchAutoNoise.setTrackTintList(ColorStateList.valueOf(getResources().getColor(R.color.green)));
            } else {
                switchAutoNoise.setThumbTintList(ColorStateList.valueOf(getResources().getColor(R.color.gray)));
                switchAutoNoise.setTrackTintList(ColorStateList.valueOf(getResources().getColor(R.color.gray)));
            }

            if (isChecked) {
                Toast.makeText(this, "노이즈캔슬링 기능이 허용되었습니다", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "노이즈캔슬링 기능이 취소되었습니다.", Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void setToggleColor(SwitchCompat switchToggle) {
        switchToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                switchToggle.setThumbTintList(getColorStateList(R.color.green));
                switchToggle.setTrackTintList(getColorStateList(R.color.green));
            } else {
                switchToggle.setThumbTintList(getColorStateList(R.color.gray));
                switchToggle.setTrackTintList(getColorStateList(R.color.gray));
            }
        });
    }


    // 위치 권한 확인
    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // 권한이 없으면 요청
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            // 권한이 이미 허용된 경우
            Toast.makeText(this, "위치 권한이 이미 허용되었습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            // 권한이 없으면 요청
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO},
                    MICROPHONE_PERMISSION_REQUEST_CODE);
        } else {
            // 권한이 이미 허용된 경우
            Toast.makeText(this, "마이크 권한이 이미 허용되었습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void handlePermissionGranted(int requestCode) {
        switch (requestCode) {
            case LOCATION_PERMISSION_REQUEST_CODE:
                Toast.makeText(this, "위치 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show();
                break;

            case MICROPHONE_PERMISSION_REQUEST_CODE:
                Toast.makeText(this, "마이크 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void handlePermissionDenied(int requestCode) {
        switch (requestCode) {
            case LOCATION_PERMISSION_REQUEST_CODE:
                Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
                break;

            case MICROPHONE_PERMISSION_REQUEST_CODE:
                Toast.makeText(this, "마이크 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // 권한이 허용된 경우 처리
            handlePermissionGranted(requestCode);
        } else {
            // 권한이 거부된 경우 처리
            handlePermissionDenied(requestCode);
        }
    }

    // 사용자가 GPS 권한을 해제하도록 설정 화면으로 안내
    private void guideToPermissionSettings() {
        Toast.makeText(this, "위치 권한을 해제하려면 설정에서 변경하세요.", Toast.LENGTH_LONG).show();
        // 설정 화면으로 이동
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(android.net.Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }
}
