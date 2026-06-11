package dev.goor.tv.network

import kotlinx.serialization.SerializationException

/**
 * Typed sync failure that tells the retry loops whether retrying can plausibly help.
 *
 * Transient failures (server errors, rate limiting) keep the exponential-backoff
 * retry behavior; permanent ones (client errors, oversized payloads) fail fast
 * instead of burning every attempt on an outcome that cannot change.
 */
sealed class SyncException(
    message: String,
    val isRetriable: Boolean,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** Non-success HTTP status. Server errors and rate limiting are worth retrying; client errors are not. */
    class Http(val statusCode: Int, message: String) :
        SyncException(message, isRetriable = statusCode >= 500 || statusCode == 429)

    /** Download exceeded the size cap — permanent until the source itself changes. */
    class TooLarge(message: String) : SyncException(message, isRetriable = false)
}

/**
 * Retry-worthiness of an arbitrary sync failure. [SyncException] carries its own
 * verdict; a [SerializationException] means a malformed API payload that won't fix
 * itself; anything else keeps the old retry-everything behavior.
 */
internal fun Throwable.isRetriableSyncError(): Boolean = when (this) {
    is SyncException -> isRetriable
    is SerializationException -> false
    else -> true
}

/**
 * One source's terminal sync failure, as returned by the `syncAll` methods.
 * Carries the source name and the typed error separately so the UI can build a
 * localized message instead of showing raw exception text.
 */
data class SyncFailure(val sourceName: String, val error: Throwable)
