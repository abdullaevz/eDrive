package com.edrive.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Lokal istifadəçi (profil). Nə PIN, nə Təhlükəsizlik açarı açıq saxlanılır.
 *
 * - **PIN** yalnız proqramın qapısıdır: [pinHash] = HMAC(Keystore açarı, [pinSalt] ‖ PIN).
 *   `null` — köhnə (1.x) hesab, PIN hələ təyin edilməyib.
 * - **Vault** Drive-a qoşulanda yaranır və ya Drive-dakından götürülür. [headerJson] onun lokal nüsxəsidir
 *   (`null` = vault yoxdur). DEK cihazın Keystore açarı ilə sarılıb [deviceWrappedDek]-də saxlanılır.
 */
@Entity(tableName = "users", indices = [Index(value = ["username"], unique = true)])
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val createdAt: Long,
    val lastLoginAt: Long = 0,
    val pinHash: ByteArray? = null,
    val pinSalt: ByteArray? = null,
    /** Ardıcıl yanlış PIN cəhdləri (uğurlu girişdə sıfırlanır). */
    val failedAttempts: Int = 0,
    /** Bu vaxta qədər (epoch ms) PIN qəbul edilmir. */
    val lockedUntil: Long = 0,
    /** Biometrik yalnız proqramı açır — heç bir açarı açmır. */
    val biometricEnabled: Boolean = false,
    val headerJson: String? = null,
    val deviceWrappedDek: ByteArray? = null,
    val deviceIv: ByteArray? = null,
    val driveEmail: String? = null,
    /** Drive-dakı "eDrive Storage" qovluğunun ID-si. */
    val driveRootFolderId: String? = null,
) {
    val hasPin: Boolean get() = pinHash != null
    val hasVault: Boolean get() = headerJson != null

    /** Drive qoşulub və vault hazırdır — fayl əlavə etmək və sinxronlaşma mümkündür. */
    val isDriveReady: Boolean get() = driveRootFolderId != null && driveEmail != null && headerJson != null
}
