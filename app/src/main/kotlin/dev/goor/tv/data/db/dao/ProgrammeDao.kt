package dev.goor.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.goor.tv.data.model.Programme
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgrammeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programmes: List<Programme>)

    @Query("DELETE FROM programmes WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: Long)

    @Query("DELETE FROM programmes WHERE sourceId = :sourceId AND endMs < :cutoffMs")
    suspend fun deleteOlderThan(sourceId: Long, cutoffMs: Long)

    @Query("SELECT COUNT(*) FROM programmes WHERE sourceId = :sourceId")
    suspend fun countForSource(sourceId: Long): Int

    @Query("""
        SELECT * FROM programmes
        WHERE sourceId = :sourceId AND tvgChannelId = :tvgId
          AND startMs <= :nowMs AND endMs > :nowMs
        LIMIT 1
    """)
    fun observeNow(sourceId: Long, tvgId: String, nowMs: Long): Flow<Programme?>

    @Query("""
        SELECT * FROM programmes
        WHERE sourceId = :sourceId AND tvgChannelId = :tvgId AND endMs > :nowMs
        ORDER BY startMs ASC LIMIT 2
    """)
    fun observeNowAndNext(sourceId: Long, tvgId: String, nowMs: Long): Flow<List<Programme>>

    /**
     * The programme airing at [nowMs] for every visible Home channel.
     *
     * When a channel has overlapping programmes the **latest-starting** one
     * wins, matching the rule the paged search applies in [ChannelDao]. This
     * keeps "what the row shows" and "what the search matched" identical.
     *
     * Driven from `channels` so the cost is per visible channel (one primary-key
     * lookup each) rather than one scan of the whole `programmes` table.
     */
    @Query("""
        SELECT DISTINCT p.* FROM channels c
        JOIN programmes p
          ON p.sourceId = c.sourceId AND p.tvgChannelId = c.tvgChannelId
        WHERE c.hidden = 0
          AND c.tvgChannelId IS NOT NULL AND c.tvgChannelId != ''
          AND p.startMs <= :nowMs AND p.endMs > :nowMs
          AND p.startMs = (
            SELECT MAX(p2.startMs) FROM programmes p2
            WHERE p2.sourceId = c.sourceId
              AND p2.tvgChannelId = c.tvgChannelId
              AND p2.startMs <= :nowMs AND p2.endMs > :nowMs
          )
          AND EXISTS (
            SELECT 1 FROM sources s
            WHERE s.id = c.sourceId
              AND (s.includedGroups IS NULL
                OR (s.includedGroups != '' AND INSTR('|' || s.includedGroups || '|', '|' || c.`group` || '|') > 0))
          )
    """)
    fun observeAllNow(nowMs: Long): Flow<List<Programme>>

    /**
     * The next programme per visible Home channel for the now/next lines. Each
     * row's `startMs` is the earliest start after [nowMs], so a channel in a
     * schedule gap still reports the upcoming show.
     *
     * Driven from `channels` for the same reason as [observeAllNow]: the
     * correlated MIN uses the `(sourceId, tvgChannelId, startMs)` primary key,
     * and an unbounded scan of every programme is avoided.
     */
    @Query("""
        SELECT DISTINCT p.* FROM channels c
        JOIN programmes p
          ON p.sourceId = c.sourceId AND p.tvgChannelId = c.tvgChannelId
        WHERE c.hidden = 0
          AND c.tvgChannelId IS NOT NULL AND c.tvgChannelId != ''
          AND p.startMs = (
            SELECT MIN(p2.startMs) FROM programmes p2
            WHERE p2.sourceId = c.sourceId
              AND p2.tvgChannelId = c.tvgChannelId
              AND p2.startMs > :nowMs
          )
          AND EXISTS (
            SELECT 1 FROM sources s
            WHERE s.id = c.sourceId
              AND (s.includedGroups IS NULL
                OR (s.includedGroups != '' AND INSTR('|' || s.includedGroups || '|', '|' || c.`group` || '|') > 0))
          )
    """)
    fun observeAllNext(nowMs: Long): Flow<List<Programme>>

    @Query("""
        SELECT * FROM programmes
        WHERE sourceId = :sourceId AND tvgChannelId = :tvgId
          AND endMs > :fromMs AND startMs < :toMs
        ORDER BY startMs
    """)
    fun observeRange(sourceId: Long, tvgId: String, fromMs: Long, toMs: Long): Flow<List<Programme>>

    /**
     * Programmes overlapping [fromMs, toMs] for the explicit (sourceId, tvgChannelId) set
     * the guide grid actually needs. Way cheaper than scanning the whole EPG — a typical
     * source has tens of thousands of programmes across thousands of EPG channels but the
     * grid only renders the ~dozens that are visible.
     */
    @Query("""
        SELECT * FROM programmes
        WHERE sourceId = :sourceId
          AND tvgChannelId IN (:tvgIds)
          AND endMs > :fromMs AND startMs < :toMs
        ORDER BY tvgChannelId, startMs
    """)
    fun observeWindowForChannels(
        sourceId: Long,
        tvgIds: List<String>,
        fromMs: Long,
        toMs: Long,
    ): Flow<List<Programme>>
}
