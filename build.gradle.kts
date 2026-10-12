// Build-classpath security pins. AGP's tooling (apk signing, jetifier, lint,
// bundletool) pulls these libraries in at versions that predate their security
// fixes. They are build-time only — they never reach the APK — but they do run
// inside Gradle on developer machines and CI, and Dependabot tracks them, so
// pin the patched releases here. Drop an entry once AGP depends on the fixed
// version by itself. Keep in sync with the androidLintTool override below.
// Alerts: https://github.com/DerkSchooltink/GoorTV/security/dependabot
buildscript {
    configurations.named("classpath") {
        resolutionStrategy.force(
            "org.bouncycastle:bcprov-jdk18on:1.86", // CVE-2026-8763, CVE-2026-13506, CVE-2026-0636
            "org.bouncycastle:bcpkix-jdk18on:1.86", // CVE-2026-5588
            "org.bitbucket.b_c:jose4j:0.9.7", // CVE-2024-29371
            "org.jdom:jdom2:2.0.6.1", // CVE-2021-33813
            "org.apache.commons:commons-lang3:3.21.0", // CVE-2025-48924
            "org.apache.httpcomponents:httpclient:4.5.14", // CVE-2020-13956
        )
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.detekt)
}

// Static analysis. Single config + baseline at repo root, applied to every Kotlin
// source set. Baseline captures pre-existing violations so the CI gate stays
// meaningful from day one — new issues fail, grandfathered ones don't.
allprojects {
    // Lint assembles the same com.android.tools stack in its own configuration;
    // mirror the buildscript pins above so `lintDebug` also runs patched libs.
    configurations.matching { it.name == "androidLintTool" }.configureEach {
        resolutionStrategy.force(
            "org.bouncycastle:bcprov-jdk18on:1.86",
            "org.bouncycastle:bcpkix-jdk18on:1.86",
            "org.bitbucket.b_c:jose4j:0.9.7",
            "org.jdom:jdom2:2.0.6.1",
            "org.apache.commons:commons-lang3:3.21.0",
            "org.apache.httpcomponents:httpclient:4.5.14",
        )
    }

    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)
    extensions.configure(io.gitlab.arturbosch.detekt.extensions.DetektExtension::class.java) {
        toolVersion = rootProject.libs.versions.detekt.get()
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        baseline = rootProject.file("config/detekt/baseline.xml")
        buildUponDefaultConfig = true
        source.setFrom(files("src/main/kotlin", "src/test/kotlin", "src/androidTest/kotlin"))
    }
}
