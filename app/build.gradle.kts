import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.r_a_d.geiravor"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "io.r_a_d.geiravor"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        optIn.add("androidx.media3.common.util.UnstableApi")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("net.java.dev.jna:jna:5.17.0@aar")
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.coil.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
}

val rustLibName = "geiravor_core"
val cargoManifest = rootProject.file("core/Cargo.toml")
val jniDir = layout.projectDirectory.dir("src/main/jniLibs")
val generatedKotlin = layout.buildDirectory.dir("generated/uniffi")

tasks.register<Exec>("cargoNdkBuild") {
    group = "build"
    description = "Build Rust UniFFI library for Android ABIs"
    workingDir = cargoManifest.parentFile
    commandLine(
        "cargo",
        "ndk",
        "-t", "arm64-v8a",
        "-t", "armeabi-v7a",
        "-t", "x86_64",
        "--platform", "26",
        "-o", jniDir.asFile.absolutePath,
        "build",
        "--release",
    )
    doFirst { jniDir.asFile.mkdirs() }
}

tasks.register<Exec>("generateUniffi") {
    group = "build"
    dependsOn("cargoNdkBuild")
    val out = generatedKotlin.get().asFile
    workingDir = rootProject.projectDir
    commandLine(
        "cargo",
        "run",
        "--quiet",
        "--bin", "uniffi-bindgen",
        "--manifest-path", cargoManifest.absolutePath,
        "generate",
        "--library", jniDir.file("arm64-v8a/lib${rustLibName}.so").asFile.absolutePath,
        "--language", "kotlin",
        "--no-format",
        "--out-dir", out.absolutePath,
    )
    doFirst { out.mkdirs() }
}

android.sourceSets.getByName("main") {
    java.srcDir(generatedKotlin)
}

tasks.named("preBuild") {
    dependsOn("generateUniffi")
}
