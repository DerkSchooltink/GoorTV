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

    @Query("""
        SELECT p.* FROM programmes p
        WHERE p.startMs <= :nowMs AND p.endMs > :nowMs
          AND EXISTS (
            SELECT 1 FROM channels c
            WHERE c.tvgChannelId = p.tvgChannelId AND c.sourceId = p.sourceId
          )
    """)
    fun observeAllNow(nowMs: Long): Flow<List<Programme>>

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
