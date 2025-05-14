package com.example.modeltest;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapView;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.PathOverlay;
import com.naver.maps.map.util.FusedLocationSource;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;
import java.util.stream.IntStream;

public class MapFragmentActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private static final int SAMPLE_RATE = 44100;
    private static final int AUDIO_BUFFER_SIZE = SAMPLE_RATE * 2;

    private MapView mapView;
    private NaverMap naverMap;
    private FusedLocationSource locationSource;
    private FusedLocationProviderClient fusedLocationClient;

    private Vibrator vibrator;
    private LinearLayout vehicleWarningLayout;
    private TextView warningMessage;
    private FrameLayout rootLayout;
    private View overlayView;

    private boolean isVehicleDetected = false;
    private boolean isBlinking = false;
    private Handler blinkHandler = new Handler();

    private YamnetClassifier yamnet;
    private AudioCapture audioCapture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_map);

        mapView = findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        warningMessage = findViewById(R.id.warning_message);
        vehicleWarningLayout = findViewById(R.id.vehicle_warning_layout);
        overlayView = findViewById(R.id.overlay_view);

        initModel();
    }

    private void initModel() {
        try {
            yamnet = new YamnetClassifier(this);
            audioCapture = new AudioCapture();

            audioCapture.start(buffer -> {
                float[] clipProb = yamnet.runInference(buffer);
                int[] vehicleIndices = IntStream.range(300, 322).toArray();
                float vehicleProb = 0f;
                for (int idx : vehicleIndices) vehicleProb += clipProb[idx];
                vehicleProb /= vehicleIndices.length;

                boolean isVehicle = vehicleProb > 0.1f;

                runOnUiThread(() -> {
                    if (isVehicle && !isVehicleDetected) {
                        isVehicleDetected = true;
                        showVehicleWarning();
                        startBlinkingOverlay();
                        startRepeatingVibration();
                    } else if (!isVehicle && isVehicleDetected) {
                        isVehicleDetected = false;
                        hideVehicleWarning();
                        stopBlinkingOverlay();
                        stopRepeatingVibration();
                    }
                });
            });
        } catch (Exception e) {
            Toast.makeText(this, "모델 초기화 실패", Toast.LENGTH_SHORT).show();
            Log.e("MapActivity", "모델 초기화 실패", e);
        }
    }

    private void showVehicleWarning() {
        vehicleWarningLayout.setVisibility(View.VISIBLE);
        warningMessage.setText("근처에 차량이 감지되었습니다. 주의하세요!");
    }

    private void hideVehicleWarning() {
        vehicleWarningLayout.setVisibility(View.GONE);
    }

    private void startBlinkingOverlay() {
        if (isBlinking) return;
        isBlinking = true;
        blinkHandler.post(new Runnable() {
            boolean visible = true;
            @Override
            public void run() {
                if (!isVehicleDetected) {
                    stopBlinkingOverlay();
                    return;
                }
                overlayView.setBackgroundColor(visible ? Color.parseColor("#88FFB3B3") : Color.TRANSPARENT);
                visible = !visible;
                blinkHandler.postDelayed(this, 500);
            }
        });
    }

    private void stopBlinkingOverlay() {
        isBlinking = false;
        blinkHandler.removeCallbacksAndMessages(null);
        overlayView.setBackgroundColor(Color.TRANSPARENT);
    }

    private void startRepeatingVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 500, 500}, 0));
        } else {
            vibrator.vibrate(new long[]{0, 500, 500}, 0);
        }
    }

    private void stopRepeatingVibration() {
        vibrator.cancel();
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;
        naverMap.setLocationSource(locationSource);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioCapture != null) audioCapture.stop();
    }
}