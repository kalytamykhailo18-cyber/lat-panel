import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val brand = Properties().apply { rootProject.file("brand.properties").inputStream().use { load(it) } }
// Any value can be overridden from the command line, e.g. ./gradlew assembleDebug -PPANEL_URL=http://10.0.2.2:8099/
fun brand(key: String): String =
    (findProperty(key) as String?) ?: brand.getProperty(key) ?: error("brand.properties: missing $key")

// Release signing is read from keystore.properties (not committed). See docs/BUILD.md.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.player.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = brand("APPLICATION_ID")
        minSdk = 23
        targetSdk = 36
        versionCode = brand("VERSION_CODE").toInt()
        versionName = brand("VERSION_NAME")

        resValue("string", "app_name", brand("APP_NAME"))
        buildConfigField("String", "PANEL_URL", "\"${brand("PANEL_URL")}\"")
        buildConfigField("String", "USER_AGENT", "\"${brand("USER_AGENT")}\"")
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        // Optional: keep Robolectric's downloads and temp files in a given folder instead of the
        // user's home (-PtestUserHome=... -PtestTmpDir=...). Unset = default behaviour.
        unitTests.all { test ->
            (findProperty("testUserHome") as String?)?.let { test.systemProperty("user.home", it) }
            (findProperty("testTmpDir") as String?)?.let { test.systemProperty("java.io.tmpdir", it) }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.tv.material)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
