package com.edrive.app.drive

import android.app.PendingIntent
import kotlinx.serialization.Serializable
import java.io.File
import java.io.InputStream

/**
 * Bulud yaddaşı ilə iş üçün abstraksiya (Dependency Inversion).
 * Biznes məntiqi bu interfeysdən asılıdır, konkret Google Drive REST klientindən yox —
 * beləliklə testlərdə saxta (in-memory) Drive istifadə etmək və gələcəkdə başqa bulud əlavə etmək mümkündür.
 */
interface DriveClient {
    suspend fun about(): DriveAbout
    suspend fun findByName(name: String, parentId: String?, folder: Boolean = false): DriveFile?
    /** Qovluq varsa onun ID-sini, yoxdursa yaradıb ID-sini qaytarır. */
    suspend fun ensureFolder(name: String, parentId: String?): String
    suspend fun listChildren(parentId: String): List<DriveFile>
    /** Kiçik fayllar (manifest, vault.json). `existingId` verilsə, mövcud fayl yenilənir. */
    suspend fun uploadSmall(name: String, parentId: String, bytes: ByteArray, mime: String, existingId: String? = null): DriveFile
    /** Böyük fayllar — axınla, yaddaşa tam yüklənmədən. */
    suspend fun uploadLarge(name: String, parentId: String, file: File, onProgress: (Float) -> Unit): DriveFile
    /** Çağıran tərəf axını bağlamalıdır. */
    suspend fun download(fileId: String): InputStream
    suspend fun downloadBytes(fileId: String): ByteArray = download(fileId).use { it.readBytes() }
    /** Fayl artıq yoxdursa (404) səssizcə keçir. */
    suspend fun delete(fileId: String)

    companion object {
        const val OCTET = "application/octet-stream"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
    }
}

/** Hansı hesab üçün hansı klientin istifadə olunacağını həll edir (token idarəsi buradadır). */
interface DriveClientProvider {
    /** Artıq qoşulmuş hesab üçün (token lazım olanda avtomatik yenilənir). */
    fun forAccount(email: String): DriveClient
    /** Qoşulmanın ilk addımı: e-poçt hələ bilinmir, yalnız token var. */
    fun withToken(token: String): DriveClient
}

/** OAuth icazəsi (Google Identity Services) üçün abstraksiya. */
interface DriveAuthorizer {
    suspend fun authorize(email: String?): AuthResult
    suspend fun revoke(token: String)
}

sealed interface AuthResult {
    data class Token(val accessToken: String) : AuthResult
    /** İstifadəçi hesab seçməli və ya icazə verməlidir — UI bu PendingIntent-i açmalıdır. */
    data class NeedsConsent(val pendingIntent: PendingIntent) : AuthResult
}

/** Drive-da eDrive-ın qovluq quruluşu. */
object DriveLayout {
    const val ROOT_FOLDER = "eDrive Storage"
    const val VAULT_FILE = "vault.json"
    fun dataName(id: String) = "$id.edrv"
    fun metaName(id: String) = "$id.meta"
}

class DriveException(val code: Int, message: String) : Exception(message)

/** Google hesabı seçimi və ya icazə tələb olunur — UI PendingIntent-i açmalıdır. */
class DriveConsentRequired(val pendingIntent: PendingIntent) : Exception("Google Drive icazəsi tələb olunur")

@Serializable data class DriveFile(val id: String, val name: String, val size: String? = null, val mimeType: String? = null)
@Serializable data class DriveFileList(val files: List<DriveFile> = emptyList(), val nextPageToken: String? = null)
@Serializable data class DriveAbout(val user: DriveUser)
@Serializable data class DriveUser(val emailAddress: String, val displayName: String? = null)
