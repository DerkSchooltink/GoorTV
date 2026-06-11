package dev.goor.tv.network

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SyncExceptionTest {

    @Test
    fun `HTTP 5xx and 429 are retriable`() {
        assertTrue(SyncException.Http(500, "boom").isRetriableSyncError())
        assertTrue(SyncException.Http(503, "boom").isRetriableSyncError())
        assertTrue(SyncException.Http(429, "slow down").isRetriableSyncError())
    }

    @Test
    fun `HTTP 4xx client errors are not retriable`() {
        assertFalse(SyncException.Http(400, "bad request").isRetriableSyncError())
        assertFalse(SyncException.Http(401, "bad credentials").isRetriableSyncError())
        assertFalse(SyncException.Http(404, "not found").isRetriableSyncError())
    }

    @Test
    fun `TooLarge is not retriable`() {
        assertFalse(SyncException.TooLarge("playlist too big").isRetriableSyncError())
    }

    @Test
    fun `SerializationException is not retriable`() {
        assertFalse(SerializationException("malformed payload").isRetriableSyncError())
    }

    @Test
    fun `arbitrary exceptions keep the retry-everything behavior`() {
        assertTrue(IOException("network unreachable").isRetriableSyncError())
        assertTrue(RuntimeException("who knows").isRetriableSyncError())
    }
}
