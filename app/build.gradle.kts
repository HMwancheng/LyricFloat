plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lyricfloat"
    compileSdk = 34

    // 1. 添加签名配置
    signingConfigs {
        create("release") {
            // 方式1：使用本地密钥库文件（推荐CI环境用环境变量）
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "lyricfloat.jks") // 密钥库路径
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "your_store_password" // 密钥库密码
            keyAlias = System.getenv("KEY_ALIAS") ?: "lyricfloat_key" // 密钥别名
            keyPassword = System.getenv("KEY_PASSWORD") ?: "your_key_password" // 密钥密码

            // 方式2：本地硬编码（仅测试用，生产环境禁用）
            // storeFile = file("lyricfloat.jks")
            // storePassword = "123456"
            // keyAlias = "lyricfloat"
            // keyPassword = "123456"
        }
    }

    defaultConfig {
        applicationId = "com.lyricfloat"
        minSdk = 16
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled = true // 保持Multidex配置
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false // 测试阶段关闭混淆，发布时可开启
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 2. 关联签名配置到release构建
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release") // 可选：debug也用相同签名
        }
    }

    // 其他原有配置（compileOptions、kotlin、buildFeatures等）保持不变
}

dependencies {
    // 原有依赖保持不变
}
