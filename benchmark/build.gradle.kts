plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
}

android {
    namespace = "dev.goor.tv.benchmark"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // Macrobenchmark + baseline-profile run OUT of process against :app, so
        // they need the standard runner. AndroidBenchmarkRunner is for in-process
        // MICRObenchmarks and forces every test through IsolationActivity (which
        // can't launch here), so it's wrong for this module.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("release")
        }
    }

    // Gradle Managed Device for reproducible baseline-profile generation (A2.7).
    // AOSP ATD image — Google's recommended target for profile capture in CI.
    testOptions {
        managedDevices {
            localDevices {
                create("profileGen") {
                    device = "Pixel 6"
                    apiLevel = 34
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }
}

// Generate the baseline profile on the managed device above, not whatever
// happens to be plugged in.
baselineProfile {
    managedDevices += "profileGen"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.benchmark.junit4)
    implementation(libs.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.uiautomator)
}
