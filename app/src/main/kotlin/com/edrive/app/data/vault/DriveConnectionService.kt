package com.edrive.app.data.vault

import com.edrive.app.data.AccountException
import com.edrive.app.data.SecurityKeyPolicy
import com.edrive.app.data.Session
import com.edrive.app.data.VaultService
import com.edrive.app.data.db.dao.FileDao
import com.edrive.app.data.db.dao.FolderDao
import com.edrive.app.data.db.dao.UserDao
import com.edrive.app.data.db.entity.FileStatus
import com.edrive.app.drive.AuthResult
import com.edrive.app.drive.DriveAuthorizer
import com.edrive.app.drive.DriveClientProvider
import com.edrive.app.drive.DriveConsentRequired
import com.edrive.app.drive.DriveLayout
import com.edrive.app.util.AppLog
import com.edrive.crypto.VaultHeader
import com.edrive.crypto.wipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Drive-a qoşulma və ayrılma. Vault Drive-a bağlıdır: `eDrive Storage/vault.json`.
 *
 * Qoşulanda (Google hesabı seçildikdən sonra):
 *  - Drive-da vault yoxdur, profildə də yoxdur → yeni Təhlükəsizlik açarı təyin edilir ([createVault]);
 *  - Drive-da vault var → onun Təhlükəsizlik açarı soruşulur ([adoptVault]);
 *  - profildə vault var (1.x hesabı), Drive-da yoxdur → yerli vault Drive-a yazılır.
 * Vault həll olunana qədər profilə heç nə yazılmır — istifadəçi imtina etsə, iz qalmır.
 */
