plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("app.cash.paparazzi")
}

// Public releases are built unsigned, then signed in the protected release job.
// Gradle and pull-request builds never receive the release private key.
val distribution = providers.gradleProperty("distribution").orNull == "true"
val verifying = providers.gradleProperty("streamingVerification").orNull == "true"
require(!(distribution && verifying)) { "Verification builds cannot be distributed as releases." }

android {
    namespace = "com.attunetv.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.attunetv.launcher"
        minSdk = 25          // Fire OS 6+
        targetSdk = 34
        versionCode = 10
        versionName = "0.4.2"
        buildConfigField("boolean", "STREAMING_VERIFICATION", verifying.toString())
        if (verifying) versionNameSuffix = "-verify"
        else if (!distribution) versionNameSuffix = "-dev"
    }
    buildFeatures { buildConfig = true }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (!distribution) signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.zxing:core:3.5.3")  // QR code for phone setup
    testImplementation("junit:junit:4.13.2")
}
