package dev.goor.tv.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastContext

/**
 * Cast routes through Google Play Services' dynamically-loaded "cast.framework"
 * module. On AOSP TV builds, Fire TV, de-Googled phones, and most Chinese
 * Android TV boxes that module is missing — calling
 * [CastContext.getSharedInstance] there throws
 * [com.google.android.gms.cast.framework.ModuleUnavailableException]
 * which is uncaught and kills the process.
 *
 * GoogleApiAvailability isn't enough: AOSP TV images can ship the Play Store
 * (so GMS overall is "available") while still lacking the Cast dynamite
 * module. The only reliable probe is to actually attempt the init.
 *
 * Result is cached process-wide — the probe is sticky and the dynamite
 * module won't appear mid-session.
 */
object CastAvailability {
    @Volatile private var cached: Boolean? = null

    fun isAvailable(context: Context): Boolean = cached ?: synchronized(this) {
        cached ?: runCatching { CastContext.getSharedInstance(context) }
            .isSuccess
            .also { cached = it }
    }
}

fun Context.isCastAvailable(): Boolean = CastAvailability.isAvailable(this)
