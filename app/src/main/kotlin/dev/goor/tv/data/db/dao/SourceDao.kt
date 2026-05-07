package dev.goor.tv.data.db.dao

import androidx.room.*
import dev.goor.tv.data.model.Source
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources")
    fun getAll(): Flow<List<Source>>

    @Insert
    suspend fun insert(source: Source): Long

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun getById(id: Long): Source?

    @Delete
    suspend fun delete(source: Source)

    @Update
    suspend fun update(source: Source)

    @Query("UPDATE sources SET includedGroups = :groups WHERE id = :id")
    suspend fun updateIncludedGroups(id: Long, groups: String?)

    @Query("UPDATE sources SET lastSyncedAt = :timestamp WHERE id = :id")
    suspend fun updateLastSyncedAt(id: Long, timestamp: Long)

    @Query("SELECT * FROM sources WHERE type = 'MANUAL' LIMIT 1")
    suspend fun getManualSource(): Source?
}
