plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.sentry)
    alias(libs.plugins.baselineprofile)
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "dev.goor.tv"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.goor.tv"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 11
        versionName = "1.5.0"

        // Sentry DSN is read from the env at build time. Empty value → Sentry
        // is not initialised at runtime (see App.onCreate), so local debug
        // builds without the env var never ship events.
        buildConfigField(
            "String",
            "SENTRY_DSN",
            "\"${System.getenv("SENTRY_DSN").orEmpty()}\"",
        )
    }

    signingConfigs {
        val storeFile = System.getenv("SIGNING_STORE_FILE")
        if (storeFile != null) {
            create("release") {
                this.storeFile = file(storeFile)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Prefer the env-provided release keystore (CI). Locally — and for
            // the baseline-profile generation variants derived from `release` —
            // fall back to debug signing so the APK is installable without
            // shipping secrets. CI sets SIGNING_STORE_FILE so it uses the real key.
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            // Default 512 MB OOMs once mockk has cached enough proxies across
            // the network/parser suites. 2 GB has plenty of headroom.
            it.maxHeapSize = "2g"
        }
    }

    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}


dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.datastore.preferences)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.appcompat)
    implementation(libs.cast.framework)
    implementation(libs.mediarouter)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.sentry.android)

    // Unit tests (JVM)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    // XmlPullParser implementation for JVM unit tests (Android provides one at runtime).
    testImplementation("net.sf.kxml:kxml2:2.3.0")

    // Instrumented tests (device/emulator)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.mockk.agent)
    androidTestImplementation(libs.room.testing)
    debugImplementation(libs.compose.ui.test.manifest)

    // Baseline profile produced by the :benchmark module (A2.7). The plugin
    // merges the generated profile into the release build for faster cold start.
    baselineProfile(project(":benchmark"))
}


// Sentry plugin handles the R8 mapping + (optionally) source-context upload so
// the dashboard can render readable stack traces. Manifest auto-init is left
// disabled — we init manually in App.kt so DSN and PII settings stay in code.
// Source-context + mapping uploads need SENTRY_AUTH_TOKEN at build time; when
// it's absent (forks, local builds) the plugin's upload tasks become no-ops
// and the build still succeeds.
sentry {
    org.set("derk-schooltink")
    projectName.set("android")
    autoInstallation { enabled.set(false) }
    autoUploadProguardMapping.set(System.getenv("SENTRY_AUTH_TOKEN") != null)
    includeProguardMapping.set(true)
    // OSS app — fine to expose sources alongside stack traces. Gated on the
    // auth token so local builds don't fail trying to upload.
    includeSourceContext.set(System.getenv("SENTRY_AUTH_TOKEN") != null)
    ignoredBuildTypes.set(setOf("debug", "benchmark"))
    tracingInstrumentation { enabled.set(false) }
}
