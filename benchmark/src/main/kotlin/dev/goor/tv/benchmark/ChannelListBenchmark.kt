package dev.goor.tv.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChannelListBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollChannelListCold() = scroll(StartupMode.COLD)

    @Test
    fun scrollChannelListWarm() = scroll(StartupMode.WARM)

    private fun scroll(startupMode: StartupMode) = benchmarkRule.measureRepeated(
        packageName = "dev.goor.tv",
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = startupMode,
        iterations = 5,
        setupBlock = {
            pressHome()
        },
    ) {
        startActivityAndWait()
        device.waitForIdle(3_000)

        val list = device.findObject(By.scrollable(true))
        if (list != null) {
            list.setGestureMargin(device.displayWidth / 5)
            repeat(5) {
                list.fling(Direction.DOWN)
                device.waitForIdle(500)
            }
            repeat(5) {
                list.fling(Direction.UP)
                device.waitForIdle(500)
            }
        }
    }
}
