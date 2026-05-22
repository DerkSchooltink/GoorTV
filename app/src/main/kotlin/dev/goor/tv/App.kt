package dev.goor.tv

import android.app.Application
import dev.goor.tv.di.appModule
import dev.goor.tv.network.AppSyncCoordinator
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.get

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@App)
            modules(appModule)
        }
        // Kick off background sync independent of any screen lifecycle so the
        // user opening directly to Guide or Settings still gets fresh data.
        get<AppSyncCoordinator>(AppSyncCoordinator::class.java).start()
    }
}
