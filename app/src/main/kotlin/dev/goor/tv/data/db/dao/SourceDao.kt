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
}
