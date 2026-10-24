package dev.goor.tv.data.db.dao

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.goor.tv.data.db.AppDatabase
import dev.goor.tv.data.db.SecretConverter
import dev.goor.tv.data.model.Channel
import dev.goor.tv.data.model.Programme
import dev.goor.tv.data.model.Source
import dev.goor.tv.data.model.SourceType
import dev.goor.tv.util.TestCredentialCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Executed (instrumented) coverage for the Home EPG search. These tests run the
 * real Room/SQLite queries; the JVM unit tests can only mock the DAO.
 *
 * Query strings are passed pre-escaped, exactly as `HomeViewModel` does via
 * `escapeLikePattern`, because escaping is the caller's contract.
 */
@RunWith(AndroidJUnit4::class)
class ChannelSearchDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var channelDao: ChannelDao
    private lateinit var programmeDao: ProgrammeDao

    private var sourceA: Long = 0
    private var sourceB: Long = 0

    private val now = 1_700_000_000_000L
    private val halfHour = 30L * 60L * 1000L

    @Before
    fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).allowMainThreadQueries()
            .addTypeConverter(SecretConverter(TestCredentialCipher))
            .build()
        channelDao = db.channelDao()
        programmeDao = db.programmeDao()

        // A shows every group; B only allows "UK", so its "US" channel is hidden.
        // `includedGroups` defaults to "" (show nothing), so an all-groups source
        // must say so explicitly with null.
        sourceA = db.sourceDao().insert(
            Source(name = "A", type = SourceType.M3U, url = "http://a.example.com", includedGroups = null),
        )
        sourceB = db.sourceDao().insert(
            Source(name = "B", type = SourceType.M3U, url = "http://b.example.com", includedGroups = "UK"),
        )

        channelDao.insertAll(
            listOf(
                channel("BBC News", "a/news", "UK", "bbc.news"),
                channel("BBC One", "a/one", "UK", "bbc.one"),
                channel("100% Movies", "a/pct", "FILM", "pct"),
                channel("100X Movies", "a/x", "FILM", "x"),
                channel("A_B", "a/underscore", "TEST", "underscore"),
                channel("AxB", "a/anychar", "TEST", "anychar"),
                channel("Overlap TV", "a/overlap", "UK", "overlap"),
                channel("Radio 1", "a/radio", "UK", "radio"),
                channel("Excluded News", "b/news", "US", "b.news", sourceB),
            ),
        )
        channelDao.insert(
            Channel(
                sourceId = sourceA,
                name = "Hidden News",
                url = "http://a/hidden",
                group = "UK",
                tvgChannelId = "hidden",
                hidden = true,
            ),
        )

        programmeDao.insertAll(
            listOf(
                programme("bbc.one", now - halfHour, now + halfHour, "Evening News"),
                programme("hidden", now - halfHour, now + halfHour, "Hidden News Bulletin"),
                programme("b.news", now - halfHour, now + halfHour, "Excluded News Bulletin"),
            ),
        )

        // Overlapping programmes: the later-starting one must win, so "Old Show"
        // is neither displayed nor searchable at `now`.
        programmeDao.insertAll(
            listOf(
                programme("overlap", now - 20_000L, now + 20_000L, "Old Show"),
                programme("overlap", now - 5_000L, now + 25_000L, "New Show"),
                programme("overlap", now + 60_000L, now + 120_000L, "Later Show"),
            ),
        )
    }

    @After
    fun teardown() { db.close() }

    // ── Search predicate and ranking ─────────────────────────────────────────

    @Test
    fun search_ranks_channelName_before_programmeTitle_for_all_sorts() = runBlocking {
        for (sort in Sort.entries) {
            val names = page(sort, query = "news", nowMs = now).map { it.name }
            assertEquals("sort=$sort", "BBC News", names.first())
            assertTrue("sort=$sort should include the programme match", "BBC One" in names)
            assertEquals("sort=$sort", "BBC One", names[1])
        }
    }

    @Test
    fun search_matches_current_programme_title() = runBlocking {
        assertEquals(listOf("BBC One"), page(Sort.GROUP, "evening", now).map { it.name })
    }

    @Test
    fun search_does_not_match_a_programme_that_is_not_airing() = runBlocking {
        assertTrue(page(Sort.GROUP, "later show", now).isEmpty())
        assertEquals(listOf("Overlap TV"), page(Sort.GROUP, "new show", now).map { it.name })
    }

    @Test
    fun search_treats_percent_as_a_literal() = runBlocking {
        assertEquals(listOf("100% Movies"), page(Sort.GROUP, """100\%""", now).map { it.name })
    }

    @Test
    fun search_treats_underscore_as_a_literal() = runBlocking {
        assertEquals(listOf("A_B"), page(Sort.GROUP, """A\_B""", now).map { it.name })
    }

    @Test
    fun search_excludes_hidden_and_disallowed_group_channels() = runBlocking {
        val names = page(Sort.GROUP, "news", now).map { it.name }
        assertFalse("Hidden News" in names)
        assertFalse("Excluded News" in names)
    }

    @Test
    fun blank_query_returns_every_visible_channel() = runBlocking {
        val names = page(Sort.GROUP, "", now).map { it.name }
        assertEquals(8, names.size)
        assertFalse("Hidden News" in names)
        assertFalse("Excluded News" in names)
    }

    // ── Now / next selection ─────────────────────────────────────────────────

    @Test
    fun observeAllNow_picks_the_latest_starting_overlapping_programme() = runBlocking {
        val programmes = programmeDao.observeAllNow(now).first()
        val overlap = programmes.single { it.tvgChannelId == "overlap" }

        assertEquals("New Show", overlap.title)
        // The hidden channel is not visible, so it must not be in the Home map.
        assertFalse(programmes.any { it.tvgChannelId == "hidden" })
        assertFalse(programmes.any { it.tvgChannelId == "b.news" })
    }

    @Test
    fun observeAllNext_returns_the_soonest_programme_per_visible_channel() = runBlocking {
        val programmes = programmeDao.observeAllNext(now).first()
        val overlap = programmes.single { it.tvgChannelId == "overlap" }
        assertEquals("Later Show", overlap.title)

        assertFalse(programmes.any { it.tvgChannelId == "hidden" })
        assertFalse(programmes.any { it.tvgChannelId == "b.news" })
    }

    @Test
    fun observeAllNext_skips_channels_with_no_future_programme() = runBlocking {
        val programmes = programmeDao.observeAllNext(now).first()
        assertFalse(programmes.any { it.tvgChannelId == "bbc.news" })
        assertFalse(programmes.any { it.tvgChannelId == "radio" })
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private enum class Sort { GROUP, NAME, LAST_WATCHED }

    private suspend fun page(sort: Sort, query: String, nowMs: Long): List<Channel> {
        val source = when (sort) {
            Sort.GROUP -> channelDao.getChannelsPaged(null, query, false, nowMs)
            Sort.NAME -> channelDao.getChannelsPagedByName(null, query, false, nowMs)
            Sort.LAST_WATCHED -> channelDao.getChannelsPagedByLastWatched(null, query, false, nowMs)
        }
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 100, placeholdersEnabled = false),
        )
        return (result as PagingSource.LoadResult.Page).data
    }

    private fun channel(
        name: String,
        urlPath: String,
        group: String,
        tvgId: String,
        owner: Long = sourceA,
    ) = Channel(
        sourceId = owner,
        name = name,
        url = "http://$urlPath",
        group = group,
        tvgChannelId = tvgId,
    )

    private fun programme(tvgId: String, startMs: Long, endMs: Long, title: String) = Programme(
        sourceId = sourceA,
        tvgChannelId = tvgId,
        startMs = startMs,
        endMs = endMs,
        title = title,
    )
}
