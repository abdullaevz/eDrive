package com.edrive.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Drive-dakı real alt qovluğun lokal əksi. [id] — Drive qovluq ID-si, [parentId] — üst qovluğun Drive ID-si
 * (`null` = "eDrive Storage" kökü). Eyni Drive-a qoşulan bir neçə lokal hesab ola bildiyi üçün açar (userId, id)-dir.
 */
@Entity(
    tableName = "folders",
    primaryKeys = ["userId", "id"],
    indices = [Index("userId", "parentId")],
)
data class FolderEntity(
    val id: String,
    val userId: Long,
    val name: String,
    val parentId: String?,
    val createdAt: Long,
)
