package com.edrive.app.data.vault

import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import android.net.Uri
import com.edrive.app.data.Session
import com.edrive.app.data.VaultLockedException
import com.edrive.app.data.db.dao.FileDao
import com.edrive.app.data.db.entity.FileEntity
import com.edrive.app.data.db.entity.FileStatus
import com.edrive.app.util.Media
import com.edrive.app.util.PickedFile
import com.edrive.crypto.ItemManifest
import com.edrive.crypto.StreamingCipher
import com.edrive.crypto.wipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.Base64
import java.util.UUID

/**
 * Telefondan seçilmiş faylları şifrələyib yükləmə növbəsinə qoyur:
 * ```
 *  fayl ──oxu──► AES-256-GCM (STREAM) ──► outbox/<id>.edrv   (yalnız şifrəli!)
 *       └──► miniatür ──► şifrəli manifest ──► outbox/<id>.meta
 * ```
 * Şifrələmə dərhal olur, Drive-a yükləməni isə [UploadScheduler] arxa fonda başladır.
 */
@Singleton
class ImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: Session,
    private val files: FileDao,
    private val store: LocalVaultStore,
    private val uploads: UploadScheduler,
) {
    /** @param folderId hədəf qovluq (Drive ID), `null` — kök */
    suspend fun import(uris: List<Uri>, folderId: String? = null) = withContext(Dispatchers.IO) {
        val s = session.requireUser()
        if (!s.hasVault) throw VaultLockedException() // vault olmadan heç bir sətir yaradılmır
        for (uri in uris) {
            val picked = Media.describe(context.contentResolver, uri)
            val id = UUID.randomUUID().toString().replace("-", "")
            val now = System.currentTimeMillis()
            files.upsert(
                FileEntity(id = id, userId = s.userId, name = picked.name, mimeType = picked.mimeType,
                    size = picked.size, createdAt = now, status = FileStatus.ENCRYPTING, folderId = folderId),
            )
            try {
                encryptToOutbox(s.userId, id, picked, now, folderId)
            } catch (e: Exception) {
                store.outboxData(s.userId, id).delete()
                store.outboxMeta(s.userId, id).delete()
                files.setStatus(s.userId, id, FileStatus.FAILED, error = "Şifrələmə alınmadı: ${e.message}")
            }
        }
        uploads.schedule()
    }

    private suspend fun encryptToOutbox(userId: Long, id: String, picked: PickedFile, now: Long, folderId: String?) {
        val dek = session.requireKey()
        try {
            val thumb = Media.thumbnail(context, picked)
            var lastUpdate = 0L
            val result = context.contentResolver.openInputStream(picked.uri)!!.use { input ->
                BufferedOutputStream(store.outboxData(userId, id).outputStream(), 256 * 1024).use { out ->
                    StreamingCipher.encrypt(BufferedInputStream(input, 256 * 1024), out, dek, id) { done ->
                        val t = System.nanoTime()
                        if (picked.size > 0 && t - lastUpdate > 200_000_000) {
                            lastUpdate = t
                            files.setStatusBlocking(userId, id, FileStatus.ENCRYPTING, done.toFloat() / picked.size)
                        }
                    }
                }
            }
            val manifest = ItemManifest(
                id = id, name = picked.name, mimeType = picked.mimeType, size = result.plaintextBytes,
                sha256 = result.sha256, createdAt = now, width = thumb?.width ?: 0, height = thumb?.height ?: 0,
                thumbnail = thumb?.jpeg?.let { Base64.getEncoder().encodeToString(it) },
            )
            store.outboxMeta(userId, id).writeBytes(manifest.seal(dek))
            if (thumb != null) store.writeThumb(userId, id, thumb.jpeg, dek)
            files.upsert(
                FileEntity(id = id, userId = userId, name = picked.name, mimeType = picked.mimeType,
                    size = result.plaintextBytes, createdAt = now, width = manifest.width, height = manifest.height,
                    hasThumb = thumb != null, status = FileStatus.PENDING, folderId = folderId),
            )
        } finally {
            dek.wipe()
        }
    }

    /** Proses gözlənilmədən dayanıbsa (şifrələmə ortasında), yarımçıq qalanları təmizləyir. */
    suspend fun recoverInterrupted() = withContext(Dispatchers.IO) {
        for (f in files.interrupted()) {
            store.outboxData(f.userId, f.id).delete()
            store.outboxMeta(f.userId, f.id).delete()
            files.setStatus(f.userId, f.id, FileStatus.FAILED, error = "${LocalVaultStore.PERMANENT} Şifrələmə yarımçıq qaldı — faylı yenidən seçin")
        }
        store.cleanupPartials()
    }
}
