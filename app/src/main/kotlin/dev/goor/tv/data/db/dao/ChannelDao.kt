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

    @Transaction
    suspend fun replaceForSource(sourceId: Long, channels: List<Channel>) {
        deleteBySource(sourceId)
        insertAll(channels)
    }

    @Query("UPDATE channels SET isFavorite = CASE WHEN isFavorite = 1 THEN 0 ELSE 1 END WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    @Query("UPDATE channels SET lastWatchedAt = :timestamp WHERE id = :id")
    suspend fun updateLastWatched(id: Long, timestamp: Long)

    @Query("UPDATE channels SET lastWatchedAt = NULL")
    suspend fun clearRecentlyWatched()
}
