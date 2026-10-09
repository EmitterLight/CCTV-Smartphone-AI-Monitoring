import java.security.MessageDigest
import java.util.LinkedHashMap

plugins {
    alias(libs.plugins.android.application)
}

val versionFile = rootProject.file("../version.properties")
val envFile = rootProject.file("../.env")

val env = (if (envFile.exists()) {
    envFile.readText().lines().map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull {
            if (!it.contains('=')) return@mapNotNull null
            val key = it.substringBefore('=')
            val value = it.substringAfter('=')
            key.trim() to value.trim().trim('"')
        }.toMap()
} else emptyMap()) + System.getenv()

val versionProperties = LinkedHashMap<String, String>()
versionFile.readText().lines().forEach { rawLine ->
    val line = rawLine.trim()
    if (line.isEmpty() || line.startsWith("#")) return@forEach
    if (!line.contains('=')) return@forEach
    val key = line.substringBefore('=')
    val value = line.substringAfter('=')
    versionProperties[key.trim()] = value.trim()
}

fun saveVersionProperties() {
    versionFile.writeText(versionProperties.entries.joinToString("\n") { "${it.key}=${it.value}" } + "\n")
}

val packagingRequested = gradle.startParameter.taskNames.any { it.contains("assemble") || it.contains("install") }
val previousApkSha256 = versionProperties.getValue("camflow.apkSha256")

fun bumpVersionCode() {
    versionProperties["camflow.versionCode"] = (versionProperties.getValue("camflow.versionCode").toInt() + 1).toString()
    saveVersionProperties()
}

if (packagingRequested) bumpVersionCode()

val camflowVersion = versionProperties.getValue("camflow.version")
val camflowVersionCode = versionProperties.getValue("camflow.versionCode").toInt()

val keystoreFile = rootProject.file("camflow.keystore")
val keystorePassword = env["CAMFLOW_KEYSTORE_PASSWORD"]
val keystoreAlias = env["CAMFLOW_KEYSTORE_ALIAS"]

if (packagingRequested && (keystorePassword == null || keystoreAlias == null)) {
    error("Signing credentials are missing. Set CAMFLOW_KEYSTORE_PASSWORD and CAMFLOW_KEYSTORE_ALIAS in .env (repository root).")
}

android {
    namespace = "com.example.phonecamsender"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.phonecamsender"
        minSdk = 26
        targetSdk = 36
        versionCode = camflowVersionCode
        versionName = camflowVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePassword != null && keystoreAlias != null) {
            create("camflow") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyPassword = keystorePassword
                keyAlias = keystoreAlias
            }
        }
    }

    buildTypes {
        debug {
            if (signingConfigs.findByName("camflow") != null) {
                signingConfig = signingConfigs.getByName("camflow")
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (signingConfigs.findByName("camflow") != null) {
                signingConfig = signingConfigs.getByName("camflow")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    val cameraxVersion = "1.3.4"

    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}

tasks.register("bumpVersionCode") {
    doLast {
        bumpVersionCode()
    }
}

tasks.register("updateApkMetadata") {
    doLast {
        val apkDir = layout.buildDirectory.dir("outputs/apk").get().asFile
        val apk = apkDir.walkTopDown()
            .filter { it.isFile && it.extension == "apk" && !it.path.contains("androidTest") }
            .maxByOrNull { it.lastModified() }
            ?: error("No APK found in $apkDir")
        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(apk.readBytes())
            .joinToString("") { "%02x".format(it) }
            .uppercase()

        val canonicalApk = rootProject.file("CamFlow-v$camflowVersion.apk")
        apk.copyTo(canonicalApk, overwrite = true)

        versionProperties["camflow.apkPath"] = "PhoneCamSender/${canonicalApk.name}"
        versionProperties["camflow.apkSha256"] = sha256
        saveVersionProperties()

        for (name in listOf("README.md", "README_CN.md", "CamFlow_UserGuide.md", "CamFlow_UserGuide_CN.md")) {
            val readme = rootProject.file("../$name")
            if (!readme.exists()) continue
            val original = readme.readText()
            val updated = original.lines().map { line ->
                if (line.contains(previousApkSha256) || line.contains(canonicalApk.name)) {
                    line.replace(previousApkSha256, sha256).replace(Regex("build \\d+"), "build $camflowVersionCode")
                } else {
                    line
                }
            }.joinToString("\n")
            if (updated != original) readme.writeText(updated)
        }
    }
}

afterEvaluate {
    for (name in listOf("assembleDebug", "assembleRelease")) {
        tasks.findByName(name)?.finalizedBy("updateApkMetadata")
    }
}
