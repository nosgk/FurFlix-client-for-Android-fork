plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// CI release signing: values are injected as env vars by the workflow (see
// .github/workflows/build.yml). Without them (e.g. local builds) the release
// build stays unsigned.
val keystoreFile = System.getenv("KEYSTORE_FILE")
val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
val hasSigning = !keystoreFile.isNullOrEmpty() && !keystorePassword.isNullOrEmpty()

android {
    namespace = "com.furflix.app"
    compileSdk = 37

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(keystoreFile!!)
                storePassword = keystorePassword
                keyAlias = System.getenv("KEY_ALIAS") ?: "furflix-release"
                keyPassword = System.getenv("KEY_PASSWORD") ?: keystorePassword
                storeType = "PKCS12"
            }
        }
    }

    defaultConfig {
        applicationId = "com.furflix.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // CI release builds pass -Pabi=arm64-v8a; builds without the property stay universal
        (project.findProperty("abi") as? String)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.let { ndk { abiFilters += it } }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // Keep only the app's supported locales (drops ~80 unused AAR locale tables)
        localeFilters += listOf("en", "ru", "uk", "zh-rCN")
    }

    lint {
        // i18n audit is report-style: missing/extra translations surface in the lint
        // report but must not break CI during upstream rebase windows.
        warning += listOf("MissingTranslation", "ExtraTranslation")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            // PdfBox-Android 与 BouncyCastle 各自带了许可证/依赖清单，
            // 合并时同名冲突会让打包失败——这些元数据运行时用不到
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")

    implementation("dev.chrisbanes.haze:haze:2.0.1")
    implementation("dev.chrisbanes.haze:haze-blur:2.0.1")

    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-process:2.11.0")

    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-gif:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.4.0")

    implementation("org.jsoup:jsoup:1.22.2")

    // PDF 文本层抽取（文档查看器：复制/翻译 PDF 文字）。系统 PdfRenderer 只能
    // 栅格化页面、拿不到文字，故引 Apache PDFBox 的 Android 移植版（Apache 2.0）。
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // 音频播放内核：ExoPlayer 走系统 MediaCodec 硬解、默认按源采样率配置 AudioTrack；
    // PlaybackParameters 变速走 Sonic，只改时长不改音高（变速不变调）。media3-session
    // 提供 MediaSessionService：后台保活 + 系统媒体通知栏/锁屏控制。
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")

    // Baseline profile installer: applies the compiled profile at install/first-run
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // LeakCanary for memory leak detection
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")

    val roomVersion = "2.8.5"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Translation engine unit tests (pure JVM, no Android framework)
    testImplementation("junit:junit:4.13.2")
}
