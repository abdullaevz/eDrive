package com.edrive.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.edrive.app.data.db.entity.FileEntity
import com.edrive.app.data.db.entity.FileStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {
    @Query("SELECT * FROM files WHERE userId = :userId ORDER BY createdAt DESC")
    fun observe(userId: Long): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun get(id: String): FileEntity?

    @Query("SELECT * FROM files WHERE id = :id")
    fun observeOne(id: String): Flow<FileEntity?>

    @Query("SELECT id FROM files WHERE userId = :userId")
    suspend fun ids(userId: Long): List<String>

    @Query("SELECT COUNT(*) FROM files WHERE userId = :userId")
    suspend fun count(userId: Long): Int

    @Query("SELECT * FROM files WHERE status IN ('PENDING','UPLOADING','FAILED') ORDER BY createdAt")
    suspend fun uploadQueue(): List<FileEntity>

    @Query("SELECT * FROM files WHERE status = 'ENCRYPTING'")
    suspend fun interrupted(): List<FileEntity>

    @Upsert
    suspend fun upsert(file: FileEntity)

    @Query("UPDATE files SET status = :status, progress = :progress, error = :error WHERE id = :id")
    suspend fun setStatus(id: String, status: FileStatus, progress: Float = 0f, error: String? = null)

    @Query("UPDATE files SET status = 'SYNCED', progress = 1, error = NULL, driveDataId = :dataId, driveMetaId = :metaId WHERE id = :id")
    suspend fun markSynced(id: String, dataId: String, metaId: String)

    @Query("UPDATE files SET driveDataId = NULL, driveMetaId = NULL WHERE id = :id")
    suspend fun clearDriveIds(id: String)

    @Query("DELETE FROM files WHERE id = :id")
    suspend fun delete(id: String)
}
