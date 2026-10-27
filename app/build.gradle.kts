import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is read from a gitignored keystore.properties when present.
// F-Droid builds without that file, producing an unsigned APK it verifies against
// the published (signed) binary for reproducible builds.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasReleaseSigning = keystorePropertiesFile.exists()
val keystoreProperties = Properties().apply {
    if (hasReleaseSigning) keystorePropertiesFile.inputStream().use { load(it) }
}

android {
    namespace = "com.xnotes"
    compileSdk = 36
    // Pinned toolchain for the vendored native builds (F-Droid reproducibility).
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.xnotes"
        minSdk = 26
        targetSdk = 36
        // Personal builds count commits so every published build is newer than the last (the
        // in-app updater compares this); upstream F-Droid builds keep their own numbering.
        versionCode = 1000 + (runCatching { providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }.standardOutput.asText.get().trim().toInt() }.getOrDefault(0))
        versionName = "0.9.2"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_static"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            // The tree-sitter parser tables gzip ~6x: compressed packaging keeps the
            // APK at ~12MB instead of ~30MB, at the cost of extract-on-install.
            useLegacyPackaging = true
        }
    }

    // F-Droid rejects the AGP dependency-metadata block in the APK signing block.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        // The personal build signs with the same key the installed test builds were signed with,
        // so it installs over them and the notes stay. A copy of the key lives in C:/dev/xnotes-keys.
        create("pessoal") {
            storeFile = file("C:/dev/xnotes-keys/xnotes-assinatura.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        // Leonardo's everyday build: release code (no debugger, faster), the installed app's id and key.
        create("pessoal") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-pessoal"
            signingConfig = signingConfigs.getByName("pessoal")
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += listOf("release")
        }
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            // R8 shrinks/optimises release builds (see proguard-rules.pro for the few keeps). R8
            // output is reproducible (pinned AGP fixes the R8 version + mapping); the one
            // non-reproducible artefact, the baseline profile, is dropped below (see ArtProfile).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // The release lint pass takes most of a personal build and needs disk it may not have.
    lint {
        checkReleaseBuilds = false
    }

    // Lists every values-* translation in the manifest so Android 13+ offers a per-app language.
    androidResources {
        generateLocaleConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// F-Droid runs verified builds: its from-source APK must byte-match the released one. AGP's
// auto-merged baseline profile (assets/dexopt/baseline.prof, built from the Compose/AndroidX
// library profiles) is not reproducible across build hosts, so omit it from release builds.
// Tradeoff: slightly slower first-run startup (no ART pre-warm profile).
tasks.configureEach {
    if (name.contains("ArtProfile")) enabled = false
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.pdfbox.android)
    implementation(libs.androidsvg)
    implementation(libs.latex.base)
    implementation(libs.latex.parser)
    implementation(libs.latex.renderer)
    // Toolkit additions. The ML Kit pieces are Google libraries: handwriting to text (models
    // downloaded once, then offline), text in images (bundled, offline) and the document scanner
    // (Play services). Motion prediction draws the live stroke a little ahead of the pen.
    implementation("com.google.mlkit:digital-ink-recognition:19.0.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    implementation("androidx.input:input-motionprediction:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.json)
}
