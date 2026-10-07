package com.edrive.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.edrive.app.data.db.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE userId = :userId ORDER BY name COLLATE NOCASE")
    fun observe(userId: Long): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE userId = :userId")
    suspend fun all(userId: Long): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE userId = :userId AND id = :id")
    suspend fun get(userId: Long, id: String): FolderEntity?

    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE userId = :userId AND id = :id")
    suspend fun delete(userId: Long, id: String)

    @Query("DELETE FROM folders WHERE userId = :userId")
    suspend fun deleteAll(userId: Long)
}
