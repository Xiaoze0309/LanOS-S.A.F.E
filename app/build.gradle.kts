// app 模块构建脚本：声明 Compose 相关依赖
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Compose 编译器插件
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.lanos.safe"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lanos.safe"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM：统一管理 Compose 版本
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    // 基础 Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Material Icons Extended（严禁 Emoji，必须用矢量图标）
    implementation("androidx.compose.material:material-icons-extended")

    // AndroidX 基础库
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    // Navigation Compose：管理路由
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // 预览工具（调试用）
    debugImplementation("androidx.compose.ui:ui-tooling")
}
