package com.edrive.app

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.room.Room
import com.edrive.app.data.AccountRepository
import com.edrive.app.data.Session
import com.edrive.app.data.VaultService
import com.edrive.app.data.db.AppDatabase
import com.edrive.app.data.db.entity.FileEntity
import com.edrive.app.data.vault.DriveConnectionService
import com.edrive.app.data.vault.FileAccessService
import com.edrive.app.data.vault.ImportService
import com.edrive.app.data.vault.LocalVaultStore
import com.edrive.app.data.vault.RemoteVaultMonitor
import com.edrive.app.data.vault.SyncService
import com.edrive.app.data.vault.ThumbnailCache
import com.edrive.app.data.vault.UploadService
import com.edrive.app.security.BiometricGate
import com.edrive.app.security.DeviceKeyStore
import com.edrive.app.security.PinHasher
import com.edrive.app.util.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Robolectric-də Android Keystore yoxdur — PIN HMAC-ı sabit test açarı ilə. */
class FakePinHasher : PinHasher {
    override fun hash(pin: CharArray, salt: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(ByteArray(32) { 7 }, "HmacSHA256"))
            doFinal(salt + String(pin).toByteArray())
        }
}

/** Cihaz açarının yaddaşdakı əvəzi. [lose] — Keystore açarının itməsini təqlid edir. */
class FakeDeviceKeys : DeviceKeyStore {
    private val keys = HashMap<Long, ByteArray>()

    override fun wrap(userId: Long, dek: ByteArray): DeviceKeyStore.Wrapped {
        val key = keys.getOrPut(userId) { com.edrive.crypto.AesGcm.newKey() }
        val sealed = com.edrive.crypto.AesGcm.seal(key, dek, "device")
        return DeviceKeyStore.Wrapped(sealed.copyOfRange(0, 12), sealed.copyOfRange(12, sealed.size))
    }

    override fun unwrap(userId: Long, wrapped: DeviceKeyStore.Wrapped): ByteArray {
        val key = keys[userId] ?: throw DeviceKeyStore.KeyUnavailableException()
        return com.edrive.crypto.AesGcm.open(key, wrapped.iv + wrapped.ciphertext, "device")
    }

    override fun delete(userId: Long) { keys.remove(userId) }

    fun lose(userId: Long) { keys.remove(userId) }
}

class FakeBiometricGate(var answer: Boolean = true) : BiometricGate {
    override fun isAvailable() = true
    override suspend fun confirm(activity: FragmentActivity, title: String, subtitle: String) = answer
}

class FakeClock(var time: Long = 1_800_000_000_000L) : Clock {
    override fun now() = time
}

/**
 * Bir telefonu təqlid edir: öz bazası, öz sessiyası və cihaz açarları, paylaşılan saxta Drive.
 * Real servislər əl ilə qurulur (Hilt-siz) — qraf istehsal kodundakı ilə eynidir.
 */
class TestPhone(ctx: Context, drive: FakeDrive = FakeDrive()) {
    val db: AppDatabase = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
    val session = Session()
    val clock = FakeClock()
    val deviceKeys = FakeDeviceKeys()
    val biometric = FakeBiometricGate()
    val vaults = VaultService(db.users(), bcKdf, session, deviceKeys)
    val accounts = AccountRepository(db.users(), session, FakePinHasher(), biometric, vaults, clock)
    private val cache = ThumbnailCache()
    val store = LocalVaultStore(ctx, db.files(), cache)
    var scheduled = 0
    private val scheduler = { scheduled++; Unit }
    val monitor = RemoteVaultMonitor(drive, vaults)
    val sync = SyncService(session, db.users(), db.files(), db.folders(), store, drive, clock)
    val connection = DriveConnectionService(
        session, db.users(), db.files(), db.folders(), vaults, monitor, store, drive, drive, sync, scheduler,
    )
    val importer = ImportService(ctx, session, db.files(), store, scheduler)
    val uploads = UploadService(db.users(), db.files(), store, drive, scheduler, monitor)
    val fileAccess = FileAccessService(ctx, session, db.users(), db.files(), store, drive, cache)

    fun register(name: String, pin: String = "4826") = runBlocking {
        accounts.activate(accounts.register(name, pin.toCharArray(), pin.toCharArray()))
    }

    fun files(): List<FileEntity> = runBlocking { db.files().observe(session.requireUser().userId).first() }

    fun close() = db.close()
}
