package com.edrive.app.data.vault

import com.edrive.app.data.VaultService
import com.edrive.app.data.db.entity.UserEntity
import com.edrive.app.drive.DriveClient
import com.edrive.app.drive.DriveClientProvider
import com.edrive.app.drive.DriveLayout
import com.edrive.app.util.AppLog
import com.edrive.crypto.VaultHeader
import javax.inject.Inject
import javax.inject.Singleton

/** Drive-da 1.x quruluşu (`eDrive Storage/<ad>/vault.json`) tapıldı, amma bu profilin vault-u deyil. */
class LegacyLayoutException(folder: String) : Exception(
    "Drive-da köhnə quruluş tapıldı: \"${DriveLayout.ROOT_FOLDER}/$folder\". Həmin qovluğun vault-u bu hesaba aid deyil. " +
        "Ya köhnə hesabla daxil olun, ya da həmin qovluğun içindəkiləri Drive-da \"${DriveLayout.ROOT_FOLDER}\" kökünə köçürün.",
)

/**
 * Drive-dakı `vault.json`-un oxunması və yerli nüsxə ilə müqayisəsi (`keyId` ilə — vault-u açmadan):
 *  - eyni `keyId`, fərqli başlıq → açar başqa cihazda dəyişib, yerli nüsxə yenilənir;
 *  - fərqli `keyId` → Drive-dakı vault başqadır, bu cihaz ora yükləməməlidir;
 *  - `vault.json` yoxdur → yerli nüsxədən bərpa olunur.
 */
@Singleton
class RemoteVaultMonitor @Inject constructor(
    private val drives: DriveClientProvider,
    private val vaults: VaultService,
) {
    enum class Status { SAME, KEY_CHANGED, DIFFERENT_VAULT, RESTORED }

    suspend fun read(api: DriveClient, rootId: String): VaultHeader? =
        api.findByName(DriveLayout.VAULT_FILE, rootId)?.let { parse(api, it.id) }

    /**
     * Kökdəki vault-u tapır. Kökdə yoxdursa, 1.x quruluşuna baxır: bu profilin vault-u alt qovluqdadırsa,
     * yalnız `vault.json` kökə köçürülür (fayllar həmin qovluqda qalır və proqramda adi qovluq kimi görünür).
     * @throws LegacyLayoutException köhnə quruluş başqa vault-a aiddirsə
     */
    suspend fun locate(api: DriveClient, rootId: String, localKeyId: String?): VaultHeader? {
        read(api, rootId)?.let { return it }
        val legacy = api.listChildren(rootId).filter { it.isFolder }.mapNotNull { folder ->
            api.findByName(DriveLayout.VAULT_FILE, folder.id)?.let { Triple(folder, it.id, parse(api, it.id)) }
        }
        legacy.firstOrNull { it.third.keyId == localKeyId }?.let { (folder, fileId, header) ->
            api.move(fileId, folder.id, rootId)
            AppLog.i("vault", "1.x quruluşu: vault.json kökə köçürüldü")
            return header
        }
        legacy.firstOrNull()?.let { throw LegacyLayoutException(it.first.name) }
        return null
    }

    /** Açıq profil üçün Drive-dakı vault-u yerli nüsxə ilə müqayisə edir. */
    suspend fun check(user: UserEntity): Status {
        val local = user.headerJson?.let(VaultHeader::fromJson) ?: error("Vault yoxdur")
        val api = drives.forAccount(checkNotNull(user.driveEmail))
        val rootId = checkNotNull(user.driveRootFolderId)
        val remote = read(api, rootId)
        return when {
            remote == null -> {
                api.uploadSmall(DriveLayout.VAULT_FILE, rootId, local.toJson().toByteArray(), JSON)
                AppLog.w("vault", "Drive-da vault.json yox idi — yerli nüsxədən bərpa olundu")
                Status.RESTORED
            }
            remote.keyId != local.keyId -> Status.DIFFERENT_VAULT
            remote == local -> Status.SAME
            else -> {
                vaults.saveHeader(user.id, remote)
                Status.KEY_CHANGED
            }
        }
    }

    private suspend fun parse(api: DriveClient, fileId: String) = VaultHeader.fromJson(api.downloadBytes(fileId).decodeToString())

    companion object {
        const val JSON = "application/json"
    }
}
