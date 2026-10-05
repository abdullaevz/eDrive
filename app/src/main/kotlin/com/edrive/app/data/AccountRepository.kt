package com.edrive.app.data

import javax.inject.Inject
import javax.inject.Singleton
import androidx.fragment.app.FragmentActivity
import com.edrive.app.data.db.dao.UserDao
import com.edrive.app.data.db.entity.UserEntity
import com.edrive.app.security.BiometricKeyStore
import com.edrive.crypto.AuthenticationFailedException
import com.edrive.crypto.PasswordKdf
import com.edrive.crypto.VaultHeader
import com.edrive.crypto.VaultKeys
import com.edrive.crypto.wipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant

class AccountException(message: String) : Exception(message)

/** Lokal hesablar: qeydiyyat, parol və ya barmaq izi ilə giriş. */
@Singleton
class AccountRepository @Inject constructor(
    private val users: UserDao,
    kdf: PasswordKdf,
    private val session: Session,
    private val biometric: BiometricKeyStore,
) {
    private val vaultKeys = VaultKeys(kdf)

    fun observeUsers(): Flow<List<UserEntity>> = users.observeAll()

    fun observeUser(id: Long): Flow<UserEntity?> = users.observe(id)

    /** Qeydiyyat: yeni vault yaradılır. Sessiya hələ açılmır — əvvəlcə bərpa sənədi saxlanmalıdır. */
    suspend fun register(username: String, password: CharArray): PendingAccount = withContext(Dispatchers.Default) {
        validateUsername(username)
        validatePassword(password)
        if (users.byUsername(username) != null) throw AccountException("Bu istifadəçi adı artıq bu cihazda mövcuddur")
        val created = vaultKeys.create(password, Instant.now().toString())
        val id = users.insert(
            UserEntity(username = username, headerJson = created.header.toJson(), createdAt = System.currentTimeMillis()),
        )
        PendingAccount(id, username, created.dek)
    }

    class PendingAccount(val userId: Long, val username: String, internal val dek: ByteArray)

    fun activate(pending: PendingAccount) {
        session.unlock(pending.userId, pending.username, pending.dek)
        pending.dek.wipe()
    }

    suspend fun login(username: String, password: CharArray) = withContext(Dispatchers.Default) {
        val user = users.byUsername(username.trim()) ?: throw AccountException("Belə istifadəçi bu cihazda tapılmadı")
        val dek = try {
            vaultKeys.unlock(VaultHeader.fromJson(user.headerJson), password)
        } catch (e: AuthenticationFailedException) {
            throw AccountException("Parol yanlışdır")
        } finally {
            password.wipe()
        }
        session.unlock(user.id, user.username, dek)
        dek.wipe()
        users.update(user.copy(lastLoginAt = System.currentTimeMillis()))
    }

    suspend fun loginWithBiometric(activity: FragmentActivity, userId: Long) {
        val user = users.byId(userId) ?: throw AccountException("İstifadəçi tapılmadı")
        val iv = user.bioIv
        val ct = user.bioWrappedDek
        if (iv == null || ct == null) throw AccountException("Barmaq izi bu hesab üçün aktiv deyil")
        val dek = try {
            biometric.unlock(activity, userId, BiometricKeyStore.Wrapped(iv, ct))
        } catch (e: BiometricKeyStore.InvalidatedException) {
            users.update(user.copy(bioIv = null, bioWrappedDek = null))
            throw AccountException("Barmaq izləri dəyişib — təhlükəsizlik üçün parolla daxil olun və yenidən aktivləşdirin")
        }
        session.unlock(user.id, user.username, dek)
        dek.wipe()
        users.update(user.copy(lastLoginAt = System.currentTimeMillis()))
    }

    suspend fun enableBiometric(activity: FragmentActivity) {
        val s = session.requireUser()
        val dek = session.requireKey()
        try {
            val wrapped = biometric.enroll(activity, s.userId, dek)
            val user = users.byId(s.userId)!!
            users.update(user.copy(bioIv = wrapped.iv, bioWrappedDek = wrapped.ciphertext))
        } finally {
            dek.wipe()
        }
    }

    suspend fun disableBiometric() {
        val s = session.requireUser()
        biometric.deleteKey(s.userId)
        users.byId(s.userId)?.let { users.update(it.copy(bioIv = null, bioWrappedDek = null)) }
    }

    /**
     * Drive-da başqa cihazda yaradılmış vault tapıldıqda: parolla onu açıb lokal vault-u onunla əvəz edirik.
     * Beləliklə yeni telefonda eyni istifadəçi adı + parolla köhnə fayllara çıxış bərpa olunur.
     */
    suspend fun adoptRemoteVault(remote: VaultHeader, password: CharArray) = withContext(Dispatchers.Default) {
        val s = session.requireUser()
        val dek = try {
            vaultKeys.unlock(remote, password)
        } catch (e: AuthenticationFailedException) {
            throw AccountException("Parol Drive-dakı vault-a uyğun gəlmir")
        } finally {
            password.wipe()
        }
        val user = users.byId(s.userId)!!
        biometric.deleteKey(user.id)
        users.update(user.copy(headerJson = remote.toJson(), bioIv = null, bioWrappedDek = null))
        session.unlock(user.id, user.username, dek)
        dek.wipe()
    }

    suspend fun header(userId: Long): VaultHeader = VaultHeader.fromJson(users.byId(userId)!!.headerJson)

    companion object {
        const val MIN_PASSWORD = 8
        private val USERNAME = Regex("^[\\p{L}0-9._-]{3,32}$")

        fun validateUsername(u: String) {
            if (!USERNAME.matches(u)) throw AccountException("İstifadəçi adı 3–32 simvol olmalıdır (hərf, rəqəm, . _ -)")
        }

        fun validatePassword(p: CharArray) {
            if (p.size < MIN_PASSWORD) throw AccountException("Parol ən azı $MIN_PASSWORD simvol olmalıdır")
        }
    }
}
