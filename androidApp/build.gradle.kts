import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.room.runtime)
    // Align app/test serialization: Room's schema test helper requires JSON/core 1.8.1.
    implementation(platform(libs.kotlinx.serialization.bom))
    implementation(libs.kotlinx.coroutines.android)
    ksp(libs.androidx.room.compiler)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.testExt.junit)
    androidTestImplementation(libs.androidx.testRunner)
    androidTestImplementation(libs.androidx.room.testing)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

room {
    schemaDirectory("$projectDir/schemas")
}

android {
    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
    namespace = "com.example.manymanager"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.example.manymanager"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "com.example.manymanager.DatabaseTestRunner"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}