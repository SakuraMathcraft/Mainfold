package com.tensorhub.manifold;

import android.app.Application;
import android.content.SharedPreferences;
import com.amap.api.location.AMapLocationClient;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // 从 SharedPreferences 获取用户设置的 Key
        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        String userKey = prefs.getString("user_amap_key", null);
        if (userKey != null && !userKey.trim().isEmpty()) {
            AMapLocationClient.setApiKey(userKey);
        }
    }
}
