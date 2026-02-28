plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.tensorhub.manifold"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tensorhub.manifold"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ✅ 注入 Manifest 占位符
        val amapApiKey: String? = project.findProperty("amap.api.key") as String?
        manifestPlaceholders["AMAP_API_KEY"] = amapApiKey ?: "PLEASE_SET_YOUR_OWN_KEY"
        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "proguard-amap.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    // ✅ 高德地图 SDK
    implementation("com.amap.api:3dmap:9.8.3")
    // ✅ Google 官方库
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.activity)

    // ✅ 单元测试
    testImplementation(libs.junit)

    // ✅ Android Instrumentation 测试
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
