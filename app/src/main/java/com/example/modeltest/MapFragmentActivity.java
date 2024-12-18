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
import android.location.LocationListener;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
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

import java.io.FileInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.json.JSONArray;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MapFragmentActivity extends AppCompatActivity implements OnMapReadyCallback, LocationListener {

    private Handler blinkHandler = new Handler();
    private boolean isBlinking = false;
    private boolean isVehicleDetected = false; // 차량 탐지 여부

    private MapView mapView;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private FusedLocationSource locationSource;
    private NaverMap naverMap;
    private FusedLocationProviderClient fusedLocationClient;

    private View overlayView;
    private FrameLayout rootLayout;

    // Sensor 관련 멤버 변수 선언
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private float currentHeading = 0f;

    // 출발지 목적지 기능
    private EditText startPoint, endPoint;
    private Button findRouteButton;

    // 경고창 관련 변수
    private LinearLayout vehicleWarningLayout;

    private TextView warningMessage;
    private Vibrator vibrator;

    // 마커 객체
    private Marker startMarker = new Marker(); // 출발지 마커
    private Marker endMarker = new Marker();   // 목적지 마커

    // TensorFlow Lite 모델
    private Interpreter tflite;
    private boolean isRecording = false;

    private static final String NAVER_CLIENT_ID = "dexbyijg2d";
    private static final String NAVER_CLIENT_SECRET = "oWbby1gXrrhtYftuyonY71axZ3K8NrgsbwdLVu2m";
    private static final int SAMPLE_RATE = 48000;
    private static final int AUDIO_BUFFER_SIZE = SAMPLE_RATE * 2;
    private OkHttpClient client = new OkHttpClient();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_map);

        mapView = findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);

        startPoint = findViewById(R.id.start_point);
        endPoint = findViewById(R.id.end_point);
        findRouteButton = findViewById(R.id.find_route_button);

        // 센서 매니저 초기화
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);

        mapView.getMapAsync(this);
        locationSource =
                new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        findRouteButton.setOnClickListener(view -> findRoute());

        // 뷰 초기화
        rootLayout = findViewById(R.id.root_layout);
        if (rootLayout == null) {
            Log.e("MapFragmentActivity", "rootLayout is null. Check the XML layout ID.");
        }

        overlayView = findViewById(R.id.overlay_view);
        if (overlayView == null) {
            Log.e("MapFragmentActivity", "overlayView is null. Check the XML ID.");
        }


        // 경고창 초기화
        vehicleWarningLayout = findViewById(R.id.vehicle_warning_layout);
        warningMessage = findViewById(R.id.warning_message);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        initTFLite();
        startVehicleDetection();

        // 차량 감지 테스트 버튼 설정
        Button testVehicleDetectionButton = findViewById(R.id.test_vehicle_detection);
        testVehicleDetectionButton.setOnClickListener(view -> {
            isVehicleDetected = !isVehicleDetected; // 차량 탐지 여부 토글

            if (isVehicleDetected) {
                startRepeatingVibration(); // 진동 시작
                startBlinkingOverlay();    // 깜빡이기 시작
                showVehicleWarning();      // 경고창 표시

                // 차량 감지 상태 메시지
                Toast.makeText(MapFragmentActivity.this, "차량이 감지되었습니다.", Toast.LENGTH_SHORT).show();
                Log.d("Vehicle Detection", "차량이 감지되었습니다.");
            } else {
                stopRepeatingVibration(); // 진동 중지
                stopBlinkingOverlay();    // 깜빡이기 중지
                hideVehicleWarning();     // 경고창 숨김

                // 차량 감지 해제 메시지
                Toast.makeText(MapFragmentActivity.this, "차량 감지가 해제되었습니다.", Toast.LENGTH_SHORT).show();
                Log.d("Vehicle Detection", "차량 감지가 해제되었습니다.");
            }
        });

    }

    private void startVehicleDetection() {
        new Thread(() -> {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, AUDIO_BUFFER_SIZE);

            short[] audioData = new short[AUDIO_BUFFER_SIZE / 2];
            recorder.startRecording();
            isRecording = true;

            while (isRecording) {
                int result = recorder.read(audioData, 0, audioData.length);
                if (result > 0) {
                    detectSound(audioData);
                }
            }

            recorder.stop();
            recorder.release();
        }).start();
    }

    // TensorFlow Lite 모델 출력 및 차량 감지 메서드
    private void detectSound(short[] audioData) {
        float[][][] input = new float[1][96000][1];
        int length = Math.min(audioData.length, 96000);

        for (int i = 0; i < length; i++) {
            input[0][i][0] = audioData[i]; // PCM 데이터를 정규화
        }

        float[][] output = new float[1][1];
        tflite.run(input, output);

        // 모델 출력 값과 차량 감지 여부 확인
        float detectionValue = output[0][0];
        boolean vehicleDetected = detectionValue < 0.5;

        Log.d("TensorFlow Output", "소리 감지 결과: " + detectionValue);
        runOnUiThread(() -> {
            Toast.makeText(this, "소리 감지 값: " + detectionValue, Toast.LENGTH_SHORT).show();

            if (vehicleDetected) {
                if (!isVehicleDetected) {
                    isVehicleDetected = true;
                    Log.d("Vehicle Detection", "차량이 감지되었습니다.");
                    Toast.makeText(this, "차량이 감지되었습니다!", Toast.LENGTH_SHORT).show();
                    showVehicleWarning();
                    startBlinkingOverlay();
                    startRepeatingVibration();
                }
            } else {
                if (isVehicleDetected) {
                    isVehicleDetected = false;
                    Log.d("Vehicle Detection", "차량 감지가 해제되었습니다.");
                    Toast.makeText(this, "차량 감지가 해제되었습니다!", Toast.LENGTH_SHORT).show();
                    hideVehicleWarning();
                    stopBlinkingOverlay();
                    stopRepeatingVibration();
                }
            }
        });
    }

    // 반복 진동 시작
    private void startRepeatingVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            long[] pattern = {0, 500, 500}; // 0.5초 진동, 0.5초 쉬기 반복
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
        } else {
            vibrator.vibrate(new long[]{0, 500, 500}, 0); // API 26 미만
        }
    }

    // 반복 진동 중지
    private void stopRepeatingVibration() {
        vibrator.cancel();
    }

    // 화면 깜빡임 시작
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

    // 화면 깜빡임 중지
    private void stopBlinkingOverlay() {
        isBlinking = false;
        blinkHandler.removeCallbacksAndMessages(null);
        overlayView.setBackgroundColor(Color.TRANSPARENT);
    }

    // 경고창 표시
    private void showVehicleWarning() {
        vehicleWarningLayout.setVisibility(View.VISIBLE);
        warningMessage.setText("차량이 감지되었습니다!");
    }

    // 경고창 숨김
    private void hideVehicleWarning() {
        vehicleWarningLayout.setVisibility(View.GONE);
        overlayView.setBackgroundColor(Color.TRANSPARENT);
    }

    // 센서 이벤트 리스너
    private SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float azimuth = event.values[0]; // 북쪽을 기준으로 한 각도 (Heading)
            currentHeading = azimuth;

            // 바라보는 방향을 지도에 반영
            if (naverMap != null) {
                naverMap.getLocationOverlay().setBearing(currentHeading);
            }

        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // 정확도 변화에 대한 처리는 생략 가능
        }
    };

    private void initTFLite() {
        try {
            FileInputStream fis = new FileInputStream(getAssets().openFd("car_detection_raw_audio_model.tflite").getFileDescriptor());
            FileChannel fileChannel = fis.getChannel();
            long startOffset = getAssets().openFd("car_detection_raw_audio_model.tflite").getStartOffset();
            long declaredLength = getAssets().openFd("car_detection_raw_audio_model.tflite").getDeclaredLength();
            ByteBuffer modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
            tflite = new Interpreter(modelBuffer);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "TensorFlow 모델 로드 실패", Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;
        naverMap.setLocationSource(locationSource);

        // 권한 요청
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            enableMyLocation();
        }

        // 센서 리스너 등록
        sensorManager.registerListener(sensorEventListener, rotationSensor, SensorManager.SENSOR_DELAY_UI);

        Marker marker = new Marker();
        marker.setPosition(new LatLng(37.5670135, 126.9783740));
        marker.setMap(naverMap);
    }

    private void enableMyLocation() {
        naverMap.setLocationTrackingMode(LocationTrackingMode.Face);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location != null) {
                    // 현재 위치로 지도 이동
                    CameraUpdate cameraUpdate = CameraUpdate.scrollTo(new com.naver.maps.geometry.LatLng(location.getLatitude(), location.getLongitude()));
                    naverMap.moveCamera(cameraUpdate);

                    // 현재 위치 아이콘 설정
                    LocationOverlay locationOverlay = naverMap.getLocationOverlay();
                    locationOverlay.setVisible(true);
                    locationOverlay.setPosition(new com.naver.maps.geometry.LatLng(location.getLatitude(), location.getLongitude()));
                }
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState){
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation();
            }
        }
    }

    private void findRoute() {
        String startAddress = startPoint.getText().toString();
        String endAddress = endPoint.getText().toString();

        if (startAddress.isEmpty() || endAddress.isEmpty()) {
            Toast.makeText(this, "출발지와 목적지를 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        getCoordinates(startAddress, true, startLatLng -> {
            getCoordinates(endAddress, false, endLatLng -> {
                requestDirection(startLatLng, endLatLng);
            });
        });
    }

    private void getCoordinates(String address, boolean isStartPoint, OnGeocodeListener listener) {
        try {
            String encodedAddress = URLEncoder.encode(address, "UTF-8");
            String url = "https://naveropenapi.apigw.ntruss.com/map-geocode/v2/geocode?query=" + encodedAddress;

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("X-NCP-APIGW-API-KEY-ID", NAVER_CLIENT_ID)
                    .addHeader("X-NCP-APIGW-API-KEY", NAVER_CLIENT_SECRET)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(MapFragmentActivity.this, "좌표 변환 실패", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONArray addresses = json.getJSONArray("addresses");
                        if (addresses.length() > 0) {
                            JSONObject location = addresses.getJSONObject(0);
                            double lat = location.getDouble("y"); // 위도
                            double lng = location.getDouble("x"); // 경도

                            String message = "주소: " + address + "\n위도: " + lat + ", 경도: " + lng;
                            runOnUiThread(() -> {
                                Toast.makeText(MapFragmentActivity.this, message, Toast.LENGTH_LONG).show();
                                Log.d("Geocode Result", message);

                                // 마커 표시
                                LatLng position = new LatLng(lat, lng);
                                if (isStartPoint) {
                                    setMarker(startMarker, position, "출발지");
                                } else {
                                    setMarker(endMarker, position, "목적지");
                                }
                            });

                            listener.onSuccess(new LatLng(lat, lng));
                        } else {
                            runOnUiThread(() -> Toast.makeText(MapFragmentActivity.this, "주소를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private void setMarker(Marker marker, LatLng position, String caption) {
        marker.setPosition(position);       // 마커 위치 설정
        marker.setCaptionText(caption);     // 마커 설명
        marker.setMap(naverMap);            // 마커를 지도에 추가
        naverMap.moveCamera(CameraUpdate.scrollTo(position)); // 카메라 이동
    }


    private void requestDirection(LatLng start, LatLng end) {
        String url = "https://naveropenapi.apigw.ntruss.com/map-direction/v1/driving?"
                + "start=" + start.longitude + "," + start.latitude
                + "&goal=" + end.longitude + "," + end.latitude
                + "&option=traoptimal"; // 보행자 경로를 위한 옵션 설정

        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-NCP-APIGW-API-KEY-ID", NAVER_CLIENT_ID)
                .addHeader("X-NCP-APIGW-API-KEY", NAVER_CLIENT_SECRET)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(MapFragmentActivity.this, "경로 요청 실패", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray path = json.getJSONObject("route")
                            .getJSONArray("traoptimal") // 보행자 경로를 위한 키
                            .getJSONObject(0)
                            .getJSONArray("path");

                    ArrayList<LatLng> latLngList = new ArrayList<>();
                    for (int i = 0; i < path.length(); i++) {
                        JSONArray coords = path.getJSONArray(i);
                        latLngList.add(new LatLng(coords.getDouble(1), coords.getDouble(0))); // 위도, 경도 추가
                    }

                    runOnUiThread(() -> drawPath(latLngList));
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(MapFragmentActivity.this, "경로 처리 중 오류 발생", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void drawPath(ArrayList<LatLng> latLngList) {
        PathOverlay path = new PathOverlay();
        path.setCoords(latLngList); // 경로 좌표 설정
        path.setColor(Color.BLUE);  // 경로 색상 설정
        path.setMap(naverMap);      // 지도에 표시

        // 경로 시작 지점으로 카메라 이동
        if (!latLngList.isEmpty()) {
            naverMap.moveCamera(CameraUpdate.scrollTo(latLngList.get(0)));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRecording = false;
        tflite.close();
    }

    interface OnGeocodeListener {
        void onSuccess(LatLng latLng);
    }
}