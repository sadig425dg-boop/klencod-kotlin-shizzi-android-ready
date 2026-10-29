package com.shizzi.app.core.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.shizzi.app.core.model.ServerConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {

    @Query("SELECT * FROM servers ORDER BY (ping_ms IS NULL), ping_ms ASC, created_at DESC")
    fun observeAll(): Flow<List<ServerConfig>>

    @Query("SELECT * FROM servers ORDER BY (ping_ms IS NULL), ping_ms ASC, created_at DESC")
    suspend fun getAll(): List<ServerConfig>

    @Query("SELECT * FROM servers WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ServerConfig?

    @Query("SELECT * FROM servers WHERE is_selected = 1 LIMIT 1")
    suspend fun getSelected(): ServerConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(servers: List<ServerConfig>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(server: ServerConfig): Long

    @Update
    suspend fun update(server: ServerConfig)

    @Delete
    suspend fun delete(server: ServerConfig)

    @Query("DELETE FROM servers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE servers SET is_selected = 0")
    suspend fun clearSelection()

    @Query("UPDATE servers SET is_selected = 1 WHERE id = :id")
    suspend fun select(id: Long)

    @Query("UPDATE servers SET ping_ms = :pingMs WHERE id = :id")
    suspend fun updatePing(id: Long, pingMs: Int?)
}
