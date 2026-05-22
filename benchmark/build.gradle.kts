plugins {
    id("com.android.test")
}

android {
    namespace = "dev.goor.tv.benchmark"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
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
}

dependencies {
    implementation(libs.benchmark.junit4)
    implementation(libs.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.uiautomator)
}
