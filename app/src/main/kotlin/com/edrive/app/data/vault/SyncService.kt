package com.edrive.app.data.vault

import javax.inject.Inject
import javax.inject.Singleton
import com.edrive.app.data.Session
import com.edrive.app.data.db.dao.FileDao
import com.edrive.app.data.db.dao.FolderDao
import com.edrive.app.data.db.dao.UserDao
import com.edrive.app.data.db.entity.FileEntity
import com.edrive.app.data.db.entity.FileStatus
import com.edrive.app.data.db.entity.FolderEntity
import com.edrive.app.drive.DriveClient
import com.edrive.app.drive.DriveClientProvider
import com.edrive.app.drive.DriveFile
import com.edrive.app.drive.DriveLayout
import com.edrive.app.util.AppLog
import com.edrive.app.util.Clock
import com.edrive.crypto.AuthenticationFailedException
import com.edrive.crypto.ItemManifest
import com.edrive.crypto.wipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

/**
 * Drive-dakı "eDrive Storage" ağacını (kök + alt qovluqlar, [DriveLayout.MAX_DEPTH] səviyyəyə qədər) lokal indekslə müqayisə edir:
 *  - qovluq siyahısı Drive ilə eyniləşdirilir;
 *  - Drive-da olub telefonda olmayan fayllar əlavə olunur (yeni telefon, başqa cihazdan yüklənənlər);
 *  - başqa cihazda köçürülmüş faylların qovluğu yenilənir;
 *  - telefonda "Drive-da" kimi qeyd olunub Drive-da olmayanlar lokal keşdən silinir (başqa cihazdan silinənlər).
 *
 * Faylın yeri onun `.meta`-sının qovluğudur. Köçürmə yarımçıq qalıbsa (`.edrv` başqa qovluqdadır), `.edrv` `.meta`-nın yanına çəkilir.
 */
@Singleton
class SyncService @Inject constructor(
    private val session: Session,
    private val users: UserDao,
    private val files: FileDao,
    private val folders: FolderDao,
    private val store: LocalVaultStore,
    private val drives: DriveClientProvider,
    private val clock: Clock,
) {
    /** Drive-dakı element və onun qovluğu (`null` = kök). */
    private class Located(val file: DriveFile, val folderId: String?)

    private class Tree(val folders: List<FolderEntity>, val metas: Map<String, Located>, val data: Map<String, Located>)

    /** @return əlavə olunan faylların sayı */
    suspend fun sync(): Int = withContext(Dispatchers.IO) {
        val s = session.requireUser()
        val user = users.byId(s.userId) ?: return@withContext 0
        val root = user.driveRootFolderId ?: return@withContext 0
        val api = drives.forAccount(user.driveEmail!!)
        val tree = walk(api, s.userId, root)

        val remoteFolders = tree.folders.mapTo(HashSet()) { it.id }
        tree.folders.forEach { remote ->
            val local = folders.get(s.userId, remote.id)
            folders.upsert(remote.copy(createdAt = local?.createdAt ?: remote.createdAt))
        }
        folders.all(s.userId).filter { it.id !in remoteFolders }.forEach { folders.delete(s.userId, it.id) }

        val remoteIds = HashSet<String>()
        val dek = session.requireKey()
        var added = 0
        try {
            for ((id, meta) in tree.metas) {
                val data = tree.data[id] ?: continue // yarımçıq yükləmə
                if (data.folderId != meta.folderId) {
                    api.move(data.file.id, data.folderId ?: root, meta.folderId ?: root)
                    AppLog.w("sync", "Yarımçıq köçürmə düzəldildi")
                }
                remoteIds += id
                val local = files.get(s.userId, id)
                if (local != null) {
                    if (local.folderId != meta.folderId) files.setFolder(s.userId, id, meta.folderId)
                    continue
                }
                val manifest = try {
                    ItemManifest.open(dek, id, api.downloadBytes(meta.file.id))
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
                        driveDataId = data.file.id, driveMetaId = meta.file.id, folderId = meta.folderId,
                    ),
                )
                added++
            }
            for (f in files.all(s.userId)) {
                if (f.status == FileStatus.SYNCED && f.id !in remoteIds) store.removeLocal(f)
            }
        } finally {
            dek.wipe()
        }
        added
    }

    /** Kökdən başlayaraq ağacı gəzir (enina, səviyyə-səviyyə). */
    private suspend fun walk(api: DriveClient, userId: Long, root: String): Tree {
        val folderList = mutableListOf<FolderEntity>()
        val metas = HashMap<String, Located>()
        val data = HashMap<String, Located>()
        var level = listOf<String?>(null)
        var depth = 0
        while (level.isNotEmpty()) {
            val next = mutableListOf<String?>()
            for (folderId in level) {
                for (child in api.listChildren(folderId ?: root)) {
                    when {
                        child.isFolder -> if (depth < DriveLayout.MAX_DEPTH) {
                            folderList += FolderEntity(child.id, userId, child.name, folderId, clock.now())
                            next += child.id
                        }
                        child.name.endsWith(META) -> metas[child.name.removeSuffix(META)] = Located(child, folderId)
                        child.name.endsWith(DATA) -> data[child.name.removeSuffix(DATA)] = Located(child, folderId)
                    }
                }
            }
            level = next
            depth++
        }
        return Tree(folderList, metas, data)
    }

    private companion object {
        const val META = ".meta"
        const val DATA = ".edrv"
    }
}
