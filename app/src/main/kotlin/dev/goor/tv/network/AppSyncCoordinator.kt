package dev.goor.tv.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Owns the initial and ongoing background sync of sources + EPG. Triggered once
 * from `App.onCreate`, decoupled from any screen lifecycle so opening the app
 * directly to Guide or Settings still kicks off a sync — previously this was
 * wired into `HomeViewModel.init` and silently no-op'd when the user landed
 * elsewhere first.
 *
 * Both services own their own throttle + retry policy; this coordinator just
 * runs them in order on a process-scoped supervised scope. Failures in one
 * service don't cancel the other.
 */
class AppSyncCoordinator(
    private val sourceSyncService: SourceSyncService,
    private val epgSyncService: EpgSyncService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            runCatching { sourceSyncService.syncAll() }
                .onFailure { Log.e(TAG, "Source sync failed at top level: ${it.message}", it) }
            runCatching { epgSyncService.syncAll() }
                .onFailure { Log.e(TAG, "EPG sync failed at top level: ${it.message}", it) }
        }
    }

    companion object {
        private const val TAG = "AppSyncCoordinator"
    }
}
