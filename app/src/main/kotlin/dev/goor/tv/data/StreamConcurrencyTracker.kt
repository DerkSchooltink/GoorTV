package dev.goor.tv.data

class StreamConcurrencyTracker {
    private val lock = Any()
    private val activeStreams = mutableMapOf<Long, MutableList<() -> Unit>>()

    fun register(sourceId: Long, maxConcurrent: Int, onForceStop: () -> Unit): () -> Unit {
        if (maxConcurrent <= 0) return {}
        synchronized(lock) {
            val streams = activeStreams.getOrPut(sourceId) { mutableListOf() }
            streams.add(onForceStop)
            while (streams.size > maxConcurrent) {
                streams.removeAt(0)()
            }
        }
        return {
            synchronized(lock) {
                activeStreams[sourceId]?.remove(onForceStop)
            }
        }
    }
}
