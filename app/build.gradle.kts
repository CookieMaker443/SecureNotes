plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.cookie.securenotes"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cookie.securenotes"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // UI e Base
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.activity.ktx)
    implementation(libs.fragment)

    implementation(libs.viewpager2)

    // Lifecycle / ViewModel (MVVM)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    // Room (Database)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // SQLCipher (Database Criptato)
    implementation(libs.sqlcipher)
    implementation(libs.sqlite)

    // Jetpack Security
    implementation(libs.security.crypto)

    // Biometric
    implementation(libs.biometric)

    // WorkManager
    implementation(libs.work.runtime)

    // Media3/ExoPlayer: motore di riproduzione video, sostituisce VideoView perché
    // supporta un DataSource personalizzato (necessario per lo streaming decifrato al volo)
    implementation(libs.media3.exoplayer)
    // Media3 UI: PlayerView (superficie video) e controlli di riproduzione pronti all'uso
    implementation(libs.media3.ui)
    // Media3 DataSource: classi base (BaseDataSource, DataSpec) usate da EncryptedFileDataSource
    implementation(libs.media3.datasource)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}