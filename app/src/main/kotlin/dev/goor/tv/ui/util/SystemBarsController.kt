package dev.goor.tv.ui.util

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Side-effect seam for fullscreen screens that want to hide system bars and
 * keep the screen on while active. The default impl pokes
 * `activity.window` directly; tests pass [NoOpSystemBarsController] so the
 * Compose host activity doesn't get its insets reshuffled mid-composition.
 *
 * Without this seam, `WindowInsetsControllerCompat.hide(systemBars())` racing
 * against the test rule's compose owner tears the composition down — see
 * commit history for the original investigation.
 */
interface SystemBarsController {
    /** Hide status + nav bars and apply `FLAG_KEEP_SCREEN_ON`. Idempotent. */
    fun hideAndKeepScreenOn()
    /** Re-show system bars and clear `FLAG_KEEP_SCREEN_ON`. Idempotent. */
    fun restore()
}

private class ActivitySystemBarsController(private val activity: Activity) : SystemBarsController {
    private val window get() = activity.window
    private val controller: WindowInsetsControllerCompat
        get() = WindowCompat.getInsetsController(window, window.decorView)

    override fun hideAndKeepScreenOn() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun restore() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

/** No-op controller. Use in tests, previews, or non-Activity contexts. */
object NoOpSystemBarsController : SystemBarsController {
    override fun hideAndKeepScreenOn() = Unit
    override fun restore() = Unit
}

/**
 * Returns a controller bound to the current [Activity], or [NoOpSystemBarsController]
 * if the context isn't an Activity (Compose previews, some test hosts).
 */
@Composable
fun rememberSystemBarsController(): SystemBarsController {
    val activity = LocalActivity.current
    return remember(activity) {
        activity?.let { ActivitySystemBarsController(it) } ?: NoOpSystemBarsController
    }
}
