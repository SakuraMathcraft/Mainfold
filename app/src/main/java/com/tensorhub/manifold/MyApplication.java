package com.tensorhub.manifold;

import android.app.Application;
import android.content.SharedPreferences;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.maps.MapsInitializer;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // 从 SharedPreferences 获取用户设置的 Key
        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        String userKey = prefs.getString("user_amap_key", null);
        
        // 关键修复：必须先调用合规接口，否则 MapSDK 也许能用，但 LocationSDK 会直接报错或无回调
        // 参阅高德合规指南：https://lbs.amap.com/api/android-location-sdk/guide/create-project/strict-compliance
        AMapLocationClient.updatePrivacyShow(this, true, true);
        AMapLocationClient.updatePrivacyAgree(this, true);
        MapsInitializer.updatePrivacyShow(this, true, true);
        MapsInitializer.updatePrivacyAgree(this, true);

        if (userKey != null && !userKey.trim().isEmpty()) {
            // 同时设置定位和地图的 Key，覆盖 Manifest 中的默认值
            AMapLocationClient.setApiKey(userKey);
            MapsInitializer.setApiKey(userKey);
        }
    }
}
