plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.xnlu.alutube"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.xnlu.alutube"
        minSdk = 26
        targetSdk = 34
        versionCode = 24
        versionName = "3.3"
    }

    buildTypes {
        release {
            // 代码压缩 + 资源裁剪：去掉未用到的 AppCompat/Material 资源，显著缩小 APK
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 与历史版本保持同一签名（debug 签名），否则已安装设备无法覆盖升级
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // release 打包不跑 lintVital：构建更快，也避免离线环境缺少 lint 依赖导致失败。
        // 需要体检时单独执行 gradlew lintRelease
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
