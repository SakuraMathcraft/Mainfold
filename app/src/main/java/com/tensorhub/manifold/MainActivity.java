package com.tensorhub.manifold;

import static com.amap.api.maps.model.BitmapDescriptorFactory.getContext;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.maps.model.PolygonOptions;
import com.amap.api.maps.model.PolylineOptions;
import com.tensorhub.manifold.StatsActivity;
import com.tensorhub.manifold.TrackingService;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private String token = "";
    private String id = "8a978069962e2b9601964c84daf01408";
    private String schoolId = "402881ea7c39c5d5017c39d134c30395";
    private String semesterId = "8a97806a9505a31d0195276933f70506";
    private static final Map<String, String> fieldMap = new HashMap<String, String>() {{
        put("avePace", "ap");
        put("limitationsGoalsSexInfoId", "lid");
        put("systemVersion", "sv");
        put("deviceType", "dt");
        put("totalPart", "tp");
        put("uneffectiveReason", "uer");
        put("startTime", "st");
        put("paceRange", "bf");
        put("gpsMileage", "lcs");
        put("semesterId", "xq");
        put("appVersion", "app");
        put("keepTime", "kt");
        put("totalMileage", "zlc");
        put("endTime", "et");
        put("scoringType", "jf");
        put("effectiveMileage", "em");
        put("paceNumber", "bs");
        put("type", "rt");
        put("effectivePart", "ep");
        put("calorie", "kll");
    }};

    private String sha1(String val) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(val.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
    public static String getDynamicKey(String timestamp) {
        try {
            String dest = timestamp.substring(2, 5); // 等价 substr(2,3)
            String nptr = timestamp.substring(4, 8); // 等价 substr(4,4)
            String lastChar = timestamp.substring(timestamp.length() - 1);

            int destInt = Integer.parseInt(dest);
            int nptrInt = Integer.parseInt(nptr);
            int diff = Math.abs(destInt - nptrInt);
            int shiftAmount = Integer.parseInt(lastChar);

            int shiftedValue = diff << shiftAmount;

            String fixedString = "402881ea7c39c5d5017c39d143a8062b";
            String dynamicKey = shiftedValue + fixedString;

            // 保证与 PHP 一致：只取前 16 位，不补0
            return dynamicKey.length() >= 16 ? dynamicKey.substring(0, 16) : dynamicKey;
        } catch (Exception e) {
            e.printStackTrace();
            return "0000000000000000"; // fallback
        }
    }

    private String aesEncrypt(String plaintext, String key) throws Exception {
        String cipher = "AES/ECB/PKCS5Padding";
        key = key.substring(0, Math.min(key.length(), 16));


        while (key.length() < 16) {
            key += "0";
        }

        Log.d("AesDebug", "加密使用的密钥: " + key);

        Cipher cipherInstance = Cipher.getInstance(cipher);
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");

        cipherInstance.init(Cipher.ENCRYPT_MODE, keySpec);

        byte[] encrypted = cipherInstance.doFinal(plaintext.getBytes("UTF-8"));
        return Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    private boolean permissionsRequestedFromUser = false;
    private MapView mapView;
    private AMap aMap;
    private AMapLocationClient locationClient;
    private AMapLocationClientOption locationOption;
    private SensorManager sensorManager;
    private int baseStepCount = -1;
    private int realSteps = 0;
    private List<LatLng> pointList = new ArrayList<>();
    private LatLng lastLatLng = null;
    private float minDistance = 2.0f;
    private int stepCounter = 0;
    private MaterialTextView realStepCountTextView;
    private TextInputEditText stepInput; // 配合 XML 中的 TextInputLayout 使用
    private MaterialTextView areaTextView, timeTextView, stepCountTextView;
    private boolean isDarkTheme = false;
    private long startTimeMillis, endTimeMillis;
    private boolean hasShownNotificationTip() {
        return getSharedPreferences("prefs", MODE_PRIVATE)
                .getBoolean("shown_notification_tip", false);
    }
    private void markNotificationTipShown() {
        getSharedPreferences("prefs", MODE_PRIVATE)
                .edit().putBoolean("shown_notification_tip", true)
                .apply();
    }

    // 统一使用系统 Toast 风格的小提示（与“清除历史记录”相同）
    private void showPillMessage(String message, int iconRes, boolean longDuration) {
        android.widget.Toast.makeText(
                this,
                message,
                longDuration ? android.widget.Toast.LENGTH_LONG : android.widget.Toast.LENGTH_SHORT
        ).show();
    }

    private void showLongerToast(String message) {
        showPillMessage(message, R.mipmap.ic_launcher_round, true);
    }

    private void startTrackingServiceSafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean hasFine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            boolean hasFGS = checkSelfPermission(Manifest.permission.FOREGROUND_SERVICE_LOCATION) == PackageManager.PERMISSION_GRANTED;

            if (!hasFine || !hasFGS) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.FOREGROUND_SERVICE_LOCATION
                }, 201);
                return;
            }
        }

        Intent serviceIntent = new Intent(this, TrackingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    // Step Counter SensorListener
    private final SensorEventListener stepListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
                int total = (int) event.values[0];
                if (baseStepCount == -1) {
                    baseStepCount = total;
                    Log.d("StepCounter", "Base step count initialized: " + baseStepCount);
                }
                realSteps = total - baseStepCount;
                runOnUiThread(() -> realStepCountTextView.setText("真实步数：" + realSteps));
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {

                if (Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))) {
                    startTrackingServiceSafely();startLocationUpdates();
                } else {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                            .setTitle("定位权限说明")
                            .setMessage("本应用需要后台定位权限来生成您的运动轨迹，否则无法准确计算里程。")
                            .setPositiveButton("我知道了", (d, w) -> requestLocationPermission())
                            .show();
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        Boolean.TRUE.equals(result.get(Manifest.permission.POST_NOTIFICATIONS))) {
                    if (!hasShownNotificationTip()) {
                        markNotificationTipShown();
                        showMessage("通知权限已授权");
                    }
                }
            });
    private void requestLocationPermission() {
        List<String> permissionList = new ArrayList<>();
        permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissionList.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        permissionList.add(Manifest.permission.ACTIVITY_RECOGNITION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionList.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        permissionLauncher.launch(permissionList.toArray(new String[0]));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapsInitializer.updatePrivacyShow(this, true, true);
        MapsInitializer.updatePrivacyAgree(this, true);

        String userKey = getSharedPreferences("prefs", MODE_PRIVATE)
                .getString("user_amap_key", null);
        if (userKey != null && !userKey.trim().isEmpty()) {

            AMapLocationClient.setApiKey(userKey);
        } else {

            checkAmapKey();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "tracking_channel",
                    "轨迹记录通知",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("用于展示轨迹记录的后台运行状态");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
            // ✅ 补充：将导航栏也设为透明
            window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View decorView = window.getDecorView();
                // ✅ 确保同时设置 状态栏 和 导航栏 的浅色图标（API 27+ 建议）
                int flags = decorView.getSystemUiVisibility();
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
                decorView.setSystemUiVisibility(flags);
            }
        }

        setContentView(R.layout.activity_main);
        com.google.android.material.textview.MaterialTextView deviceInfoText = findViewById(R.id.deviceInfoText);
        String deviceType = Build.MODEL;
        String systemVersion = Build.VERSION.RELEASE;
        deviceInfoText.setText("设备：" + deviceType + " · Android " + systemVersion);

        // Initialize sensor manager and register for step counter sensor
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (stepSensor != null) {
            sensorManager.registerListener(stepListener, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            showPillMessage("当前设备不支持步数传感器", R.mipmap.ic_launcher_round, true);
        }

        // Initialize map view and other UI elements
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        aMap = mapView.getMap();

        stepInput = findViewById(R.id.stepInput);
        areaTextView = findViewById(R.id.areaResult);
        timeTextView = findViewById(R.id.timeResult);
        stepCountTextView = findViewById(R.id.stepCountText);
        realStepCountTextView = findViewById(R.id.realStepCountText);

        // Location and Map settings
        MyLocationStyle myLocationStyle = new MyLocationStyle();
        myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE);
        myLocationStyle.showMyLocation(true);
        myLocationStyle.strokeWidth(0);
        myLocationStyle.radiusFillColor(0x00000000);
        aMap.setMyLocationStyle(myLocationStyle);
        aMap.setMyLocationEnabled(true);

        // Bottom sheet behavior
        View bottomSheet = findViewById(R.id.cardView);
        BottomSheetBehavior<View> bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setPeekHeight(780);
        bottomSheetBehavior.setHideable(false);

        // Start button click listener
        findViewById(R.id.startBtn).setOnClickListener(v -> {
            if (!isLocationEnabled()) {
                showMessage("请开启定位服务后重试");
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                return;
            }

            try {
                String input = stepInput.getText().toString();
                minDistance = Float.parseFloat(input);
            } catch (Exception e) {
                minDistance = 2.0f;
                showMessage("无效步长，默认2米");
            }

            stepCounter = 0;
            startTimeMillis = System.currentTimeMillis();
            permissionsRequestedFromUser = true;

            requestLocationPermission();
            triggerNotificationForActivation();

            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            String packageName = getPackageName();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && pm != null
                    && !pm.isIgnoringBatteryOptimizations(packageName)) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            }
        });

        // Stop button click listener
        findViewById(R.id.stopBtn).setOnClickListener(v -> {
            if (startTimeMillis == 0) {
                showMessage("请先点击“开始记录坐标(GPS)”再结束记录");
                return;
            }
            endTimeMillis = System.currentTimeMillis();
            stopLocationUpdates();
            drawClosedPolygon();

            double area = calculateArea(pointList);
            double perimeter = calculatePerimeter(pointList);
            String duration = getDuration();

            areaTextView.setText(String.format("面积：%.2f㎡\n周长：%.2fm", area, perimeter));
            timeTextView.setText(String.format("开始：%s\n结束：%s\n用时：%s",
                    formatTime(startTimeMillis), formatTime(endTimeMillis), duration));

            stepCountTextView.setText("步长数：" + stepCounter);
            runOnUiThread(() -> realStepCountTextView.setText("真实步数：" + realSteps));
            saveToHistory(area, perimeter, stepCounter);

            if (pointList.size() >= 3) {
                double gap = euclideanDistance(pointList.get(0), pointList.get(pointList.size() - 1));
                if (gap > 10) showMessage("路径可能未闭合（起终点向量差距大）");
            }

            showMessage("轨迹记录结束");
            stopService(new Intent(this, TrackingService.class));
        });

        // Zoom button click listener
        findViewById(R.id.zoomBtn).setOnClickListener(v -> {
            if (lastLatLng != null) {
                aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(lastLatLng, 18));
                showMessage("已定位到当前位置");
            } else {
                showMessage("尚未获取定位，请先点击“开始记录坐标(GPS)”并允许使用确切的位置信息");
            }
        });

        // Layer button click listener（仅通过按钮文字和地图样式反馈状态）
        com.google.android.material.button.MaterialButton layerBtn = findViewById(R.id.layerBtn);
        layerBtn.setOnClickListener(v -> {
            boolean isSatellite = aMap.getMapType() == AMap.MAP_TYPE_SATELLITE;
            aMap.setMapType(isSatellite ? AMap.MAP_TYPE_NORMAL : AMap.MAP_TYPE_SATELLITE);
            layerBtn.setText(isSatellite ? "交通图" : "卫星图");
        });

        // Theme button click listener（仅通过按钮文字和地图样式反馈状态）
        com.google.android.material.button.MaterialButton themeBtn = findViewById(R.id.themeBtn);
        themeBtn.setOnClickListener(v -> {
            isDarkTheme = !isDarkTheme;
            aMap.setMapType(isDarkTheme ? AMap.MAP_TYPE_NIGHT : AMap.MAP_TYPE_NORMAL);

            // ✅ MD3 适配：根据主题动态切换系统栏（状态栏+导航栏）图标颜色
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View decor = getWindow().getDecorView();
                int flags = decor.getSystemUiVisibility();

                if (isDarkTheme) {
                    // 夜间模式：背景变黑，清除“浅色”标记，使图标变【白】
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    // 如果 API >= 26，同时处理底部导航栏图标
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                    }
                } else {
                    // 白天模式：背景变白，添加“浅色”标记，使图标变【黑】
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                    }
                }
                decor.setSystemUiVisibility(flags);
            }

            themeBtn.setText(isDarkTheme ? "夜间模式" : "白天模式");
        });

        // Replay button click listener
        findViewById(R.id.replayBtn).setOnClickListener(v -> {
            if (pointList.size() < 2) {
                showMessage("轨迹太短，无法回放");
                return;
            }
            aMap.clear();
            new Thread(() -> {
                for (int i = 1; i < pointList.size(); i++) {
                    final List<LatLng> subList = pointList.subList(0, i + 1);
                    runOnUiThread(() -> aMap.addPolyline(new PolylineOptions()
                            .addAll(subList)
                            .width(6)
                            .color(0xFF00BCD4)));
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException ignored) {
                    }
                }
                runOnUiThread(() -> showMessage("开始轨迹回放"));
            }).start();
        });

        // Stats button click listener
        findViewById(R.id.statsBtn).setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, StatsActivity.class));
        });
        com.google.android.material.button.MaterialButton btnLeJian = findViewById(R.id.btn_lejian);
        if (btnLeJian != null) {
            btnLeJian.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showLeJianDialog();
                }
            });
        }
    }

    private void showLeJianDialog() {
        // 使用 Material 设计的布局加载
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_step_input, null);

        // 类型转换为 TextInputEditText (需确保 XML 同步修改)
        com.google.android.material.textfield.TextInputEditText phoneInput = dialogView.findViewById(R.id.edit_phone);
        com.google.android.material.textfield.TextInputEditText passwordInput = dialogView.findViewById(R.id.edit_password);
        com.google.android.material.textfield.TextInputEditText distanceInput = dialogView.findViewById(R.id.edit_step);
        com.google.android.material.textview.MaterialTextView accountHint = dialogView.findViewById(R.id.tv_account_hint);
        com.google.android.material.button.MaterialButton submitBtn = dialogView.findViewById(R.id.btn_submit_lejian);
        com.google.android.material.button.MaterialButton cancelBtn = dialogView.findViewById(R.id.btn_cancel_lejian);

        // 规则：检测到手机号被清空时，同时清空密码（避免遗留密码误用）
        phoneInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (s == null || s.toString().trim().isEmpty()) {
                    if (passwordInput.getText() != null && passwordInput.getText().length() > 0) {
                        passwordInput.setText("");
                    }
                }
            }
        });

        // 读取已保存的乐健账号信息
        android.content.SharedPreferences sp = getSharedPreferences("lejian", MODE_PRIVATE);
        String accountsStr = sp.getString("accounts", "");
        String[] accountLines = accountsStr.isEmpty() ? new String[0] : accountsStr.split("\n");
        if (accountLines.length > 0) {
            // 默认使用第一条账号
            String[] parts = accountLines[0].split(":", 2);
            String defaultPhone = parts[0];
            String defaultPwd = parts.length > 1 ? parts[1] : "";
            phoneInput.setText(defaultPhone);
            passwordInput.setText(defaultPwd);

            accountHint.setText("当前账号：" + defaultPhone + "（点此选择账号）");
        } else {
            accountHint.setText("当前未保存账号");
        }

        accountHint.setOnClickListener(v -> showLeJianAccountSelector(phoneInput, passwordInput, accountHint));

        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setView(dialogView);

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();

        submitBtn.setOnClickListener(v -> {
            String phone = phoneInput.getText().toString();
            String password = passwordInput.getText().toString();
            String distanceStr = distanceInput.getText().toString();
            try {
                float distance = Float.parseFloat(distanceStr);
                if (distance <= 0 || distance > 14f) {
                    showPillMessage("请输入 0 到 14km 之间的值", 0, true);
                    return;
                }
                // 保存账号信息（支持多个账号，phone:password 按行存储）
                saveLeJianAccount(phone, password);
                loginWithOkHttp(phone, password, distance);
                dialog.dismiss();
            } catch (Exception e) {
                showToast("无效的里程输入");
            }
        });

        cancelBtn.setOnClickListener(v -> dialog.dismiss());
    }
    private void showLeJianAccountSelector(
            com.google.android.material.textfield.TextInputEditText phoneInput,
            com.google.android.material.textfield.TextInputEditText passwordInput,
            com.google.android.material.textview.MaterialTextView accountHint
    ) {
        android.content.SharedPreferences sp = getSharedPreferences("lejian", MODE_PRIVATE);
        String accountsStr = sp.getString("accounts", "");

        // 优化：使用 LinkedHashMap 来存储和操作账号，更高效、更安全
        java.util.LinkedHashMap<String, String> accountsMap = new java.util.LinkedHashMap<>();
        if (accountsStr != null && !accountsStr.trim().isEmpty()) {
            for (String line : accountsStr.split("\n")) {
                if (line.isEmpty()) continue;
                String[] p = line.split(":", 2);
                accountsMap.put(p[0], p.length > 1 ? p[1] : "");
            }
        }

        if (accountsMap.isEmpty()) {
            showToast("暂无已保存账号");
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(dialog.getContext());
        View view = inflater.inflate(R.layout.dialog_lejian_accounts, null);
        LinearLayout container = view.findViewById(R.id.accountsContainer);
        com.google.android.material.button.MaterialButton clearBtn = view.findViewById(R.id.btn_clear_accounts);
        clearBtn.setTextColor(android.graphics.Color.WHITE);
        // 获取颜色
        int primaryColor = getResources().getColor(R.color.primary_blue, getTheme());
        // M3主题下的警告/删除颜色
        int deleteIconColor = com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorError, android.graphics.Color.RED);

        // 使用 Map 来构建列表
        for (java.util.Map.Entry<String, String> entry : accountsMap.entrySet()) {
            String phone = entry.getKey();
            String pwd = entry.getValue();

            // 1. 创建卡片容器
            com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(this);
            LinearLayout.LayoutParams cardLayoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardLayoutParams.setMargins(0, 0, 0, 24);
            card.setLayoutParams(cardLayoutParams);
            card.setRadius(32f);
            card.setStrokeWidth(2);
            card.setStrokeColor(android.graphics.Color.parseColor("#EEEEEE"));

            // 2. 创建包含所有元素的水平布局
            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.HORIZONTAL);
            itemLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
            itemLayout.setPadding(40, 20, 20, 20); // 调整内边距以适应删除按钮

            // -- 左侧账号图标 --
            ImageView accountIcon = new ImageView(this);
            accountIcon.setImageResource(R.drawable.ic_account);
            androidx.core.graphics.drawable.DrawableCompat.setTint(accountIcon.getDrawable(), primaryColor);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(64, 64);
            iconParams.setMarginEnd(32);
            itemLayout.addView(accountIcon, iconParams);

            // -- 中间账号文本 --
            com.google.android.material.textview.MaterialTextView tv = new com.google.android.material.textview.MaterialTextView(this);
            tv.setText(phone);
            tv.setTextSize(16);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            itemLayout.addView(tv, textParams);

            ImageButton deleteBtn = new ImageButton(this);
            deleteBtn.setImageResource(R.drawable.ic_delete); // 复用图标
            androidx.core.graphics.drawable.DrawableCompat.setTint(deleteBtn.getDrawable(), deleteIconColor);

            android.util.TypedValue outValueForDelete = new android.util.TypedValue();
            getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValueForDelete, true);
            deleteBtn.setBackgroundResource(outValueForDelete.resourceId);
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(110, 110); // 增大点击区域
            deleteBtn.setPadding(24, 24, 24, 24);
            itemLayout.addView(deleteBtn, deleteParams);

            card.addView(itemLayout);

            android.util.TypedValue outValue = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            itemLayout.setBackgroundResource(outValue.resourceId); // 给LinearLayout设置水波纹
            itemLayout.setClickable(true);
            itemLayout.setFocusable(true);

            itemLayout.setOnClickListener(v1 -> {
                phoneInput.setText(phone);
                passwordInput.setText(pwd);
                accountHint.setText("当前账号：" + phone + "（点此选择账号）");
                dialog.dismiss();
            });

            deleteBtn.setOnClickListener(v_del -> {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle("删除账号")
                        .setMessage("确定要删除账号 " + phone + " 吗？")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除", (d, w) -> {

                            accountsMap.remove(phone);

                            StringBuilder sb = new StringBuilder();
                            for (java.util.Map.Entry<String, String> acc : accountsMap.entrySet()) {
                                sb.append(acc.getKey()).append(":").append(acc.getValue()).append("\n");
                            }
                            sp.edit().putString("accounts", sb.toString()).apply();

                            container.removeView(card);

                            if (phoneInput.getText() != null && phoneInput.getText().toString().equals(phone)) {
                                phoneInput.setText("");
                                passwordInput.setText("");
                                accountHint.setText(accountsMap.isEmpty() ? "当前未保存账号" : "请选择账号");
                            }
                            showToast("账号 " + phone + " 已删除");
                        })
                        .show();
            });

            container.addView(card);
        }

        clearBtn.setOnClickListener(v -> {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                    .setTitle("确认清空账号？")
                    .setMessage("将删除已保存的所有乐健账号与密码，且无法恢复。")
                    .setPositiveButton("清空", (dd, ww) -> {
                        sp.edit().remove("accounts").apply();
                        phoneInput.setText("");
                        passwordInput.setText("");
                        accountHint.setText("当前未保存账号");
                        dialog.dismiss();
                        showToast("所有账号已清空");
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        dialog.setContentView(view);
        dialog.show();
    }

    private void saveLeJianAccount(String phone, String password) {
        android.content.SharedPreferences sp = getSharedPreferences("lejian", MODE_PRIVATE);
        String raw = sp.getString("accounts", "");
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        if (raw != null && !raw.isEmpty()) {
            String[] lines = raw.split("\n");
            for (String line : lines) {
                if (line.isEmpty()) continue;
                String[] p = line.split(":", 2);
                map.put(p[0], p.length > 1 ? p[1] : "");
            }
        }
        map.put(phone, password);
        StringBuilder sbAccounts = new StringBuilder();
        for (java.util.Map.Entry<String, String> e : map.entrySet()) {
            if (sbAccounts.length() > 0) sbAccounts.append("\n");
            sbAccounts.append(e.getKey()).append(":").append(e.getValue());
        }
        sp.edit().putString("accounts", sbAccounts.toString()).apply();
    }

    private void showToast(String message) {
        new Handler(Looper.getMainLooper()).post(() ->
                showPillMessage(message, R.mipmap.ic_launcher_round, false)
        );
    }

    public void loginWithOkHttp(String phone, String password, float distance) {
        new Thread(() -> {
            try {
                String signSalt = "itauVfnexHiRigZ6";
                String signInput = phone + password + "1";
                String sign = sha1(signInput + signSalt);

                JSONObject payload = new JSONObject();
                payload.put("userName", phone);
                payload.put("password", password);
                payload.put("entrance", "1");
                payload.put("signDigital", sign);

                String rawJson = payload.toString();

                long timestamp = System.currentTimeMillis();
                String tStr = String.valueOf(timestamp);
                String aesKey = getDynamicKey(tStr);
                String encryptedBase64 = aesEncrypt(rawJson, aesKey);

                JSONObject finalJson = new JSONObject();
                finalJson.put("t", tStr);
                finalJson.put("pyd", encryptedBase64);

                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .writeTimeout(10, TimeUnit.SECONDS)
                        .build();

                RequestBody body = RequestBody.create(
                        finalJson.toString(),
                        MediaType.get("application/json; charset=utf-8")
                );

                Request request = new Request.Builder()
                        .url("https://cpes.legym.cn/authorization/user/v2/manage/login")
                        .post(body)
                        .addHeader("User-Agent", getUserAgent())
                        .addHeader("Origin", "https://cpes.legym.cn")
                        .addHeader("Referer", "https://cpes.legym.cn/")
                        .addHeader("Accept", "application/json, text/plain, */*")
                        .addHeader("Accept-Encoding", "gzip, deflate, br")
                        .addHeader("Connection", "keep-alive")
                        .addHeader("Content-Type", "application/json; charset=UTF-8")
                        .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
                        .addHeader("Host", "cpes.legym.cn")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String result = response.body().string();
                        Log.d("LeJian", "登录成功：" + result);

                        JSONObject obj = new JSONObject(result);
                        JSONObject data = obj.getJSONObject("data");
                        String encrypted = data.getString("pyd");
                        String timeStr = data.getString("t");

                        String dynamicKey = getDynamicKey(timeStr);
                        String decrypted = aesDecrypt(encrypted, dynamicKey);

                        JSONObject realData = new JSONObject(decrypted);
                        Log.d("LeJian", "🧾 解密后的数据: " + realData.toString());

                        token = realData.optString("accessToken", "");
                        id = realData.optString("id", "");
                        schoolId = realData.optString("schoolId", "");
                        semesterId = getSemesterId(token);

                        if (token.isEmpty() || id == null || schoolId == null || semesterId == null) {
                            runOnUiThread(() -> showToast("登录成功，但缺少必要参数，上传取消"));
                            return;
                        }

                        runOnUiThread(() -> showToast("登录成功，正在上传刷步数据..."));

                        // 直接传递 limitationsId 给 sendFakeRunData
                        String limitationsId = "yourLimitationsId"; // 用您的实际 limitationsId 替换这里
                        sendFakeRunData(token, id, schoolId, semesterId, limitationsId, distance);

                    } else {
                        String error = response.body().string();
                        Log.e("LeJian", "登录失败，状态码：" + response.code() + "\n" + error);
                        runOnUiThread(() -> showToast("登录失败：" + response.code()));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> showToast("登录异常：" + e.getMessage()));
            }
        }).start();
    }

    private String aesDecrypt(String ciphertext, String key) throws Exception {
        String cipher = "AES/ECB/PKCS5Padding";
        key = key.substring(0, 16);

        Cipher cipherInstance = Cipher.getInstance(cipher);
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");
        cipherInstance.init(Cipher.DECRYPT_MODE, keySpec);

        byte[] decodedData = Base64.decode(ciphertext, Base64.NO_WRAP);
        byte[] decrypted = cipherInstance.doFinal(decodedData);

        return new String(decrypted, "UTF-8");
    }

    private void checkAmapKey() {
        try {

            ApplicationInfo appInfo = getPackageManager()
                    .getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            String amapKey = appInfo.metaData.getString("com.amap.api.v2.apikey");

            if (amapKey == null || amapKey.trim().isEmpty() || amapKey.equals("PLEASE_SET_YOUR_OWN_KEY")) {

                String savedKey = getSharedPreferences("prefs", MODE_PRIVATE)
                        .getString("user_amap_key", null);

                if (savedKey == null || savedKey.trim().isEmpty()) {

                    showApiKeyInputDialog();
                } else {

                    AMapLocationClient.setApiKey(savedKey);
                }
            } else {

                AMapLocationClient.setApiKey(amapKey);
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void showApiKeyInputDialog() {

        BottomSheetDialog dialog = new BottomSheetDialog(this);


        View view = getLayoutInflater().inflate(R.layout.dialog_input_apikey, null);
        EditText inputField = view.findViewById(R.id.editTextApiKey);
        Button saveBtn = view.findViewById(R.id.saveKeyButton);


        saveBtn.setOnClickListener(v -> {
            String userKey = inputField.getText().toString().trim();
            if (!userKey.isEmpty()) {

                getSharedPreferences("prefs", MODE_PRIVATE)
                        .edit()
                        .putString("user_amap_key", userKey)
                        .apply();


                AMapLocationClient.setApiKey(userKey);

                Toast.makeText(MainActivity.this, "Key 已保存，请重启APP", Toast.LENGTH_SHORT).show();
                restartApp(); // 重启 App
            } else {

                Toast.makeText(MainActivity.this, "Key 不能为空", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.setContentView(view);
        dialog.setCancelable(false);
        dialog.show();
    }

    private void restartApp() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }
    }

    private void saveToHistory(double area, double perimeter, int steps) {
        try {
            File file = new File(getExternalFilesDir(null), "history.json");
            JSONArray all = new JSONArray();

            if (file.exists()) {
                FileInputStream in = new FileInputStream(file);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                in.close();
                String existing = out.toString("UTF-8");
                if (!existing.isEmpty()) {
                    all = new JSONArray(existing);
                }
            }

            JSONObject record = new JSONObject();
            record.put("area", area);
            record.put("distance", perimeter);
            record.put("steps", steps);
            record.put("realSteps", realSteps);
            record.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));

            JSONArray pathArray = new JSONArray();
            for (LatLng point : pointList) {
                JSONObject p = new JSONObject();
                p.put("lat", point.latitude);
                p.put("lng", point.longitude);
                pathArray.put(p);
            }
            record.put("path", pathArray);

            all.put(record);

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(all.toString().getBytes("UTF-8"));
            fos.close();

            showMessage("记录已保存，共 " + all.length() + " 条");

        } catch (Exception e) {
            showMessage("保存失败：" + e.getMessage());
        }
    }

    private boolean isLocationEnabled() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        return lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    private void startLocationUpdates() {
        pointList.clear();
        lastLatLng = null;
        aMap.clear();

        try {
            locationClient = new AMapLocationClient(getApplicationContext());
        } catch (Exception e) {
            showMessage("定位初始化失败：" + e.getMessage());
            return;
        }

        locationOption = new AMapLocationClientOption();
        locationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        locationOption.setInterval(1000);
        locationClient.setLocationOption(locationOption);

        locationClient.setLocationListener(location -> {
            if (location != null && location.getErrorCode() == 0) {
                LatLng current = new LatLng(location.getLatitude(), location.getLongitude());
                if (lastLatLng == null || euclideanDistance(lastLatLng, current) >= minDistance) {
                    pointList.add(current);
                    lastLatLng = current;
                    stepCounter++;
                    runOnUiThread(() -> aMap.addPolyline(new PolylineOptions()
                            .addAll(pointList)
                            .width(6)
                            .color(0xFF1E90FF)));
                }
            }
        });

        locationClient.startLocation();
        showMessage("开始记录轨迹");
    }

    private void stopLocationUpdates() {
        if (locationClient != null) {
            locationClient.stopLocation();
            locationClient.onDestroy();
        }
    }

    private void drawClosedPolygon() {
        if (pointList.size() >= 3) {
            if (!pointList.get(0).equals(pointList.get(pointList.size() - 1))) {
                pointList.add(pointList.get(0));
            }
            aMap.addPolygon(new PolygonOptions()
                    .addAll(pointList)
                    .fillColor(0x3300FF00)
                    .strokeWidth(4)
                    .strokeColor(0xFF00AA00));
        }
    }

    private double calculateArea(List<LatLng> list) {
        if (list.size() < 3) return 0;
        double area = 0;
        for (int i = 0; i < list.size(); i++) {
            LatLng p1 = list.get(i);
            LatLng p2 = list.get((i + 1) % list.size());
            area += (p1.longitude * p2.latitude - p2.longitude * p1.latitude);
        }
        return Math.abs(area / 2.0) * 111139 * 111139;
    }

    private double calculatePerimeter(List<LatLng> list) {
        double total = 0;
        for (int i = 1; i < list.size(); i++) {
            total += euclideanDistance(list.get(i - 1), list.get(i));
        }
        if (list.size() > 2) {
            total += euclideanDistance(list.get(0), list.get(list.size() - 1));
        }
        return total;
    }

    private double euclideanDistance(LatLng p1, LatLng p2) {

        double latDiff = Math.toRadians(p2.latitude - p1.latitude);
        double lonDiff = Math.toRadians(p2.longitude - p1.longitude);
        double earthRadius = 6371.0;
        double distance = Math.sqrt(Math.pow(latDiff, 2) + Math.pow(lonDiff, 2)) * earthRadius;
        return distance * 1000;
    }

    private String formatTime(long millis) {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(millis));
    }

    private String getDuration() {
        long seconds = (endTimeMillis - startTimeMillis) / 1000;
        return (seconds / 60) + "分" + (seconds % 60) + "秒";
    }

    private void showMessage(String message) {
        showPillMessage(message, R.mipmap.ic_launcher_round, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();


        Sensor stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (stepSensor != null) {
            sensorManager.registerListener(stepListener, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(stepListener);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 999) { // 通知权限回调
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // ✅ 统一使用 Snackbar 样式的 showMessage
                showMessage("✅ 通知权限已授权");
            } else {
                showPillMessage("未授权通知，可能看不到后台记录", R.mipmap.ic_launcher_round, true);
                Intent intent = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(intent);
            }
        } else if (requestCode == 201) { // 定位及相关权限回调
            boolean granted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (granted) {
                if (permissionsRequestedFromUser) {
                    permissionsRequestedFromUser = false;
                    // ... 启动逻辑保持不变 ...
                    startLocationUpdates();
                    showMessage("✅ 已获取权限，开始记录");
                }
            } else {
                showPillMessage("权限不足，无法准确记录轨迹", R.mipmap.ic_launcher_round, true);
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("权限说明")
                        .setMessage("轨迹记录需要后台定位权限，否则App切入后台后记录会中断。")
                        .setPositiveButton("我知道了", null)
                        .show();
            }
        }
    }

    private void triggerNotificationForActivation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w("通知", "没有通知权限，无法触发测试通知");
                return;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "tracking_channel")
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle("通知权限激活测试")
                        .setContentText("此为后台记录功能测试通知")
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setAutoCancel(true);
                manager.notify(10086, builder.build());
            }
        }
    }
    private JSONObject convertJson2Oct(JSONObject original) throws Exception {
        JSONObject converted = new JSONObject();
        for (String key : fieldMap.keySet()) {
            if (original.has(key)) {
                converted.put(fieldMap.get(key), original.get(key));
            }
        }
        return converted;
    }
    private String octSign(JSONObject raw, String id, String schoolId) throws Exception {
        if (id == null || id.length() < 12 || schoolId == null || schoolId.length() < 7) {
            throw new IllegalArgumentException("id 或 schoolId 长度不足，无法生成签名！");
        }

        JSONObject oct = convertJson2Oct(raw);

        String jsonStr = oct.toString(2);
        jsonStr = jsonStr.replace("    ", "  ").replace("\":", "\" :");

        String key = id.substring(3, 6) + schoolId.substring(4, 7) + id.substring(9, 12)
                + "3e0783d6891a4a3e9521dcb6bb341560";
        key = key.substring(0, 16);

        return aesEncrypt(jsonStr, key);
    }
    private void generateRoutineLine(JSONObject payload) throws JSONException {
        JSONArray routineLine = new JSONArray();
        double[][] coords = new double[][]{
        {104.18877522786460,30.82946099175347},
        {104.18878603819594,30.82944443758445},
        {104.18881321936198,30.82942978506399},
        {104.18885931560628,30.82942318290062},
        {104.18902726246664,30.829416497418755},
        {104.18911476750392,30.829432022435693},
        {104.18919820789723,30.829472547676623},
        {104.18927196196633,30.829526101232062},
        {104.18935506528824,30.82962912520049},
        {104.18943430612012,30.829818550847655},
        {104.1895791442782, 30.83025454681924},
        {104.18960951833104,30.83033465149054},
        {104.18963964732606,30.83043611760497},
        {104.18964343518354,30.830494067721673},
        {104.1896316447624, 30.83056857328485},
        {104.18960967288544,30.830607132875738},
        { 104.18950648366784, 30.830717074276908},
        { 104.18938865891,   30.83079609219052},
        { 104.18924447703328, 30.83084630923584},
        { 104.18915327686908, 30.83085144251209},
        { 104.1890965108574,  30.83084597254828},
        { 104.188972037833,   30.83078756037325},
        { 104.18888326401164, 30.83069063989911},
        { 104.18884500224902, 30.830621757438593},
        { 104.18867617939408, 30.8301382923264},
        { 104.188650915314,   30.830039689140406},
        { 104.18858089207129, 30.829807216369556},
        { 104.18858088302736, 30.829711533291},
        { 104.18860818597253, 30.829635191968613},
        { 104.18870606506708, 30.829525856753992},
        { 104.18877450116938, 30.82947712002939},
        { 104.18882241380636, 30.829452950512422},
        { 104.18893665344544, 30.8294330368758},
        { 104.1889674499049,  30.82943175947288},
        { 104.18899935162788, 30.8294239135716},
        { 104.18905851153738, 30.829427805676453},
        { 104.1891458724102,  30.82944853467326},
        { 104.1892470044418,  30.829492072770748},
        { 104.18936242061469, 30.829603949128753},
        { 104.18939767774566, 30.829672037118947},
        { 104.18949720921056, 30.829980407621463},
        { 104.18953204208854, 30.83011168043583},
        { 104.18964135254468, 30.83045489608918},
        { 104.18964608799052, 30.83049083555892},
        { 104.18962291623257, 30.83058598371748},
        { 104.1895387865337,  30.8307091140082},
        { 104.18949595267904, 30.830743342364777},
        { 104.18935473020449, 30.830829506241937},
        { 104.18923169407908, 30.830858791324054},
        { 104.18917016749288, 30.83086196709512},
        { 104.18906321788484, 30.830840561383976},
        { 104.1889886930741,  30.83080406186967},
        { 104.18888761934222, 30.830715709829494},
        { 104.18885158252807, 30.830652036018396},
        { 104.18880782234191, 30.83054195896259},
        { 104.1887512033057,  30.830342921923737},
        { 104.18866551225948, 30.83010190137995},
        { 104.18865586688248, 30.83005942703054},
        { 104.18858999212236, 30.82990740229923},
        { 104.18856343665966, 30.829772556213157},
        { 104.1885863254156,  30.82966473387037},
        { 104.18861443293164, 30.829608703906345},
        { 104.18867721248104, 30.82953508964959},
        { 104.1888300619208,  30.829447457917748},
        { 104.18892545297628, 30.829428003913662},
        { 104.1891331933444,  30.829440736803026},
        { 104.18916369520286, 30.829447932718853},
        { 104.1892949868891,  30.829517812605893},
        { 104.18935293007534, 30.82957988696563},
        { 104.18939562311267, 30.82965012600323},
        { 104.18947235549376, 30.829869993556706},
        { 104.18949435854174, 30.82991539712236},
        { 104.18963055996127, 30.83036973217385},
        { 104.1896388220108,  30.8304199053543},
        { 104.18965100882104, 30.830469771280825},
        { 104.18964465954612, 30.830565712497737},
        { 104.18957981458146, 30.83069697167289},
        { 104.1895475615536,  30.830736254135996},
        { 104.1893861221525,  30.830835182607583},
        { 104.18929975096802, 30.830861385731943},
        { 104.18916144420864, 30.830868869179035},
        { 104.18910919034808, 30.83086477150511},
        { 104.18903684816506, 30.83084472311456},
        { 104.18899572755365, 30.830820190167618},
        { 104.18891842142088, 30.830750546457953},
        { 104.18885449364106, 30.83065521394403},
        { 104.18879324224916, 30.830508689118343},
        { 104.18875592962686, 30.830365703889345},
        { 104.18870809244495, 30.830236196039124},
        { 104.18868634669424, 30.830196388638225},
        { 104.1886664002854,  30.83007438993803},
        { 104.18864581555424, 30.830027492840227},
        { 104.18858661806875, 30.82978664157628},
        { 104.18864585527872, 30.82959747029872},
        { 104.18867372709688, 30.829557940725177 },
        { 104.18875702317322, 30.829490506410124 },
        { 104.18885093934723, 30.82944627259616 },
        { 104.18893511462986, 30.82942437541861 },
        { 104.189018264405, 30.829424714353895 },
        { 104.18912934568284, 30.829447163697584 },
        { 104.18922799408718, 30.829490601820524 },
        { 104.18931378600372, 30.829574226225255 },
        { 104.18937485192576, 30.82967496310252 },
        { 104.18938482874267, 30.82972072575363 },
        { 104.18953450227816, 30.830112058970744 },
        { 104.18960280755346, 30.830321871989735 },
        { 104.18964864776288, 30.830465578870168 },
        { 104.18965498538904, 30.830538071021717 },
        { 104.18963016478168, 30.830629808839088 },
        { 104.18958827626592, 30.830691684759092 },
        { 104.18954141349568, 30.830733138418015 },
        { 104.1894335199917, 30.83078079925439 },
        { 104.18938346665064, 30.83081704768464 },
        { 104.1892088681179, 30.830865319495672 },
        { 104.18915191515852, 30.830870863345513 },
        { 104.18905211799228, 30.830848534789588 },
        { 104.18898577973576, 30.83081260985745 },
        { 104.18891734536676, 30.830748285975385 },
        { 104.18886812916884, 30.83066837937564 },
        { 104.18871104664387, 30.83022997109433 },
        { 104.18862764479114, 30.829991787327156 },
        { 104.18861598182488, 30.8299697653528 },
        { 104.18857719305586, 30.829797374754936 },
        { 104.18857890098936, 30.82975552723833 },
        { 104.18863125922464, 30.829598360585127 },
        { 104.18864499646948, 30.829580110949667 },
        { 104.18869993388226, 30.829532118804 },
        { 104.1887602950153, 30.82949920331068 },
        { 104.18888342665232, 30.8294543316515 },
        { 104.18897269475744, 30.82943762692482 },
        { 104.18902607191926, 30.829436135473532 },
        { 104.18907992674414, 30.829444461503872 },
        { 104.18923207642788, 30.829498160199844 },
        { 104.18929848404387, 30.829541208508903 },
        { 104.18937391524685, 30.82964341611994 },
        { 104.1895363327458, 30.830090796448207 },
        { 104.18956332471328, 30.830203604596456 },
        { 104.1896258200574, 30.830390692669425 },
        { 104.18963032508448, 30.83049695698176 },
        { 104.18960594708672, 30.830543764814973 },
        { 104.1894404197115, 30.830766942661725 },
        { 104.18942161385056, 30.830784182489666 },
        { 104.18932062254514, 30.830842902347797 },
        { 104.18923378313826, 30.830869342950084 },
        { 104.18912588649174, 30.83087694933731 },
        { 104.18905360545678, 30.830860048423293 },
        { 104.18897749187782, 30.83081835920968 },
        { 104.18889595213214, 30.830745222525717},
        { 104.18884742498987, 30.830677173185215},
        { 104.1888067711858, 30.83055363393179},
        { 104.18880079004252, 30.83050483101576},
        { 104.18873451967488, 30.83032968722},
        { 104.18862967503082, 30.830025817475256},
        { 104.18858287669973, 30.82987002945216},
        { 104.18857784772578, 30.829782481930533},
        { 104.1885908080289, 30.829672913664897},
        { 104.18858448880756,  30.82966868617254}
        };
        Random rand = new Random();

        // 1. 随机切片 (保留原坐标数据的5-24到57-70区间)
        int start = rand.nextInt(20) + 5;  // 生成5-24之间的随机数
        int end = Math.min(coords.length, rand.nextInt(14) + 57); // 生成57-70之间的随机数

        // 2. 坐标微调 + 构建轨迹
        for (int i = start; i < end; i++) {
            double[] coord = coords[i];

            // 经度微调 (±0.00001范围)
            double lngOffset = (rand.nextDouble() * 2 - 1) * 0.00001 + 0.0001;
            // 纬度微调 (±0.00001范围)
            double latOffset = (rand.nextDouble() * 2 - 1) * 0.00001 + 0.0001;

            JSONObject point = new JSONObject();
            point.put("latitude", Double.parseDouble(String.format("%.15f", coord[1] + latOffset))); // 纬度
            point.put("longitude", Double.parseDouble(String.format("%.15f", coord[0] + lngOffset))); // 经度
            routineLine.put(point);
        }

        // 3. 反转轨迹数组
        JSONArray reversedLine = new JSONArray();
        for (int i = routineLine.length() - 1; i >= 0; i--) {
            reversedLine.put(routineLine.get(i));
        }
        payload.put("routineLine", reversedLine);
    }
    private String getUserAgent() {
        String deviceType = Build.MODEL;
        String systemVersion = Build.VERSION.RELEASE;
        return "Mozilla/5.0 (Linux; Android " + systemVersion + "; " + deviceType +
                ") AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    }

    public void sendFakeRunData(String token, String id, String schoolId, String semesterId, String limitationsId,double distance) {
        Log.d("LeJian", "📦 已进入 sendFakeRunData()");
        new Thread(() -> {
            try {
                Random rand = new Random();
                double randNum = rand.nextDouble() * 0.09 + 0.01; // 生成0.01-0.1的随机数

                int avePace = (500 + rand.nextInt(200)) * 1000; // 500-700秒/公里
                int keepTime = (int)(distance * (avePace / 1000.0));
                int signOffset = (int)(keepTime % 11); // 时间偏移关键

                int paceNumber = (int)(distance * 402);
                int calorie = (int)(distance * 97);

                long now = System.currentTimeMillis() / 1000;
                String startTime = formatUnix(now - keepTime - signOffset); // 添加偏移量
                String endTime = formatUnix(now - signOffset);
                String signTime = formatUnix(now);

                String deviceType = Build.MODEL;
                String systemVersion = Build.VERSION.RELEASE;

                JSONObject payload = new JSONObject();
                payload.put("gpsMileage", fixPrecision(distance + randNum, 6));
                payload.put("effectiveMileage", 14);
                payload.put("totalMileage", fixPrecision(distance + randNum, 6));
                payload.put("limitationsGoalsSexInfoId",distance);
                payload.put("paceNumber", paceNumber);
                payload.put("calorie", calorie);
                payload.put("avePace", avePace);
                payload.put("startTime", startTime);
                payload.put("endTime", endTime);
                payload.put("keepTime", keepTime);
                payload.put("totalPart", 1);
                payload.put("effectivePart", 1);
                payload.put("semesterId", semesterId);
                payload.put("uneffectiveReason", "超过日里程限制");
                payload.put("signTime", signTime);
                payload.put("type", "自由跑");
                payload.put("paceRange", 0.6);
                payload.put("scoringType", 1);
                payload.put("signPoint", new JSONArray());
                payload.put("appVersion", "3.10.0");
                payload.put("deviceType", deviceType);
                payload.put("systemVersion", systemVersion);

                generateRoutineLine(payload);

                // ✍️ signDigital 签名字段
                String salt = "itauVfnexHiRigZ6";
                String willBeSigned = String.format("%.5f", payload.getDouble("effectiveMileage"))
                        + payload.getInt("effectivePart")
                        + payload.getString("startTime")
                        + payload.getInt("calorie")
                        + payload.getInt("avePace")
                        + payload.getInt("keepTime")
                        + payload.getInt("paceNumber")
                        + String.format("%.5f", payload.getDouble("totalMileage"))
                        + payload.getInt("totalPart")
                        + salt;
                payload.put("signDigital", sha1(willBeSigned));

                // ✨ 加签 oct
                payload.put("oct", octSign(payload, id, schoolId));

                Log.d("LeJian", "🚀 上传内容：" + payload.toString(2));

                OkHttpClient client = new OkHttpClient();
                RequestBody body = RequestBody.create(
                        payload.toString(),
                        MediaType.get("application/json; charset=utf-8")
                );

                Request request = new Request.Builder()
                        .url("https://cpes.legym.cn/running//app/v3/upload")
                        .post(body)
                        .addHeader("Authorization", "Bearer " + token)
                        .addHeader("User-Agent", getUserAgent())
                        .addHeader("Origin", "https://cpes.legym.cn")
                        .addHeader("Referer", "https://cpes.legym.cn/")
                        .addHeader("Accept", "application/json, text/plain, */*")
                        .addHeader("Accept-Encoding", "gzip, deflate, br")
                        .addHeader("Connection", "keep-alive")
                        .addHeader("Content-Type", "application/json; charset=UTF-8")
                        .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
                        .addHeader("Host", "cpes.legym.cn")
                        .build();

                Response response = client.newCall(request).execute();
                String responseBody = response.body().string();
                if (response.isSuccessful()) {
                    if (id == null || id.length() < 12) {
                        Log.e("LeJian", "id 不合法，无法上传刷步数据");
                        runOnUiThread(() -> showToast("登录成功，但 id 不合法，无法上传刷步数据"));
                        return;
                    }
                    Log.d("LeJian", "刷步数据上传成功：" + responseBody);
                    Log.e("LeJian", "响应码：" + response.code() + "，响应体：" + responseBody);
                    runOnUiThread(() -> showLongerToast("🎉 刷步数据上传成功！ 还愣着干嘛？不打开乐健看看嘛？"));
                } else {
                    Log.e("LeJian", "上传失败：" + responseBody);
                    if (responseBody.contains("今日已刷满") || responseBody.contains("超过") || responseBody.contains("14")) {
                        runOnUiThread(() -> showToast("今日已刷满 14km，上传失败"));
                    } else {
                        Log.e("LeJian", "请求的完整URL：" + request.url());
                        runOnUiThread(() -> showToast("上传失败：" + response.code()));
                    }
                }
            } catch (Exception e) {
                Log.e("LeJian", "错误：" + e.getMessage());
                e.printStackTrace();
                runOnUiThread(() -> showToast("上传异常：" + e.getMessage()));
            }
        }).start();
    }

    private String formatUnix(long seconds) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(seconds * 1000));
    }

    private String getSemesterId(String token) {
        try {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url("https://cpes.legym.cn/education/semester/getCurrent")
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("User-Agent", getUserAgent())
                    .addHeader("Origin", "https://cpes.legym.cn")
                    .addHeader("Referer", "https://cpes.legym.cn/")
                    .addHeader("Accept", "application/json, text/plain, */*")
                    .addHeader("Accept-Encoding", "gzip, deflate, br")
                    .build();
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                String json = response.body().string();
                Log.d("LeJian", "📚 学期接口返回：" + json);

                JSONObject obj = new JSONObject(json);
                if (obj.has("data") && !obj.isNull("data")) {
                    JSONObject data = obj.getJSONObject("data");
                    if (data.has("id")) {
                        return data.getString("id");
                    } else {
                        Log.e("LeJian", "data 中无 id 字段！");
                    }
                } else {
                    Log.e("LeJian", "响应中无 data 字段或 data 为 null");
                }
            } else {
                Log.e("LeJian", "学期接口请求失败：" + response.code());
            }
        } catch (Exception e) {
            Log.e("LeJian", "获取学期 ID 异常", e);
        }
        Log.e("LeJian", "返回空 semesterId，接口可能失效或者未登录成功！");
        showToast("获取学期信息失败，无法上传里程");
        return "";
    }
    private double fixPrecision(double value, int digits) {
        return Double.parseDouble(String.format("%." + digits + "f", value));
    }
}
