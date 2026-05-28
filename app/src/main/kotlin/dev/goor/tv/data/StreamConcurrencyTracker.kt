package dev.goor.tv.data

class StreamConcurrencyTracker {
    private val lock = Any()
    private val activeStreams = mutableMapOf<Long, MutableList<() -> Unit>>()

    /**
     * Registers a stream against [sourceId]'s [maxConcurrent] limit
     * (`<= 0` means unlimited). When the source is already at capacity the *new*
     * stream is refused — [onForceStop] fires for it — rather than evicting an
     * existing one. This mirrors how the provider itself rejects the surplus
     * connection, and means the stream the user just tried to open is the one
     * that reports the limit (where we can actually show feedback), instead of
     * silently killing a stream they can't see.
     *
     * Returns an unregister callback; it's a no-op when the stream was refused
     * or when the source is unlimited.
     */
    fun register(sourceId: Long, maxConcurrent: Int, onForceStop: () -> Unit): () -> Unit {
        if (maxConcurrent <= 0) return {}
        val accepted = synchronized(lock) {
            val streams = activeStreams.getOrPut(sourceId) { mutableListOf() }
            if (streams.size >= maxConcurrent) {
                false
            } else {
                streams.add(onForceStop)
                true
            }
        }
        if (!accepted) {
            onForceStop()
            return {}
        }
        return {
            synchronized(lock) {
                activeStreams[sourceId]?.remove(onForceStop)
            }
        }
    }
}
