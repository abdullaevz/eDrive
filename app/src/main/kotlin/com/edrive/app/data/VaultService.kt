package com.edrive.app.data

import com.edrive.app.data.db.dao.UserDao
import com.edrive.app.data.db.entity.UserEntity
import com.edrive.app.security.DeviceKeyStore
import com.edrive.app.util.AppLog
import com.edrive.crypto.AuthenticationFailedException
import com.edrive.crypto.PasswordKdf
import com.edrive.crypto.VaultHeader
import com.edrive.crypto.VaultKeys
import com.edrive.crypto.wipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vault açarının (DEK) həyat dövrü: yaradılma, Drive-dakı vault-un götürülməsi, cihazda saxlanma,
 * açarın dəyişdirilməsi və silinmə. Drive ilə əlaqəni bilmir — onu [com.edrive.app.data.vault.DriveConnectionService] idarə edir.
 *
 * DEK həmişə iki formada qorunur: `vault.json`-da Təhlükəsizlik açarından törədilən KEK ilə (bərpa üçün)
 * və bu cihazda Keystore açarı ilə (gündəlik açılış üçün, [DeviceKeyStore]).
 */
@Singleton
class VaultService @Inject constructor(
    private val users: UserDao,
    kdf: PasswordKdf,
    private val session: Session,
    private val deviceKeys: DeviceKeyStore,
) {
    private val vaultKeys = VaultKeys(kdf)

    suspend fun header(userId: Long): VaultHeader? = users.byId(userId)?.headerJson?.let(VaultHeader::fromJson)

    /** Cihazda saxlanılan DEK (profil açılanda). `null` — vault yoxdur və ya Təhlükəsizlik açarı tələb olunur. */
    fun loadDeviceKey(user: UserEntity): ByteArray? {
        val iv = user.deviceIv ?: return null
        val ct = user.deviceWrappedDek ?: return null
        return try {
            deviceKeys.unwrap(user.id, DeviceKeyStore.Wrapped(iv, ct))
        } catch (e: DeviceKeyStore.KeyUnavailableException) {
            AppLog.w("vault", "Cihaz açarı əlçatan deyil — Təhlükəsizlik açarı tələb olunacaq", e)
            null
        }
    }

    /** Yeni vault: təsadüfi DEK yaradılır və Təhlükəsizlik açarı ilə sarılır. @return Drive-a yazılacaq başlıq */
    suspend fun create(userId: Long, securityKey: CharArray): VaultHeader = withContext(Dispatchers.Default) {
        val created = try {
            vaultKeys.create(securityKey, Instant.now().toString())
        } finally {
            securityKey.wipe()
        }
        try {
            store(userId, created.header, created.dek)
            created.header
        } finally {
            created.dek.wipe()
        }
    }

    /** Drive-da tapılan vault-u açar və bu profilə bağlayır. */
    suspend fun adopt(userId: Long, remote: VaultHeader, securityKey: CharArray) = withContext(Dispatchers.Default) {
        val dek = unlock(remote, securityKey)
        try {
            store(userId, remote, dek)
        } finally {
            dek.wipe()
        }
    }

    /** Cihaz açarı itəndə: lokal başlıq nüsxəsini Təhlükəsizlik açarı ilə açıb DEK-i yenidən cihazda saxlayır. */
    suspend fun unlockWithKey(userId: Long, securityKey: CharArray) {
        val local = header(userId) ?: throw AccountException("Bu hesabın vault-u yoxdur")
        adopt(userId, local, securityKey)
    }

    /**
     * Açarın dəyişdirilməsi: eyni DEK yeni açarla yenidən sarılır. Nəticə SAXLANMIR —
     * çağıran tərəf əvvəl Drive-a yazmalı, sonra [saveHeader] çağırmalıdır.
     */
    suspend fun rewrap(userId: Long, oldKey: CharArray, newKey: CharArray): VaultHeader = withContext(Dispatchers.Default) {
        val local = header(userId) ?: throw AccountException("Bu hesabın vault-u yoxdur")
        try {
            vaultKeys.rewrap(local, oldKey, newKey)
        } catch (e: AuthenticationFailedException) {
            throw AccountException("Köhnə Təhlükəsizlik açarı yanlışdır")
        } finally {
            oldKey.wipe()
            newKey.wipe()
        }
    }

    /** Eyni vault-un yenilənmiş başlığı (açar dəyişib) lokal nüsxəyə yazılır. DEK dəyişmir. */
    suspend fun saveHeader(userId: Long, header: VaultHeader) {
        val user = users.byId(userId) ?: return
        val local = user.headerJson?.let(VaultHeader::fromJson)
        require(local == null || local.keyId == header.keyId) { "Başqa vault-un başlığı" }
        users.update(user.copy(headerJson = header.toJson()))
    }

    /** Vault-u bu profildən ayırır: lokal başlıq və cihaz açarı silinir, sessiyada açar sıfırlanır. */
    suspend fun clear(userId: Long) {
        deviceKeys.delete(userId)
        users.byId(userId)?.let { users.update(it.copy(headerJson = null, deviceWrappedDek = null, deviceIv = null)) }
        if (session.current?.userId == userId) session.detachVault()
    }

    /** @throws AccountException açar yanlışdırsa */
    fun unlock(header: VaultHeader, securityKey: CharArray): ByteArray = try {
        vaultKeys.unlock(header, securityKey)
    } catch (e: AuthenticationFailedException) {
        throw AccountException("Təhlükəsizlik açarı yanlışdır")
    } finally {
        securityKey.wipe()
    }

    private suspend fun store(userId: Long, header: VaultHeader, dek: ByteArray) {
        val user = users.byId(userId) ?: throw AccountException("İstifadəçi tapılmadı")
        val wrapped = deviceKeys.wrap(userId, dek)
        users.update(user.copy(headerJson = header.toJson(), deviceWrappedDek = wrapped.ciphertext, deviceIv = wrapped.iv))
        if (session.current?.userId == userId) session.attachVault(dek)
    }
}
