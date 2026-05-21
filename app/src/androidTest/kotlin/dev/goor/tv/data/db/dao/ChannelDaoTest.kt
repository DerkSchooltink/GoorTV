package dev.goor.tv.data.db.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.goor.tv.data.db.AppDatabase
import dev.goor.tv.data.model.Channel
import dev.goor.tv.data.model.Source
import dev.goor.tv.data.model.SourceType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChannelDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var sourceDao: SourceDao
    private lateinit var channelDao: ChannelDao
    private var sourceId: Long = 0

    @Before
    fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        sourceDao = db.sourceDao()
        channelDao = db.channelDao()
        sourceId = sourceDao.insert(
            Source(name = "Test", type = SourceType.M3U, url = "http://example.com")
        )
    }

    @After
    fun teardown() { db.close() }

    @Test
    fun replaceForSourcePreservingUserData_preservesFavoriteAndLastWatchedByUrl() = runBlocking {
        val initial = listOf(
            Channel(sourceId = sourceId, name = "BBC", url = "http://x/bbc.ts", group = "UK"),
            Channel(sourceId = sourceId, name = "CNN", url = "http://x/cnn.ts", group = "US"),
        )
        channelDao.insertAll(initial)
        val inserted = channelDao.getBySourceOnce(sourceId)
        val bbcId = inserted.first { it.name == "BBC" }.id
        val cnnId = inserted.first { it.name == "CNN" }.id
        channelDao.toggleFavorite(bbcId)
        channelDao.updateLastWatched(cnnId, 1_700_000_000_000L)

        // Resync: BBC URL unchanged, CNN gone (upstream removed it), new channel added.
        val fetched = listOf(
            Channel(sourceId = sourceId, name = "BBC One", url = "http://x/bbc.ts", group = "UK"),
            Channel(sourceId = sourceId, name = "Al Jazeera", url = "http://x/aj.ts", group = "INT"),
        )
        channelDao.replaceForSourcePreservingUserData(sourceId, fetched)

        val after = channelDao.getBySourceOnce(sourceId).associateBy { it.name }
        assertEquals(2, after.size)
        // BBC matched by URL: name update from upstream wins, but favorite preserved.
        assertEquals(true, after["BBC One"]?.isFavorite)
        // CNN dropped from upstream entirely: no surviving row to carry its lastWatchedAt.
        assertNull(after["CNN"])
        // New channel inserted as-is.
        assertEquals(false, after["Al Jazeera"]?.isFavorite)
        assertNull(after["Al Jazeera"]?.lastWatchedAt)
    }

    @Test
    fun replaceForSourcePreservingUserData_fallsBackToTvgChannelIdWhenUrlChanges() = runBlocking {
        channelDao.insertAll(listOf(
            Channel(sourceId = sourceId, name = "BBC", url = "http://x/bbc.ts", tvgChannelId = "bbc.uk"),
        ))
        val id = channelDao.getBySourceOnce(sourceId).first().id
        channelDao.toggleFavorite(id)
        channelDao.updateLastWatched(id, 42L)

        // Upstream re-encoded the URL but kept the tvg-id.
        channelDao.replaceForSourcePreservingUserData(sourceId, listOf(
            Channel(sourceId = sourceId, name = "BBC", url = "http://x/bbc-hd.ts", tvgChannelId = "bbc.uk"),
        ))

        val after = channelDao.getBySourceOnce(sourceId).single()
        assertEquals("http://x/bbc-hd.ts", after.url)
        assertEquals(true, after.isFavorite)
        assertEquals(42L, after.lastWatchedAt)
    }

    @Test
    fun replaceForSourcePreservingUserData_fallsBackToNameAndGroupWhenUrlAndTvgIdChange() = runBlocking {
        channelDao.insertAll(listOf(
            Channel(sourceId = sourceId, name = "BBC", url = "http://x/bbc.ts", group = "UK"),
        ))
        val id = channelDao.getBySourceOnce(sourceId).first().id
        channelDao.toggleFavorite(id)

        channelDao.replaceForSourcePreservingUserData(sourceId, listOf(
            Channel(sourceId = sourceId, name = "BBC", url = "http://different/bbc.ts", group = "UK", tvgChannelId = "bbc.new"),
        ))

        val after = channelDao.getBySourceOnce(sourceId).single()
        assertEquals(true, after.isFavorite)
    }

    @Test
    fun replaceForSourcePreservingUserData_consumesEachExistingMatchOnce() = runBlocking {
        // Two existing rows with same (name, group) — only one has the favorite.
        channelDao.insertAll(listOf(
            Channel(sourceId = sourceId, name = "Movie", url = "http://x/a", group = "VOD"),
            Channel(sourceId = sourceId, name = "Movie", url = "http://x/b", group = "VOD"),
        ))
        val favId = channelDao.getBySourceOnce(sourceId).first { it.url == "http://x/a" }.id
        channelDao.toggleFavorite(favId)

        // Both URLs change so URL match misses; both fall back to (name, group).
        channelDao.replaceForSourcePreservingUserData(sourceId, listOf(
            Channel(sourceId = sourceId, name = "Movie", url = "http://x/a2", group = "VOD"),
            Channel(sourceId = sourceId, name = "Movie", url = "http://x/b2", group = "VOD"),
        ))

        val after = channelDao.getBySourceOnce(sourceId)
        // Exactly one of the two fetched rows ends up favorited (the existing-fav was consumed once).
        assertEquals(1, after.count { it.isFavorite })
    }

    @Test
    fun replaceForSourcePreservingUserData_emptyFetched_clearsAllRowsForSource() = runBlocking {
        channelDao.insertAll(listOf(
            Channel(sourceId = sourceId, name = "A", url = "http://x/a"),
            Channel(sourceId = sourceId, name = "B", url = "http://x/b"),
        ))
        channelDao.replaceForSourcePreservingUserData(sourceId, emptyList())
        assertTrue(channelDao.getBySourceOnce(sourceId).isEmpty())
    }

    @Test
    fun replaceForSourcePreservingUserData_doesNotTouchOtherSources() = runBlocking {
        val otherSourceId = sourceDao.insert(
            Source(name = "Other", type = SourceType.M3U, url = "http://other.example.com")
        )
        channelDao.insertAll(listOf(
            Channel(sourceId = sourceId, name = "Mine", url = "http://x/mine"),
            Channel(sourceId = otherSourceId, name = "Theirs", url = "http://other/theirs"),
        ))

        channelDao.replaceForSourcePreservingUserData(sourceId, emptyList())

        assertTrue(channelDao.getBySourceOnce(sourceId).isEmpty())
        assertEquals(1, channelDao.getBySourceOnce(otherSourceId).size)
    }
}
