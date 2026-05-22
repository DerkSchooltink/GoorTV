package dev.goor.tv.network

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Owns the initial and ongoing background sync of sources + EPG. Triggered once
 * from `App.onCreate`, decoupled from any screen lifecycle so opening the app
 * directly to Guide or Settings still kicks off a sync — previously this was
 * wired into `HomeViewModel.init` and silently no-op'd when the user landed
 * elsewhere first.
 *
 * Both services own their own throttle + retry policy; this coordinator just
 * runs them in order on a supervised scope. Failures in one service don't
 * cancel the other. Unexpected top-level throwables (anything that escapes the
 * per-source retry inside the service) surface on [lastTopLevelError] so the
 * UI can show "we tried, it broke" instead of an unexplained empty list.
 */
class AppSyncCoordinator(
    private val sourceSyncService: SourceSyncService,
    private val epgSyncService: EpgSyncService,
    coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.IO,
) {
    private val scope = CoroutineScope(coroutineContext)

    private val _lastTopLevelError = MutableStateFlow<Throwable?>(null)
    val lastTopLevelError: StateFlow<Throwable?> = _lastTopLevelError.asStateFlow()

    fun start(): Job = scope.launch {
        runStage("Source sync") { sourceSyncService.syncAll() }
        runStage("EPG sync") { epgSyncService.syncAll() }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runStage(label: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.e(TAG, "$label failed at top level: ${t.message}", t)
            _lastTopLevelError.value = t
        }
    }

    fun stop() {
        scope.cancel()
    }

    companion object {
        private const val TAG = "AppSyncCoordinator"
    }
}
