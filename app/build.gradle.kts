plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lyricfloat"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lyricfloat"
        minSdk = 16  // 保持支持安卓16
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
    
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        }
    }
    
    buildFeatures {
        viewBinding = true
        dataBinding = false
    }
    
    // 兼容安卓16的配置
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX核心库（兼容minSdk 16的版本）
    implementation("androidx.core:core-ktx:1.7.0")  // 1.7.0支持minSdk 16
    implementation("androidx.appcompat:appcompat:1.6.1")  // 1.6.1支持minSdk 16
    implementation("com.google.android.material:material:1.9.0")  // 1.9.0是支持minSdk 16的最后版本
    
    // 网络请求
    implementation("com.squareup.retrofit2:retrofit:2.9.0")  // 兼容旧版Android
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    
    // 媒体元数据解析
    implementation("com.mpatric:mp3agic:0.9.1")
    
    // 偏好设置（兼容minSdk 16）
    implementation("androidx.preference:preference-ktx:1.2.1")
    
    // 生命周期库（兼容minSdk 16）
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.5.1")
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
