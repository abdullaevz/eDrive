package com.edrive.app.data.vault

import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.edrive.app.data.db.dao.FileDao
import com.edrive.app.data.db.entity.FileEntity
import com.edrive.app.data.db.entity.FileStatus
import com.edrive.crypto.AesGcm
import com.edrive.crypto.CryptoConstants
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Telefondakı lokal vault fayllarının yerləşməsi və idarəsi. Burada yalnız ŞİFRƏLİ məlumat saxlanılır:
 * ```
 *  files/vault/<userId>/thumbs/<id>        şifrəli miniatür
 *  files/vault/<userId>/outbox/<id>.edrv   yüklənməyi gözləyən şifrəli fayl
 *  files/vault/<userId>/outbox/<id>.meta   yüklənməyi gözləyən şifrəli manifest
 *  cache/blobs/<id>.edrv                   Drive-dan endirilmiş şifrəli nüsxə (keş)
 *  cache/open/                             kənar tətbiqdə açmaq üçün müvəqqəti deşifrə (kilid zamanı silinir)
 * ```
 */
@Singleton
class LocalVaultStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val files: FileDao,
    private val thumbCache: ThumbnailCache,
) {
    fun outbox(userId: Long) = File(userDir(userId), "outbox").apply { mkdirs() }
    fun outboxData(userId: Long, id: String) = File(outbox(userId), "$id.edrv")
    fun outboxMeta(userId: Long, id: String) = File(outbox(userId), "$id.meta")
    fun cachedBlob(id: String) = File(blobCache(), "$id.edrv")
    fun partialBlob(id: String) = File(blobCache(), "$id.part")
    fun openDir() = File(context.cacheDir, "open")

    fun writeThumb(userId: Long, id: String, jpeg: ByteArray, dek: ByteArray) {
        File(thumbs(userId), id).writeBytes(AesGcm.seal(dek, jpeg, CryptoConstants.AAD_THUMB + id))
    }

    /** @return deşifrə olunmuş JPEG və ya null (yoxdursa) */
    fun readThumb(userId: Long, id: String, dek: ByteArray): ByteArray? {
        val f = File(thumbs(userId), id)
        if (!f.exists()) return null
        return AesGcm.open(dek, f.readBytes(), CryptoConstants.AAD_THUMB + id)
    }

    /** Faylın bütün lokal izlərini (miniatür, outbox, keş, indeks) silir. Drive-a toxunmur. */
    suspend fun removeLocal(f: FileEntity) {
        File(thumbs(f.userId), f.id).delete()
        outboxData(f.userId, f.id).delete()
        outboxMeta(f.userId, f.id).delete()
        cachedBlob(f.id).delete()
        thumbCache.remove(f.id)
        files.delete(f.userId, f.id)
    }

    /**
     * Drive-dan endirilmiş şifrəli nüsxələrin keşi [maxBytes]-dan böyükdürsə, ən köhnələrini silir
     * (lazım olanda yenidən endirilir). [keepId] faylına toxunulmur. Outbox-a toxunulmur.
     */
    fun trimBlobCache(keepId: String, maxBytes: Long = BLOB_CACHE_LIMIT) {
        val blobs = blobCache().listFiles { file -> file.name.endsWith(".edrv") } ?: return
        var total = blobs.sumOf { it.length() }
        if (total <= maxBytes) return
        for (blob in blobs.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            if (blob.name == "$keepId.edrv") continue
            val size = blob.length()
            if (blob.delete()) total -= size
        }
    }

    /** Yarımçıq qalmış müvəqqəti faylları silir (proses gözlənilmədən dayananda). */
    fun cleanupPartials() {
        File(context.filesDir, "vault").listFiles()?.forEach { userDir ->
            File(userDir, "outbox").listFiles { file -> file.name.endsWith(".part") }?.forEach { it.delete() }
        }
        blobCache().listFiles { file -> file.name.endsWith(".part") }?.forEach { it.delete() }
    }

    private fun userDir(userId: Long) = File(context.filesDir, "vault/$userId")
    private fun thumbs(userId: Long) = File(userDir(userId), "thumbs").apply { mkdirs() }
    private fun blobCache() = File(context.cacheDir, "blobs").apply { mkdirs() }

    companion object {
        /** Bu prefikslə başlayan xəta daimi sayılır — avtomatik təkrar cəhd edilmir. */
        const val PERMANENT = "⛔"

        /** Endirilmiş şifrəli nüsxələr üçün keş limiti (2 GB). */
        const val BLOB_CACHE_LIMIT = 2L * 1024 * 1024 * 1024
    }
}

/** Progress callback-lər sinxron koddan (şifrələmə/yükləmə döngüsündən) çağırılır. */
internal fun FileDao.setStatusBlocking(userId: Long, id: String, status: FileStatus, progress: Float) =
    runBlocking { setStatus(userId, id, status, progress) }
