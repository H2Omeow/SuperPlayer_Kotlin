import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}

// ==================== 版本管理 ====================
val versionProps = Properties().also { props ->
    val f = file("version.properties")
    if (f.exists()) f.inputStream().use(props::load)
}
val buildVersionCode = versionProps.getProperty("versionCode", "1").toInt()
val buildVersionName = versionProps.getProperty("versionName", "1.0.0")

/**
 * 手动递增版本号（仅在新增功能时执行）：
 *   ./gradlew incrementVersion
 * versionCode +1，versionName patch 段 +1。
 * 修 bug 的构建不调用此任务，版本号保持不变。
 */
tasks.register("incrementVersion") {
    doFirst {
        val newCode = buildVersionCode + 1
        val parts = buildVersionName.split(".")
        val newName = "${parts.getOrElse(0) { "1" }}.${parts.getOrElse(1) { "0" }}" +
            ".${(parts.getOrNull(2)?.toIntOrNull() ?: 0) + 1}"
        versionProps.setProperty("versionCode", newCode.toString())
        versionProps.setProperty("versionName", newName)
        file("version.properties").outputStream().use { versionProps.store(it, "auto-generated") }
        println("Version bumped → $newCode ($newName)")
    }
}

android {
    namespace = "top.nekoh2o.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "top.nekoh2o.player"
        minSdk = 24
        targetSdk = 34
        versionCode = buildVersionCode
        versionName = buildVersionName
    }

    // 正式签名：CI 通过环境变量注入。本地无这些变量时 release 走 debug 签名，
    // 不影响本地 assembleDebug / assembleRelease 流程。
    val releaseStoreFile = System.getenv("KEYSTORE_FILE")
    val hasReleaseSigning = releaseStoreFile != null && file(releaseStoreFile).exists()
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    lint {
        disable += "UnsafeOptInUsageError"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)

    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.serialization.json)

    implementation(
        "com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0"
    )

    implementation(libs.coil.compose)
    implementation(libs.reorderable)
    // SAF DocumentFile helper（下载目录选择）
    implementation("androidx.documentfile:documentfile:1.0.1")
    // Gson for saving playback state
    implementation("com.google.code.gson:gson:2.10.1")
}
