import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val uploadKeystorePropertiesFile = rootProject.file("keystore.properties")
val uploadKeystoreProperties =
    Properties().apply {
        if (uploadKeystorePropertiesFile.exists()) {
            uploadKeystorePropertiesFile.inputStream().use { load(it) }
        }
    }

fun uploadSigningReady(): Boolean {
    if (!uploadKeystorePropertiesFile.exists()) return false
    val path = uploadKeystoreProperties.getProperty("storeFile")?.trim().orEmpty()
    if (path.isEmpty()) return false
    return rootProject.file(path).isFile
}

android {
    namespace = "edu.uestc.eams.helper"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.milloong.uestc.Helper"
        minSdk = 24
        targetSdk = 36
        versionCode = 29
        versionName = "1.1.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("upload") {
            if (uploadSigningReady()) {
                val storePass = uploadKeystoreProperties.getProperty("storePassword")
                storeFile = rootProject.file(uploadKeystoreProperties.getProperty("storeFile")!!.trim())
                storePassword = storePass
                keyAlias = uploadKeystoreProperties.getProperty("keyAlias")
                keyPassword = storePass
            }
        }
    }

    buildTypes {
        debug {
            if (uploadSigningReady()) {
                signingConfig = signingConfigs.getByName("upload")
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (uploadSigningReady()) {
                signingConfig = signingConfigs.getByName("upload")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

afterEvaluate {
    listOf(
        "packageRelease",
        "assembleRelease",
        "createReleaseApkListingFileRedirect",
    ).forEach { taskName ->
        tasks.findByName(taskName)?.let { task ->
            task.enabled = false
        }
    }

    tasks.register("printReleaseTag") {
        group = "release"
        description = "Print git tag commands matching versionName in this module"
        doLast {
            val v = android.defaultConfig.versionName
            val code = android.defaultConfig.versionCode
            println("versionName=$v versionCode=$code")
            println("git tag v$v")
            println("git push origin v$v")
        }
    }

    tasks.named("assembleDebug") {
        doLast {
            val apkDir = layout.buildDirectory.dir("outputs/apk/debug").get().asFile
            if (!apkDir.isDirectory) return@doLast
            val source =
                apkDir.listFiles()
                    ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
                    ?.maxByOrNull { it.lastModified() }
                    ?: return@doLast
            val dest = apkDir.resolve("UESTC-EAMS-Helper.apk")
            if (source.absolutePath != dest.absolutePath) {
                source.copyTo(dest, overwrite = true)
                source.delete()
            }
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation("androidx.compose.foundation:foundation")
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.work.runtime)
    implementation(libs.gson)

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.okhttp)
    implementation(libs.okhttp.brotli)
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.kotlin.coroutines.android)
    implementation(libs.fragment.ktx)
    implementation(libs.lifecycle.runtime.ktx)

    testImplementation(platform(libs.compose.bom))
    testImplementation("androidx.compose.ui:ui-text")
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlin.coroutines.core)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
