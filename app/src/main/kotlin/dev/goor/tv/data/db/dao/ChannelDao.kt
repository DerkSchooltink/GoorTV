package dev.goor.tv.data.db.dao

import androidx.paging.PagingSource
import androidx.room.*
import dev.goor.tv.data.model.Channel
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels ORDER BY `group`, name")
    fun getAll(): Flow<List<Channel>>

    /**
     * Like [getAll] but filters out channels whose `group` is excluded by the
     * source's `includedGroups` allow-list. Matches the predicate used by the
     * paged home queries.
     */
    @Query("""
        SELECT c.* FROM channels c
        JOIN sources s ON c.sourceId = s.id
        WHERE (s.includedGroups IS NULL OR (s.includedGroups != '' AND INSTR('|' || s.includedGroups || '|', '|' || c.`group` || '|') > 0))
        ORDER BY c.`group`, c.name
    """)
    fun getAllVisible(): Flow<List<Channel>>

    @Query("""
        SELECT c.* FROM channels c
        JOIN sources s ON c.sourceId = s.id
        WHERE (s.includedGroups IS NULL OR (s.includedGroups != '' AND INSTR('|' || s.includedGroups || '|', '|' || c.`group` || '|') > 0))
        AND (:group IS NULL OR c.`group` = :group)
        AND (:query = '' OR c.name LIKE '%' || :query || '%')
        AND (:favOnly = 0 OR c.isFavorite = 1)
        ORDER BY c.`group`, c.name
    """)
    fun getChannelsPaged(group: String?, query: String, favOnly: Boolean): PagingSource<Int, Channel>

    @Query("""
        SELECT c.* FROM channels c
        JOIN sources s ON c.sourceId = s.id
        WHERE (s.includedGroups IS NULL OR (s.includedGroups != '' AND INSTR('|' || s.includedGroups || '|', '|' || c.`group` || '|') > 0))
        AND (:group IS NULL OR c.`group` = :group)
        AND (:query = '' OR c.name LIKE '%' || :query || '%')
        AND (:favOnly = 0 OR c.isFavorite = 1)
        ORDER BY c.name COLLATE NOCASE ASC
    """)
    fun getChannelsPagedByName(group: String?, query: String, favOnly: Boolean): PagingSource<Int, Channel>

    @Query("""
        SELECT c.* FROM channels c
        JOIN sources s ON c.sourceId = s.id
        WHERE (s.includedGroups IS NULL OR (s.includedGroups != '' AND INSTR('|' || s.includedGroups || '|', '|' || c.`group` || '|') > 0))
        AND (:group IS NULL OR c.`group` = :group)
        AND (:query = '' OR c.name LIKE '%' || :query || '%')
        AND (:favOnly = 0 OR c.isFavorite = 1)
        ORDER BY c.lastWatchedAt DESC, c.name COLLATE NOCASE ASC
    """)
    fun getChannelsPagedByLastWatched(group: String?, query: String, favOnly: Boolean): PagingSource<Int, Channel>

    @Query("SELECT DISTINCT `group` FROM channels WHERE sourceId = :sourceId AND `group` IS NOT NULL ORDER BY `group`")
    fun getGroupsForSource(sourceId: Long): Flow<List<String>>

    @Query("SELECT DISTINCT `group` FROM channels WHERE `group` IS NOT NULL ORDER BY `group`")
    fun getGroups(): Flow<List<String>>

    @Query("SELECT * FROM channels WHERE lastWatchedAt IS NOT NULL ORDER BY lastWatchedAt DESC LIMIT 10")
    fun getRecentlyWatched(): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId ORDER BY `group`, name")
    fun getBySource(sourceId: Long): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId")
    suspend fun getBySourceOnce(sourceId: Long): List<Channel>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun getById(id: Long): Channel?

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun count(): Int

    @Insert
    suspend fun insert(channel: Channel): Long

    @Update
    suspend fun update(channel: Channel)

    @Delete
    suspend fun delete(channel: Channel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<Channel>)

    @Query("DELETE FROM channels WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: Long)

    /**
     * Replaces all channels for [sourceId] with [fetched], preserving user data
     * (favorites, last-watched) from the existing rows.
     *
     * Matching cascades through three keys, in order, so user data survives
     * upstream renames/re-encodings that change just one of them:
     *   1. URL (cheapest, exact)
     *   2. tvgChannelId (stable across URL/name churn when present)
     *   3. (name, group) (last resort for sources without tvg-ids)
     *
     * Each existing row is "consumed" on first match so the same favorite can't
     * be applied to two different fetched channels.
     *
     * Read-merge-write happens inside a single transaction so concurrent reads
     * never observe the table mid-delete, and a partial-insert failure leaves
     * the old rows intact (the implicit ROLLBACK undoes the prior DELETE).
     */
    @Transaction
    suspend fun replaceForSourcePreservingUserData(sourceId: Long, fetched: List<Channel>) {
        val existing = getBySourceOnce(sourceId)
            .filter { it.isFavorite || it.lastWatchedAt != null }
            .toMutableList()
        val byUrl = existing.associateBy { it.url }.toMutableMap()
        val byTvgId = existing.filter { !it.tvgChannelId.isNullOrBlank() }
            .associateBy { it.tvgChannelId!! }.toMutableMap()
        val byNameGroup = existing.associateBy { it.name to it.group }.toMutableMap()

        fun consume(match: Channel) {
            byUrl.remove(match.url)
            match.tvgChannelId?.let { byTvgId.remove(it) }
            byNameGroup.remove(match.name to match.group)
        }

        val merged = fetched.map { ch ->
            val match = byUrl[ch.url]
                ?: ch.tvgChannelId?.takeIf { it.isNotBlank() }?.let { byTvgId[it] }
                ?: byNameGroup[ch.name to ch.group]
            if (match != null) {
                consume(match)
                ch.copy(isFavorite = match.isFavorite, lastWatchedAt = match.lastWatchedAt)
            } else {
                ch
            }
        }
        deleteBySource(sourceId)
        insertAll(merged)
    }

    @Query("UPDATE channels SET isFavorite = CASE WHEN isFavorite = 1 THEN 0 ELSE 1 END WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    @Query("UPDATE channels SET lastWatchedAt = :timestamp WHERE id = :id")
    suspend fun updateLastWatched(id: Long, timestamp: Long)

    @Query("UPDATE channels SET lastWatchedAt = NULL")
    suspend fun clearRecentlyWatched()

    @Query("""
        SELECT id, name FROM channels
        WHERE sourceId = :sourceId AND (tvgChannelId IS NULL OR tvgChannelId = '')
    """)
    suspend fun getMissingTvgIdsBySource(sourceId: Long): List<ChannelIdName>

    @Query("UPDATE channels SET tvgChannelId = :tvgId WHERE id = :id")
    suspend fun setTvgChannelId(id: Long, tvgId: String)

    /**
     * Bulk-apply tvg-id assignments in a single transaction. Avoids the WAL thrash of
     * issuing one auto-committed UPDATE per channel during EPG backfill.
     */
    @Transaction
    suspend fun applyTvgChannelIdAssignments(assignments: List<Pair<Long, String>>) {
        assignments.forEach { (id, tvgId) -> setTvgChannelId(id, tvgId) }
    }
}

/** Lightweight projection used by [ChannelDao.getMissingTvgIdsBySource]. */
data class ChannelIdName(val id: Long, val name: String)
