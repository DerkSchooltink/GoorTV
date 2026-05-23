package dev.goor.tv

import android.app.Application
import dev.goor.tv.di.appModule
import dev.goor.tv.network.AppSyncCoordinator
import io.sentry.android.core.SentryAndroid
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.get

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        initSentry()
        startKoin {
            androidContext(this@App)
            modules(appModule)
        }
        // Kick off background sync independent of any screen lifecycle so the
        // user opening directly to Guide or Settings still gets fresh data.
        get<AppSyncCoordinator>(AppSyncCoordinator::class.java).start()
    }

    // Init runs before Koin so crashes during DI wiring also surface. No-op
    // when the build was produced without a SENTRY_DSN env var, or for debug
    // builds (we don't want local crashes polluting the dashboard). The Gradle
    // plugin's manifest auto-init is disabled in build.gradle so behavior
    // stays in one place.
    private fun initSentry() {
        if (BuildConfig.SENTRY_DSN.isBlank() || BuildConfig.DEBUG) return
        SentryAndroid.init(this) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            options.environment = "release"
            // PII off: no IP capture, no source URL, no auto-screenshot. BYOC
            // playlist URLs and Xtream creds count as PII and must never leave
            // the device. Stack trace + device model + OS version only.
            options.isSendDefaultPii = false
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
            // We're paying for crashes, not traces or replays.
            options.tracesSampleRate = 0.0
            options.profilesSampleRate = 0.0
            options.sessionReplay.onErrorSampleRate = 0.0
            options.sessionReplay.sessionSampleRate = 0.0
        }
    }
}
