package dev.goor.tv.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.goor.tv.data.model.Programme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "migration-test.db"

    private val allMigrations = arrayOf(
        AppDatabase.MIGRATION_1_2,
        AppDatabase.MIGRATION_2_3,
        AppDatabase.MIGRATION_3_4,
        AppDatabase.MIGRATION_4_5,
        AppDatabase.MIGRATION_5_6,
        AppDatabase.MIGRATION_6_7,
        AppDatabase.MIGRATION_7_8,
        AppDatabase.MIGRATION_8_9,
        AppDatabase.MIGRATION_9_10,
        AppDatabase.MIGRATION_10_11,
    )

    @Before
    fun setup() {
        context.deleteDatabase(dbName)
    }

    @After
    fun teardown() {
        context.deleteDatabase(dbName)
    }

    /**
     * Walks the full migration chain from v1 with seed rows, then opens Room at the
     * current version. Room validates the resulting schema against the entity
     * fingerprint on open — any migration drift throws `IllegalStateException`.
     * Also asserts the seed rows survive and that columns added by later migrations
     * hydrate to their defaults.
     */
    @Test
    fun migratesV1ToCurrent_preservesSeedData() {
        seedV1Database()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*allMigrations)
            .build()

        runBlocking {
            val sources = db.sourceDao().getAll().first()
            assertEquals(1, sources.size)
            val source = sources.first()
            assertEquals("seed-source", source.name)
            assertEquals("http://example.com/playlist.m3u8", source.url)
            // Columns added by later migrations: nullable ones stay NULL on legacy rows;
            // NOT NULL DEFAULT 0 (maxConcurrentStreams) hydrates to 0.
            assertNull(source.includedGroups)
            assertNull(source.lastSyncedAt)
            assertNull(source.headers)
            assertEquals(0, source.maxConcurrentStreams)
            assertNull(source.epgUrl)
            assertNull(source.lastEpgSyncedAt)
            assertNull(source.epgLastError)

            val channels = db.channelDao().getAll().first()
            assertEquals(1, channels.size)
            val channel = channels.first()
            assertEquals("Seed Channel", channel.name)
            assertEquals("Seed Group", channel.group)
            assertEquals(false, channel.isFavorite)
            assertNull(channel.lastWatchedAt)
            assertNull(channel.tvgChannelId)

            // programmes table was created in v8 — must be usable post-migration.
            val now = 1_700_000_000_000L
            val programme = Programme(
                sourceId = source.id,
                tvgChannelId = "seed.ch",
                startMs = now,
                endMs = now + 60_000,
                title = "Test",
            )
            db.programmeDao().insertAll(listOf(programme))
            val window = db.programmeDao()
                .observeWindowForChannels(source.id, listOf("seed.ch"), now - 1, now + 60_001)
                .first()
            assertEquals(1, window.size)
            assertEquals("Test", window.first().title)
        }

        db.close()
    }

    /**
     * Hand-rolls the v1 schema (Channel + Source only, none of the later columns/
     * indexes) and seeds one row of each. Mirrors what a freshly-installed v1
     * device would have on disk.
     */
    private fun seedV1Database() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE sources (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                name TEXT NOT NULL,
                                type TEXT NOT NULL,
                                url TEXT NOT NULL,
                                username TEXT,
                                password TEXT
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE channels (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                sourceId INTEGER NOT NULL,
                                name TEXT NOT NULL,
                                url TEXT NOT NULL,
                                `group` TEXT,
                                logoUrl TEXT,
                                FOREIGN KEY(sourceId) REFERENCES sources(id) ON DELETE CASCADE
                            )
                            """.trimIndent()
                        )
                        db.execSQL("CREATE INDEX index_channels_sourceId ON channels(sourceId)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        error("Unexpected upgrade during v1 seed: $oldVersion -> $newVersion")
                    }
                })
                .build()
        )
        helper.writableDatabase.use { db ->
            db.execSQL(
                "INSERT INTO sources (name, type, url, username, password) " +
                    "VALUES ('seed-source', 'M3U', 'http://example.com/playlist.m3u8', NULL, NULL)"
            )
            db.execSQL(
                "INSERT INTO channels (sourceId, name, url, `group`, logoUrl) " +
                    "VALUES (1, 'Seed Channel', 'http://example.com/ch.ts', 'Seed Group', 'http://example.com/logo.png')"
            )
        }
        helper.close()
    }
}
