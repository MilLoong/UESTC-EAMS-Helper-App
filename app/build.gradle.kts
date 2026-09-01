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
        versionCode = 41
        versionName = "1.3.0"

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
            val source = apkDir.resolve("app-debug.apk")
            if (!source.isFile) return@doLast
            val dest = apkDir.resolve("UESTC-EAMS-Helper.apk")
            source.copyTo(dest, overwrite = true)
        }
    }

    tasks.register("installDebugKeepSession") {
        group = "install"
        description = "覆盖安装 debug APK，不清登录数据（adb install -r）"
        dependsOn("installDebug")
    }

    val adbPath =
        (System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
            ?: "${System.getProperty("user.home")}/Android/Sdk") + "/platform-tools/adb"
    val debugPkg = android.defaultConfig.applicationId ?: "com.milloong.uestc.Helper"
    val sessionBackupDir = rootProject.layout.projectDirectory.dir(".local/emulator-session")
    val sessionPrefFiles =
        listOf(
            "uestc_okhttp_cookie_snapshot_v1.xml",
            "academic_cache.xml",
        )

    fun runAdb(vararg args: String): String {
        val proc =
            ProcessBuilder(listOf(adbPath) + args.toList())
                .redirectErrorStream(true)
                .start()
        val out = proc.inputStream.bufferedReader().readText()
        val code = proc.waitFor()
        if (code != 0) {
            error("adb ${args.joinToString(" ")} failed ($code): $out")
        }
        return out
    }

    tasks.register("backupEmulatorSession") {
        group = "install"
        description = "从模拟器备份登录 Cookie 与课表缓存到 .local/emulator-session/"
        doLast {
            val dir = sessionBackupDir.asFile
            dir.mkdirs()
            sessionPrefFiles.forEach { name ->
                val dest = dir.resolve(name)
                val proc =
                    ProcessBuilder(adbPath, "exec-out", "run-as", debugPkg, "cat", "shared_prefs/$name")
                        .start()
                val bytes = proc.inputStream.readBytes()
                val err = proc.errorStream.bufferedReader().readText()
                val code = proc.waitFor()
                if (code != 0 || bytes.isEmpty()) {
                    println("skip $name: ${err.ifBlank { "empty (code=$code)" }}")
                    return@forEach
                }
                dest.writeBytes(bytes)
                println("backed up $name (${bytes.size} bytes) -> ${dest.path}")
            }
        }
    }

    tasks.register("restoreEmulatorSession") {
        group = "install"
        description = "把 .local/emulator-session/ 写回模拟器（卸载重装后用，无需再登录）"
        doLast {
            val dir = sessionBackupDir.asFile
            sessionPrefFiles.forEach { name ->
                val src = dir.resolve(name)
                if (!src.isFile) {
                    println("skip $name: no local backup")
                    return@forEach
                }
                val tmp = "/data/local/tmp/$name"
                runAdb("push", src.absolutePath, tmp)
                runAdb("shell", "chmod", "644", tmp)
                runAdb("shell", "run-as", debugPkg, "cp", tmp, "shared_prefs/$name")
                println("restored $name")
            }
            runAdb("shell", "am", "force-stop", debugPkg)
            println("session restored; relaunch the app")
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
    implementation(libs.calendar.compose)
    implementation(libs.sheets.core)
    implementation(libs.sheets.input)

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
