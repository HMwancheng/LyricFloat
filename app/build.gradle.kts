plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lyricfloat"
    compileSdk = 34

    // 1. 签名配置：复用Android默认调试签名（无需手动密钥库）
    signingConfigs {
        // Android默认调试签名配置（自动生成，无需手动创建）
        getByName("debug") {
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        // Release构建复用调试签名（仅测试/临时使用）
        create("release") {
            initWith(signingConfigs.getByName("debug")) // 复用调试签名配置
        }
    }

    defaultConfig {
        applicationId = "com.lyricfloat"
        minSdk = 16
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled = true // 解决64K方法限制

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release") // 关联复用的调试签名
            isMinifyEnabled = false // 测试阶段关闭混淆
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("debug") // debug构建默认使用调试签名
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
        viewBinding = true // 启用ViewBinding（若项目使用）
        dataBinding = false // 禁用DataBinding减少依赖
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}" // 排除冲突的元文件
        }
    }
}

dependencies {
    // AndroidX核心依赖
    implementation("androidx.core:core-ktx:1.7.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")

    // Multidex支持（minSdk<21必需）
    implementation("androidx.multidex:multidex:2.0.1")

    // 偏好设置
    implementation("androidx.preference:preference-ktx:1.2.1")

    // 生命周期
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.5.1")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // 网络/解析（可选，若项目使用）
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // 测试依赖
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