@Singleton
class DriveConnectionService @Inject constructor(
    private val session: Session,
    private val users: UserDao,
    private val files: FileDao,
    private val folders: FolderDao,
    private val vaults: VaultService,
    private val monitor: RemoteVaultMonitor,
    private val store: LocalVaultStore,
    private val auth: DriveAuthorizer,
    private val drives: DriveClientProvider,
    private val sync: SyncService,
    private val uploads: UploadScheduler,
) {
    /** Seçilmiş, amma hələ profilə yazılmamış Drive. */
    data class PendingDrive(val email: String, val rootId: String)

    sealed interface ConnectOutcome {
        data class Connected(val email: String, val imported: Int) : ConnectOutcome
        /** Drive-da vault yoxdur — yeni Təhlükəsizlik açarı təyin edilməlidir. */
        data class NeedsNewKey(val drive: PendingDrive) : ConnectOutcome
        /** Drive-da vault var — onun Təhlükəsizlik açarı tələb olunur. */
        data class NeedsKey(val drive: PendingDrive, val remote: VaultHeader) : ConnectOutcome
    }

    /** 1-ci addım: istifadəçinin seçdiyi Google hesabı üçün icazə. Token hazırdırsa birbaşa davam edir. */
    suspend fun startConnect(email: String): ConnectOutcome = when (val r = auth.authorize(email)) {
        is AuthResult.NeedsConsent -> throw DriveConsentRequired(r.pendingIntent)
        is AuthResult.Token -> finishConnect(r.accessToken)
    }

    /** 2-ci addım: token alındıqdan sonra kök qovluğu hazırlayır və vault-u müəyyənləşdirir. */
    suspend fun finishConnect(token: String): ConnectOutcome = withContext(Dispatchers.IO) {
        val userId = session.requireUser().userId
        val email = drives.withToken(token).about().user.emailAddress
        val api = drives.forAccount(email)
        val drive = PendingDrive(email, api.ensureFolder(DriveLayout.ROOT_FOLDER, null))
        val local = vaults.header(userId)
        val remote = monitor.locate(api, drive.rootId, local?.keyId)
        when {
            remote == null && local == null -> ConnectOutcome.NeedsNewKey(drive)
            remote == null -> {
                api.uploadSmall(DriveLayout.VAULT_FILE, drive.rootId, local!!.toJson().toByteArray(), RemoteVaultMonitor.JSON)
                complete(userId, drive)
            }
            local != null && remote.keyId == local.keyId -> {
                if (remote != local) vaults.saveHeader(userId, remote) // açar başqa cihazda dəyişib
                complete(userId, drive)
            }
            local != null && files.count(userId) > 0 -> throw AccountException(
                "Bu Google Drive-dakı vault başqa açarla yaradılıb, bu hesabda isə başqa vault-un faylları var. " +
                    "Bu Drive üçün yeni hesab yaradın.",
            )
            else -> ConnectOutcome.NeedsKey(drive, remote)
        }
    }

    /** Drive-da vault yoxdur: yeni vault yaradılır, `vault.json` Drive-a yazılır. */
    suspend fun createVault(drive: PendingDrive, securityKey: CharArray, confirm: CharArray): VaultHeader = withContext(Dispatchers.IO) {
        try {
            SecurityKeyPolicy.validate(securityKey, confirm)
        } catch (e: AccountException) {
            securityKey.wipe()
            throw e
        } finally {
            confirm.wipe()
        }
        val userId = session.requireUser().userId
        val header = vaults.create(userId, securityKey)
        drives.forAccount(drive.email).uploadSmall(DriveLayout.VAULT_FILE, drive.rootId, header.toJson().toByteArray(), RemoteVaultMonitor.JSON)
        complete(userId, drive)
        header
    }

    /** Drive-dakı vault-u Təhlükəsizlik açarı ilə açıb bu profilə bağlayır (yeni telefon, ikinci hesab). */
    suspend fun adoptVault(drive: PendingDrive, remote: VaultHeader, securityKey: CharArray): ConnectOutcome = withContext(Dispatchers.IO) {
        val userId = session.requireUser().userId
        vaults.adopt(userId, remote, securityKey)
        complete(userId, drive)
    }

    /**
     * Təhlükəsizlik açarının dəyişdirilməsi: eyni DEK yeni açarla sarılır (fayllar dəyişmir).
     * Əvvəl Drive-dakı `vault.json` yenilənir, yalnız sonra yerli nüsxə — Drive həmişə ən yeni başlığı saxlayır.
     */
    suspend fun changeKey(oldKey: CharArray, newKey: CharArray, confirm: CharArray): VaultHeader = withContext(Dispatchers.IO) {
        try {
            SecurityKeyPolicy.validate(newKey, confirm)
        } catch (e: AccountException) {
            oldKey.wipe()
            newKey.wipe()
            throw e
        } finally {
            confirm.wipe()
        }
        val userId = session.requireUser().userId
        val user = users.byId(userId)?.takeIf { it.isDriveReady } ?: throw AccountException("Əvvəlcə Google Drive-a qoşulun")
        val header = vaults.rewrap(userId, oldKey, newKey)
        val api = drives.forAccount(user.driveEmail!!)
        val existing = api.findByName(DriveLayout.VAULT_FILE, user.driveRootFolderId!!)
        api.uploadSmall(DriveLayout.VAULT_FILE, user.driveRootFolderId, header.toJson().toByteArray(), RemoteVaultMonitor.JSON, existing?.id)
        vaults.saveHeader(userId, header)
        AppLog.i("vault", "Təhlükəsizlik açarı dəyişdirildi")
        header
    }

    /** Ayrılma zamanı itəcək fayllar: hələ Drive-a çatmamış olanlar (LOCAL-lar bilərəkdən sayılmır). */
    suspend fun unsyncedCount(): Int {
        val userId = session.requireUser().userId
        return files.all(userId).count { it.status in UNSYNCED }
    }

    /**
     * Drive-dan ayrılma: icazə geri alınır və bu profildəki bütün lokal izlər silinir —
     * şifrəli nüsxələr, miniatürlər, fayl və qovluq indeksi, vault başlığı və cihaz açarı.
     * Drive-dakı fayllara və telefonun qalereyasındakı orijinallara toxunulmur.
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        val userId = session.requireUser().userId
        val user = users.byId(userId) ?: return@withContext
        user.driveEmail?.let { email ->
            runCatching { (auth.authorize(email) as? AuthResult.Token)?.let { auth.revoke(it.accessToken) } }
            auth.forgetAccount(email)
        }
        files.all(userId).forEach { store.removeLocal(it) }
        folders.deleteAll(userId)
        vaults.clear(userId)
        users.byId(userId)?.let { users.update(it.copy(driveEmail = null, driveRootFolderId = null)) }
        AppLog.i("drive", "Drive ayrıldı, lokal keş təmizləndi")
    }

    private suspend fun complete(userId: Long, drive: PendingDrive): ConnectOutcome.Connected {
        val user = users.byId(userId) ?: throw AccountException("İstifadəçi tapılmadı")
        users.update(user.copy(driveEmail = drive.email, driveRootFolderId = drive.rootId))
        val imported = sync.sync()
        uploads.schedule()
        AppLog.i("drive", "Drive qoşuldu, bərpa olunan fayl: $imported")
        return ConnectOutcome.Connected(drive.email, imported)
    }

    private companion object {
        val UNSYNCED = setOf(FileStatus.ENCRYPTING, FileStatus.PENDING, FileStatus.UPLOADING, FileStatus.FAILED)
    }
}
