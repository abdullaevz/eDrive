package com.edrive.app.data.vault

import javax.inject.Inject
import javax.inject.Singleton
import com.edrive.app.data.AccountRepository
import com.edrive.app.data.Session
import com.edrive.app.data.db.dao.FileDao
import com.edrive.app.data.db.dao.UserDao
import com.edrive.app.drive.AuthResult
import com.edrive.app.drive.DriveAuthorizer
import com.edrive.app.drive.DriveClientProvider
import com.edrive.app.drive.DriveConsentRequired
import com.edrive.app.drive.DriveLayout
import com.edrive.app.util.AppLog
import com.edrive.crypto.VaultHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Google Drive-a qoşulma və ayrılma. Qoşulanda:
 *  1. "eDrive Storage/<istifadəçi adı>" qovluqları hazırlanır;
 *  2. vault başlığı (vault.json) Drive ilə uzlaşdırılır — Drive-da başqa cihazdan qalmış vault varsa,
 *     parol ilə təsdiq tələb olunur (yeni telefonda bərpa ssenarisi);
 *  3. fayl siyahısı sinxronlaşdırılır və gözləyən yükləmələr başladılır.
 */
@Singleton
class DriveConnectionService @Inject constructor(
    private val session: Session,
    private val users: UserDao,
    private val files: FileDao,
    private val accounts: AccountRepository,
    private val auth: DriveAuthorizer,
    private val drives: DriveClientProvider,
    private val sync: SyncService,
    private val uploads: UploadScheduler,
) {
    sealed interface ConnectOutcome {
        data class Connected(val email: String, val imported: Int) : ConnectOutcome
        /** Drive-da başqa cihazda yaradılmış vault var — parol ilə təsdiq lazımdır. */
        data class NeedsPassword(val remote: VaultHeader) : ConnectOutcome
    }

    /** 1-ci addım: istifadəçinin seçdiyi Google hesabı üçün icazə. Token hazırdırsa birbaşa bağlanır. */
    suspend fun startConnect(email: String): ConnectOutcome = when (val r = auth.authorize(email)) {
        is AuthResult.NeedsConsent -> throw DriveConsentRequired(r.pendingIntent)
        is AuthResult.Token -> finishConnect(r.accessToken)
    }

    /** 2-ci addım: token alındıqdan sonra qovluqları hazırlayır və vault başlığını uzlaşdırır. */
    suspend fun finishConnect(token: String): ConnectOutcome = withContext(Dispatchers.IO) {
        val s = session.requireUser()
        val email = drives.withToken(token).about().user.emailAddress
        val api = drives.forAccount(email)

        val rootId = api.ensureFolder(DriveLayout.ROOT_FOLDER, null)
        val userFolder = api.ensureFolder(s.username, rootId)

        val local = accounts.header(s.userId)
        val remoteFile = api.findByName(DriveLayout.VAULT_FILE, userFolder)
        val user = users.byId(s.userId)!!
        users.update(user.copy(driveEmail = email, driveRootFolderId = rootId, driveUserFolderId = userFolder))

        if (remoteFile == null) {
            api.uploadSmall(DriveLayout.VAULT_FILE, userFolder, local.toJson().toByteArray(), "application/json")
        } else {
            val remote = VaultHeader.fromJson(api.downloadBytes(remoteFile.id).decodeToString())
            if (remote.keyId != local.keyId) {
                if (files.count(s.userId) > 0) {
                    users.update(user)
                    throw IllegalStateException(
                        "Bu Google hesabındakı \"${DriveLayout.ROOT_FOLDER}/${s.username}\" qovluğu başqa açarla yaradılıb. " +
                            "Başqa istifadəçi adı və ya Google hesabı seçin.",
                    )
                }
                return@withContext ConnectOutcome.NeedsPassword(remote)
            }
        }
        val imported = sync.sync()
        uploads.schedule()
        AppLog.i("drive", "Drive qoşuldu, bərpa olunan fayl: $imported")
        ConnectOutcome.Connected(email, imported)
    }

    /** Drive-dakı vault-u parolla açıb bu cihazdakı hesaba bağlayır (yeni telefonda bərpa ssenarisi). */
    suspend fun adoptRemote(remote: VaultHeader, password: CharArray): ConnectOutcome {
        accounts.adoptRemoteVault(remote, password)
        val email = users.byId(session.requireUser().userId)?.driveEmail.orEmpty()
        return ConnectOutcome.Connected(email, sync.sync())
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        val user = users.byId(session.requireUser().userId) ?: return@withContext
        user.driveEmail?.let { email ->
            runCatching { (auth.authorize(email) as? AuthResult.Token)?.let { auth.revoke(it.accessToken) } }
            auth.forgetAccount(email)
        }
        users.update(user.copy(driveEmail = null, driveRootFolderId = null, driveUserFolderId = null))
    }
}
