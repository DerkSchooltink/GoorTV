package dev.goor.tv.data.db.dao

import androidx.room.*
import dev.goor.tv.data.model.Channel
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels ORDER BY `group`, name")
    fun getAll(): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId ORDER BY `group`, name")
    fun getBySource(sourceId: Long): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId")
    suspend fun getBySourceOnce(sourceId: Long): List<Channel>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun getById(id: Long): Channel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<Channel>)

    @Query("DELETE FROM channels WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: Long)

    @Query("UPDATE channels SET isFavorite = CASE WHEN isFavorite = 1 THEN 0 ELSE 1 END WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    @Query("UPDATE channels SET lastWatchedAt = :timestamp WHERE id = :id")
    suspend fun updateLastWatched(id: Long, timestamp: Long)
}
