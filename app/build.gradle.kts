import java.io.BufferedReader

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Some Android Studio / IntelliJ builds still ask the app module for this sync task.
tasks.register("prepareKotlinBuildScriptModel")

android {
    namespace = "com.mytv0"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mytv0"
        minSdk = 24
        targetSdk = 35
        versionCode = getVersionCode()
        versionName = getVersionName()
    }

    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        // Flag to enable support for the new language APIs
        // For AGP 4.1+
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

fun getTag(): String {
    return try {
        val process = ProcessBuilder("git", "describe", "--tags", "--always")
            .redirectErrorStream(true)
            .start()
        process.waitFor()
        process.inputStream.bufferedReader().use(BufferedReader::readText).trim().removePrefix("v")
    } catch (_: Exception) {
        ""
    }
}

fun getVersionCode(): Int {
    return try {
        val arr = (getTag().replace(".", " ").replace("-", " ") + " 0").split(" ")
        arr[0].toInt() * 16777216 + arr[1].toInt() * 65536 + arr[2].toInt() * 256 + arr[3].toInt()
    } catch (_: Exception) {
        1
    }
}

fun getVersionName(): String {
    return getTag().ifEmpty {
        "0.0.0-1"
    }
}

dependencies {
    // For AGP 7.4+
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.glide)

    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.core.ktx)
    implementation(libs.coroutines)

    implementation(libs.constraintlayout)
    implementation(libs.appcompat)
    implementation(libs.recyclerview)
    implementation(libs.lifecycle.viewmodel)
}
