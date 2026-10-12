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
                    apiLevel = 35
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
    // androidx.benchmark 1.5.0 pulls wire-runtime 6.4.0 for trace parsing,
    // which is vulnerable to CVE-2026-63126 (decoder crash on malformed
    // input). Declaring the patched 6.4.x release makes Gradle's
    // highest-version-wins pick it instead; drop once benchmark ships the fix.
    implementation(libs.wire.runtime)

    implementation(libs.benchmark.junit4)
    implementation(libs.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.uiautomator)
}
