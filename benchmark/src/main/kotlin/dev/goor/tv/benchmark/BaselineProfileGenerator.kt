package dev.goor.tv.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline profile consumed by `:app` (A2.7). Generation runs on
 * the `profileGen` managed device (AOSP ATD). The result is written to
 * `app/src/<variant>/generated/baselineProfiles/`. Run with:
 *
 * ```
 * ./gradlew :app:generateBaselineProfile \
 *   -Pandroid.testInstrumentationRunnerArguments.class=dev.goor.tv.benchmark.BaselineProfileGenerator \
 *   -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
 * ```
 *
 * The `class=` filter is needed because the module also holds [ChannelListBenchmark],
 * a macrobench that can't trace on an emulator and would otherwise fail the run.
 *
 * The journey mirrors `ChannelListBenchmark`: cold start into the channel list,
 * then scroll, so the profile covers the hottest startup + first-scroll paths.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(packageName = "dev.goor.tv") {
        pressHome()
        startActivityAndWait()
        device.waitForIdle(3_000)

        val list = device.findObject(By.scrollable(true))
        if (list != null) {
            list.setGestureMargin(device.displayWidth / 5)
            repeat(3) {
                list.fling(Direction.DOWN)
                device.waitForIdle(500)
            }
            list.fling(Direction.UP)
            device.waitForIdle(500)
        }
    }
}
