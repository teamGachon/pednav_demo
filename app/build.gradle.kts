plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.modeltest"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.modeltest"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        dataBinding = true
    }
    buildToolsVersion = "34.0.0"
}


dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation(libs.tensorflow.lite)

    // for vsm sdk
    implementation("com.google.flatbuffers:flatbuffers-java:1.11.0")

    // Naver Map sdk
    implementation("com.naver.maps:map-sdk:3.20.0")


    // TFLite
    implementation("org.tensorflow:tensorflow-lite:2.12.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.3")
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.11.0")

    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Web Socket
    implementation ("org.java-websocket:Java-WebSocket:1.5.2")

    // Card View
    implementation ("androidx.cardview:cardview:1.0.0")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.6.0")
    implementation("com.squareup.retrofit2:converter-gson:2.6.0")

    implementation("com.google.android.material:material:1.11.0")

}