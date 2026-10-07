package com.edrive.app.data

import androidx.fragment.app.FragmentActivity
import com.edrive.app.data.db.dao.UserDao
import com.edrive.app.data.db.entity.UserEntity
import com.edrive.app.security.BiometricGate
import com.edrive.app.security.PinHasher
import com.edrive.app.util.Clock
import com.edrive.crypto.AesGcm
import com.edrive.crypto.VaultHeader
import com.edrive.crypto.wipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

class AccountException(message: String) : Exception(message)

/** Yanlış PIN cəhdləri çox olduğu üçün giriş müvəqqəti bağlanıb. */
class PinLockedException(val untilMillis: Long) : Exception("PIN müvəqqəti bloklanıb")

/**
 * Lokal profillər: qeydiyyat, PIN və ya barmaq izi ilə giriş, PIN dəyişmə.
 * PIN yalnız proqramın qapısıdır; profil açılanda vault (varsa) cihaz açarı ilə avtomatik yüklənir ([VaultService]).
 */
@Singleton
class AccountRepository @Inject constructor(
    private val users: UserDao,
    private val session: Session,
    private val pins: PinHasher,
    private val biometric: BiometricGate,
    private val vaults: VaultService,
    private val clock: Clock,
) {
    fun observeUsers(): Flow<List<UserEntity>> = users.observeAll()

    fun observeUser(id: Long): Flow<UserEntity?> = users.observe(id)

    /** Qeydiyyat: yalnız istifadəçi adı və PIN. Vault Drive-a qoşulanda yaranır. Sessiya [activate] ilə açılır. */
    suspend fun register(username: String, pin: CharArray, confirm: CharArray): PendingAccount = withContext(Dispatchers.Default) {
        try {
            validateUsername(username)
            PinPolicy.validate(pin)
            if (!pin.contentEquals(confirm)) throw AccountException("PIN-lər uyğun gəlmir")
            if (users.byUsername(username) != null) throw AccountException("Bu istifadəçi adı artıq bu cihazda mövcuddur")
            val salt = AesGcm.randomBytes(16)
            val id = users.insert(
                UserEntity(username = username, createdAt = clock.now(), pinHash = pins.hash(pin, salt), pinSalt = salt),
            )
            PendingAccount(id, username)
        } finally {
            confirm.wipe()
        }
    }

    class PendingAccount(val userId: Long, val username: String)

    suspend fun activate(pending: PendingAccount) {
        val user = users.byId(pending.userId) ?: throw AccountException("İstifadəçi tapılmadı")
        open(user)
    }

    /**
     * PIN ilə giriş. Hər [PinPolicy.ATTEMPTS_PER_STEP] yanlış cəhddən sonra artan gözləmə tətbiq olunur.
     * @throws PinLockedException gözləmə müddəti bitməyibsə
     */
    suspend fun login(username: String, pin: CharArray) = withContext(Dispatchers.Default) {
        try {
            val user = users.byUsername(username.trim()) ?: throw AccountException("Belə istifadəçi bu cihazda tapılmadı")
            if (!user.hasPin) throw AccountException("Bu hesab köhnə versiyadandır — köhnə parolla daxil olun")
            if (clock.now() < user.lockedUntil) throw PinLockedException(user.lockedUntil)
            if (!MessageDigest.isEqual(pins.hash(pin, user.pinSalt!!), user.pinHash)) {
                val failures = user.failedAttempts + 1
                val wait = PinPolicy.lockoutMillis(failures)
                users.update(user.copy(failedAttempts = failures, lockedUntil = if (wait > 0) clock.now() + wait else 0))
                if (wait > 0) throw PinLockedException(clock.now() + wait)
                throw AccountException("PIN yanlışdır")
            }
            open(user)
        } finally {
            pin.wipe()
        }
    }

    /** Barmaq izi yalnız qapını açır — PIN-in alternativi. @return false — istifadəçi "PIN ilə" seçdi */
    suspend fun loginWithBiometric(activity: FragmentActivity, userId: Long): Boolean {
        val user = users.byId(userId) ?: throw AccountException("İstifadəçi tapılmadı")
        if (!user.biometricEnabled || !user.hasPin) throw AccountException("Barmaq izi bu hesab üçün aktiv deyil")
        if (clock.now() < user.lockedUntil) throw PinLockedException(user.lockedUntil)
        if (!biometric.confirm(activity, "eDrive-ı aç", "Davam etmək üçün barmaq izinizi təsdiqləyin")) return false
        open(user)
        return true
    }

    /**
     * 1.x hesabının yeni modelə keçidi: köhnə parol (indi Təhlükəsizlik açarı) lokal vault-u açır,
     * DEK cihaz açarı ilə saxlanılır və yeni PIN təyin edilir. Fayllar və vault dəyişmir.
     */
    suspend fun migrateLegacy(username: String, oldPassword: CharArray, pin: CharArray, confirm: CharArray) = withContext(Dispatchers.Default) {
        try {
            val user = users.byUsername(username.trim()) ?: throw AccountException("Belə istifadəçi bu cihazda tapılmadı")
            check(!user.hasPin) { "Hesab artıq köçürülüb" }
            PinPolicy.validate(pin)
            if (!pin.contentEquals(confirm)) throw AccountException("PIN-lər uyğun gəlmir")
            val header = user.headerJson?.let(VaultHeader::fromJson) ?: throw AccountException("Bu hesabın vault-u yoxdur")
            vaults.adopt(user.id, header, oldPassword) // parol yanlışdırsa AccountException, heç nə dəyişmir
            val salt = AesGcm.randomBytes(16)
            val migrated = users.byId(user.id)!!.copy(pinHash = pins.hash(pin, salt), pinSalt = salt)
            users.update(migrated)
            open(migrated)
        } finally {
            oldPassword.wipe()
            pin.wipe()
            confirm.wipe()
        }
    }

    suspend fun changePin(oldPin: CharArray, newPin: CharArray, confirm: CharArray) = withContext(Dispatchers.Default) {
        try {
            val user = users.byId(session.requireUser().userId) ?: throw AccountException("İstifadəçi tapılmadı")
            if (!MessageDigest.isEqual(pins.hash(oldPin, user.pinSalt!!), user.pinHash)) throw AccountException("Köhnə PIN yanlışdır")
            PinPolicy.validate(newPin)
            if (!newPin.contentEquals(confirm)) throw AccountException("PIN-lər uyğun gəlmir")
            val salt = AesGcm.randomBytes(16)
            users.update(user.copy(pinHash = pins.hash(newPin, salt), pinSalt = salt))
        } finally {
            oldPin.wipe()
            newPin.wipe()
            confirm.wipe()
        }
    }

    /** Barmaq izini aktivləşdirmək üçün əvvəlcə təsdiq alınır (sensor işləyirmi). */
    suspend fun setBiometric(activity: FragmentActivity, enabled: Boolean) {
        val user = users.byId(session.requireUser().userId) ?: return
        if (enabled && !biometric.confirm(activity, "Barmaq izini aktivləşdir", "Növbəti girişlərdə PIN əvəzinə barmaq izi")) return
        users.update(user.copy(biometricEnabled = enabled))
    }

    /** Profili açır: uğursuz cəhdlər sıfırlanır, vault (varsa) cihaz açarı ilə yüklənir. */
    private suspend fun open(user: UserEntity) {
        val dek = vaults.loadDeviceKey(user)
        try {
            session.unlock(user.id, user.username, dek)
        } finally {
            dek?.wipe()
        }
        users.update(user.copy(failedAttempts = 0, lockedUntil = 0, lastLoginAt = clock.now()))
    }

    companion object {
        private val USERNAME = Regex("^[\\p{L}0-9._-]{3,32}$")

        fun validateUsername(u: String) {
            if (!USERNAME.matches(u)) throw AccountException("İstifadəçi adı 3–32 simvol olmalıdır (hərf, rəqəm, . _ -)")
        }
    }
}
