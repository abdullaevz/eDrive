package com.edrive.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Lokal istifadəçi. Parolun özü SAXLANILMIR — yalnız vault başlığı (salt + parolla şifrələnmiş DEK).
 * Giriş zamanı parol düzgündürsə DEK açılır (AES-GCM autentifikasiyası), səhvdirsə açılmır.
 */
@Entity(tableName = "users", indices = [Index(value = ["username"], unique = true)])
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val headerJson: String,
    val createdAt: Long,
    val lastLoginAt: Long = 0,
    /** Barmaq izi: DEK Android Keystore açarı ilə şifrələnib (açar yalnız biometrik təsdiqdən sonra işləyir). */
    val bioWrappedDek: ByteArray? = null,
    val bioIv: ByteArray? = null,
    val driveEmail: String? = null,
    val driveRootFolderId: String? = null,
    val driveUserFolderId: String? = null,
)
