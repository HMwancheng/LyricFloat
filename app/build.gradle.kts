plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lyricfloat"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lyricfloat"
        minSdk = 16
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
        languageVersion = "2.0"
    }
    
    buildFeatures {
        viewBinding = true
    }
    
    // Gradle 9.x需要显式声明的配置
    namespace = "com.lyricfloat"
}

dependencies {
    // AndroidX核心库（使用最新兼容版本）
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    
    // 网络请求
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    
    // 媒体元数据解析（使用兼容版本）
    implementation("com.github.warren-bank:android-mp3agic:0.9.1.9")
    
    // 歌词解析（替换为兼容库）
    implementation("io.github.nafg.simple-lyrics:core:0.1.0")
    
    // 偏好设置
    implementation("androidx.preference:preference-ktx:1.2.1")
    
    // 兼容库
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
