package com.edrive.app.data.vault

import javax.inject.Inject
import javax.inject.Singleton
import com.edrive.app.data.Session
import com.edrive.app.data.db.dao.FileDao
import com.edrive.app.data.db.dao.UserDao
import com.edrive.app.data.db.entity.FileEntity
import com.edrive.app.data.db.entity.FileStatus
import com.edrive.app.drive.DriveClientProvider
import com.edrive.app.drive.DriveLayout
import com.edrive.crypto.AuthenticationFailedException
import com.edrive.crypto.ItemManifest
import com.edrive.crypto.wipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

/**
 * Drive-dakı şifrəli manifestləri lokal indekslə müqayisə edir:
 *  - Drive-da olub telefonda olmayanlar əlavə olunur (yeni telefon, başqa cihazdan yüklənənlər);
 *  - telefonda "Drive-da" kimi qeyd olunub Drive-da olmayanlar silinir (başqa cihazdan silinənlər).
 */
@Singleton
class SyncService @Inject constructor(
    private val session: Session,
    private val users: UserDao,
    private val files: FileDao,
    private val store: LocalVaultStore,
    private val drives: DriveClientProvider,
) {
    /** @return əlavə olunan faylların sayı */
    suspend fun sync(): Int = withContext(Dispatchers.IO) {
        val s = session.requireUser()
        val user = users.byId(s.userId) ?: return@withContext 0
        val folder = user.driveUserFolderId ?: return@withContext 0
        val api = drives.forAccount(user.driveEmail!!)
        val children = api.listChildren(folder)
        val byName = children.associateBy { it.name }
        val remoteIds = mutableSetOf<String>()
        val local = files.ids(s.userId).toSet()
        val dek = session.requireKey()
        var added = 0
        try {
            for (meta in children.filter { it.name.endsWith(".meta") }) {
                val id = meta.name.removeSuffix(".meta")
                val data = byName[DriveLayout.dataName(id)] ?: continue // yarımçıq yükləmə
                remoteIds += id
                if (id in local) continue
                val manifest = try {
                    ItemManifest.open(dek, id, api.downloadBytes(meta.id))
                } catch (e: AuthenticationFailedException) {
                    continue // başqa açarla şifrələnib — atlanır
                }
                val thumb = manifest.thumbnail?.let { Base64.getDecoder().decode(it) }
                if (thumb != null) store.writeThumb(s.userId, id, thumb, dek)
                files.upsert(
                    FileEntity(
                        id = id, userId = s.userId, name = manifest.name, mimeType = manifest.mimeType,
                        size = manifest.size, createdAt = manifest.createdAt, width = manifest.width, height = manifest.height,
                        hasThumb = thumb != null, status = FileStatus.SYNCED, progress = 1f,
                        driveDataId = data.id, driveMetaId = meta.id,
                    ),
                )
                added++
            }
            for (id in local) {
                val f = files.get(id) ?: continue
                if (f.status == FileStatus.SYNCED && id !in remoteIds) store.removeLocal(f)
            }
        } finally {
            dek.wipe()
        }
        added
    }
}
