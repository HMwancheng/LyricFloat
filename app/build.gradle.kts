plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lyricfloat"
    compileSdk = 34 // 编译SDK版本（最新稳定版）

    // 1. 签名配置：复用Android默认调试签名（带签名，避免安装异常）
    signingConfigs {
        // Android默认调试签名（自动生成，无需手动创建keystore）
        getByName("debug") {
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            enableV1Signing = true // 兼容旧版签名验证
            enableV2Signing = true // 兼容新版签名验证
        }

        // Release构建复用调试签名（带签名，可直接安装）
        create("release") {
            initWith(signingConfigs.getByName("debug")) // 复用调试签名配置
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    defaultConfig {
        applicationId = "com.lyricfloat"
        minSdk = 29 // 最低支持Android 10（API 29）
        targetSdk = 34 // 目标SDK版本（与compileSdk一致）
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled = false // Android 21+默认支持MultiDex，无需手动开启

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release") // 关联调试签名
            isMinifyEnabled = false // 关闭混淆（测试/内部使用）
            isDebuggable = false // 关闭调试模式（正式release特性）
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            signingConfig = signingConfigs.getByName("debug") // debug构建默认使用调试签名
            isDebuggable = true
        }
    }

    // 编译配置
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // 构建特性
    buildFeatures {
        viewBinding = true // 启用ViewBinding（悬浮窗布局使用）
        dataBinding = false // 禁用DataBinding（未使用）
        compose = false // 禁用Compose（未使用）
    }

    // 打包配置（排除冲突元文件）
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX核心依赖（Android 10+适配）
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // 卡片视图（悬浮窗布局使用）
    implementation("androidx.cardview:cardview:1.0.0")

    // 协程（歌词解析/网络请求异步处理）
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // 网络请求（获取网络歌词）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON解析（解析歌词API返回数据）
    implementation("com.google.code.gson:gson:2.10.1")

    // 偏好设置（设置页面使用）
    implementation("androidx.preference:preference-ktx:1.2.1")

    // 测试依赖（可选，不影响APK运行）
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
