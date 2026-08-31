import com.android.build.api.artifact.SingleArtifact
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Workaround: AGP 9's built-in Kotlin (2.2.10) makes the Compose compiler plugin
// request org.jetbrains.kotlin:compose-group-mapping:2.2.10, which was never published
// (the artifact starts at 2.3.0). Force it to the Compose plugin's actual version.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && requested.name == "compose-group-mapping") {
            useVersion("2.4.10")
        }
    }
}

android {
    namespace = "ss.colytitse.setappfull"
    compileSdk = 37

    defaultConfig {
        applicationId = "ss.colytitse.setappfull"
        minSdk = 28
        targetSdk = 37
        versionCode = 136
        versionName = "1.3.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants { variant ->
        val apkDir = variant.artifacts.get(SingleArtifact.APK)
        val versionName = android.defaultConfig.versionName ?: "0"
        val cap = variant.name.replaceFirstChar { it.uppercase() }

        val rename = tasks.register("renameApk$cap") {
            inputs.dir(apkDir)
            doLast {
                val dir = apkDir.get().asFile
                val apk = dir.listFiles()?.firstOrNull { it.extension == "apk" } ?: return@doLast
                val meta = File(dir, "output-metadata.json")

                val stamp = SimpleDateFormat("yyMMddHHmm")
                    .apply { timeZone = TimeZone.getTimeZone("GMT+8") }
                    .format(Date())
                val newName = "setappfull_${versionName}_$stamp.apk"

                if (apk.name != newName) {
                    apk.renameTo(File(dir, newName))
                    // Keep output-metadata.json in sync so install / AS can still locate the APK.
                    if (meta.exists()) {
                        meta.writeText(
                            meta.readText().replace(
                                "\"outputFile\": \"${apk.name}\"",
                                "\"outputFile\": \"$newName\""
                            )
                        )
                    }
                }
            }
        }
        tasks.matching { it.name == "assemble$cap" }.configureEach { finalizedBy(rename) }
    }
}

dependencies {
    compileOnly(libs.libxposed)
    implementation(libs.libxposed.service)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
