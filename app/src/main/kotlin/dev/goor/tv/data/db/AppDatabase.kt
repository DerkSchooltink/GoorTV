package dev.goor.tv.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.ProgrammeDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Channel
import dev.goor.tv.data.model.Programme
import dev.goor.tv.data.model.Source

@Database(entities = [Source::class, Channel::class, Programme::class], version = 12, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun channelDao(): ChannelDao
    abstract fun programmeDao(): ProgrammeDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE channels ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE channels ADD COLUMN lastWatchedAt INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_channels_group_name ON channels (`group`, name)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // NULL = show all (existing sources keep all channels visible)
                db.execSQL("ALTER TABLE sources ADD COLUMN includedGroups TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sources ADD COLUMN lastSyncedAt INTEGER")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sources ADD COLUMN headers TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sources ADD COLUMN maxConcurrentStreams INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sources ADD COLUMN epgUrl TEXT")
                db.execSQL("ALTER TABLE sources ADD COLUMN lastEpgSyncedAt INTEGER")
                db.execSQL("ALTER TABLE sources ADD COLUMN epgLastError TEXT")
                db.execSQL("ALTER TABLE channels ADD COLUMN tvgChannelId TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_channels_tvgChannelId ON channels(tvgChannelId)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS programmes (
                        sourceId INTEGER NOT NULL,
                        tvgChannelId TEXT NOT NULL,
                        startMs INTEGER NOT NULL,
                        endMs INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT,
                        category TEXT,
                        iconUrl TEXT,
                        PRIMARY KEY(sourceId, tvgChannelId, startMs),
                        FOREIGN KEY(sourceId) REFERENCES sources(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_programmes_sourceId ON programmes(sourceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_programmes_sourceId_tvgChannelId_endMs ON programmes(sourceId, tvgChannelId, endMs)")
            }
        }

        /**
         * Adds a covering (sourceId, tvgChannelId) index on channels. Used by the
         * channel-matching subqueries in ProgrammeDao — without it SQLite scans all
         * channels per programme row, turning the guide query into a 60s+ hang on a
         * 38k-channel / 175k-programme DB.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_channels_sourceId_tvgChannelId ON channels(sourceId, tvgChannelId)")
            }
        }

        /**
         * Drops the standalone (tvgChannelId) index. The composite
         * (sourceId, tvgChannelId) added in v9 covers every existing query path
         * (including any tvgChannelId-only lookups via its leading column), so
         * the standalone was pure write-amplification on large playlist inserts.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_channels_tvgChannelId")
            }
        }

        /**
         * Adds a unique index on `sources(type, url)`. Dedupes existing duplicates
         * first by keeping the oldest row of each pair — the FK cascade then drops
         * the orphaned channels. Affects any user who somehow accumulated two
         * sources pointing at the same URL (rare; the UI didn't pre-check
         * historically).
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    DELETE FROM sources WHERE id NOT IN (
                        SELECT MIN(id) FROM sources GROUP BY type, url
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sources_type_url ON sources(type, url)")
            }
        }

        /**
         * Adds a per-channel `hidden` flag for user-driven moderation
         * (Play Store UGC policy A3.5). Preserved across re-syncs by the
         * merge in `ChannelDao.replaceForSourcePreservingUserData`.
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE channels ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
