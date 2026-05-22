plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt)
}

// Static analysis. Single config + baseline at repo root, applied to every Kotlin
// source set. Baseline captures pre-existing violations so the CI gate stays
// meaningful from day one — new issues fail, grandfathered ones don't.
allprojects {
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)
    extensions.configure(io.gitlab.arturbosch.detekt.extensions.DetektExtension::class.java) {
        toolVersion = rootProject.libs.versions.detekt.get()
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        baseline = rootProject.file("config/detekt/baseline.xml")
        buildUponDefaultConfig = true
        source.setFrom(files("src/main/kotlin", "src/test/kotlin", "src/androidTest/kotlin"))
    }
}
