package com.edrive.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Faylın həyat dövrü: şifrələnir → növbədə → yüklənir → Drive-da (və ya xəta).
 * [LOCAL] — Drive-dan silinib, şifrəli nüsxə yalnız bu cihazdadır (sinxronizasiya ona toxunmur).
 */
enum class FileStatus { ENCRYPTING, PENDING, UPLOADING, SYNCED, FAILED, LOCAL }

/**
 * [id] faylın sabit ID-sidir (Drive-da `<id>.edrv` / `<id>.meta` adlarında və şifrələmə AAD-ində işlənir).
 * Eyni Drive-a qoşulan bir neçə lokal hesab ola bildiyi üçün açar (userId, id)-dir.
 */
@Entity(
    tableName = "files",
    primaryKeys = ["userId", "id"],
    indices = [Index("userId", "folderId")],
)
data class FileEntity(
    val id: String,
    val userId: Long,
    val name: String,
    val mimeType: String,
    val size: Long,
    val createdAt: Long,
    val width: Int = 0,
    val height: Int = 0,
    val hasThumb: Boolean = false,
    val status: FileStatus,
    val progress: Float = 0f,
    val error: String? = null,
    val driveDataId: String? = null,
    val driveMetaId: String? = null,
    /** Drive qovluq ID-si; `null` = "eDrive Storage" kökü. */
    val folderId: String? = null,
)
