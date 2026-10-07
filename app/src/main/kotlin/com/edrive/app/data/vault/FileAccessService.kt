package com.edrive.app.data.vault

import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import com.edrive.app.data.Session
import com.edrive.app.data.db.dao.FileDao
import com.edrive.app.data.db.dao.UserDao
import com.edrive.app.data.db.entity.FileEntity
import com.edrive.app.data.db.entity.FileStatus
import com.edrive.app.drive.DriveClientProvider
import com.edrive.crypto.CryptoException
import com.edrive.crypto.FileCiphertextSource
import com.edrive.crypto.RandomAccessDecryptor
import com.edrive.crypto.StreamingCipher
import com.edrive.crypto.wipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Artıq vault-da olan fayllarla iş: siyahı, miniatür, baxış (yaddaşda deşifrə),
 * kənar tətbiqdə açma, cihaza endirmə və silmə.
 */
@Singleton
class FileAccessService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: Session,
    private val users: UserDao,
    private val files: FileDao,
    private val store: LocalVaultStore,
    private val drives: DriveClientProvider,
    private val thumbCache: ThumbnailCache,
) {
    init {
        session.addLockListener {
            thumbCache.clear()
            store.openDir().deleteRecursively()
        }
        store.openDir().deleteRecursively()
    }

    fun observeFiles(userId: Long): Flow<List<FileEntity>> = files.observe(userId)
    fun observeFile(id: String): Flow<FileEntity?> = files.observeOne(id)

    suspend fun thumbnail(userId: Long, id: String): ImageBitmap? = withContext(Dispatchers.IO) {
        thumbCache.get(id)?.let { return@withContext it }
        val dek = session.current?.dek?.copyOf() ?: return@withContext null
        try {
            val jpeg = store.readThumb(userId, id, dek) ?: return@withContext null
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.asImageBitmap()?.also { thumbCache.put(id, it) }
        } catch (e: Exception) {
            null
        } finally {
            dek.wipe()
        }
    }

    /** Faylı deşifrə edib yaddaşda (RAM) qaytarır — diskə açıq mətn yazılmır. */
    suspend fun decryptToMemory(id: String, onProgress: (Float) -> Unit = {}): ByteArray = withContext(Dispatchers.IO) {
        val f = files.get(id) ?: error("Fayl tapılmadı")
        val out = ByteArrayOutputStream(f.size.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
        decryptInto(f, out, onProgress)
        out.toByteArray()
    }

    /**
     * Video, PDF və s. üçün: faylı müvəqqəti olaraq deşifrə edib başqa tətbiqdə açmağa imkan verir.
     * Bu, açıq mətnin tətbiq daxilində diskə düşdüyü YEGANƏ haldır — keş qovluğunda, vault kilidlənəndə silinir.
     */
    suspend fun openExternally(id: String, onProgress: (Float) -> Unit = {}): Uri = withContext(Dispatchers.IO) {
        val f = files.get(id) ?: error("Fayl tapılmadı")
        val dir = File(store.openDir(), id).apply { mkdirs() }
        val target = File(dir, f.name.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
        try {
            target.outputStream().use { decryptInto(f, it, onProgress) }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        FileProvider.getUriForFile(context, "${context.packageName}.files", target)
    }

    /**
     * "Endir": faylın deşifrə olunmuş nüsxəsini istifadəçinin seçdiyi yerə (Storage Access Framework) yazır.
     * DİQQƏT: nəticə şifrəsiz fayldır və artıq vault-un qorunması altında deyil.
     */
    suspend fun exportTo(id: String, target: Uri, onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        val f = files.get(id) ?: error("Fayl tapılmadı")
        val out = context.contentResolver.openOutputStream(target, "wt") ?: error("Faylı yazmaq mümkün olmadı")
        try {
            out.use { decryptInto(f, it, onProgress) }
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, target) }
            throw e
        }
    }

    /** Deşifrə olunmuş faylı istifadəçinin seçdiyi qovluğa (SAF ağacı) yazır; ad toqquşsa sistem özü nömrələyir. */
    suspend fun exportToFolder(id: String, tree: Uri, onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        val f = files.get(id) ?: error("Fayl tapılmadı")
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val target = DocumentsContract.createDocument(context.contentResolver, parent, f.mimeType, f.name)
            ?: error("Qovluqda fayl yaradılmadı")
        exportTo(id, target, onProgress)
    }

    /**
     * Şəkil və videoları birbaşa qalereyaya (Pictures/eDrive, Movies/eDrive) yazır. Android 10+ — icazə tələb etmir.
     * @return qalereyadakı qovluq adı (istifadəçiyə göstərmək üçün)
     */
    suspend fun exportToGallery(id: String, onProgress: (Float) -> Unit = {}): String = withContext(Dispatchers.IO) {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { "Qalereyaya birbaşa yazmaq Android 10+ tələb edir" }
        val f = files.get(id) ?: error("Fayl tapılmadı")
        val video = f.mimeType.startsWith("video/")
        val folder = if (video) "${Environment.DIRECTORY_MOVIES}/eDrive" else "${Environment.DIRECTORY_PICTURES}/eDrive"
        val collection = if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
            put(MediaStore.MediaColumns.MIME_TYPE, f.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1) // yazılış bitənə qədər digər tətbiqlər görməsin
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: error("Qalereyada fayl yaradılmadı")
        try {
            resolver.openOutputStream(uri)!!.use { decryptInto(f, it, onProgress) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        folder
    }

    /**
     * Video üçün: şifrəli nüsxəni hazırlayıb ixtiyari mövqedən deşifrə edən oxuyucu qaytarır.
     * Diskə açıq mətn yazılmır, yaddaşda yalnız bir neçə chunk (64 KiB) saxlanılır.
     * Çağıran tərəf [RandomAccessDecryptor.close] etməlidir.
     */
    suspend fun openRandomAccess(id: String, onProgress: (Float) -> Unit = {}): RandomAccessDecryptor = withContext(Dispatchers.IO) {
        val f = files.get(id) ?: error("Fayl tapılmadı")
        val file = ciphertextFile(f, onProgress)
        val dek = session.requireKey()
        val source = FileCiphertextSource(file)
        try {
            val d = RandomAccessDecryptor.open(source, dek, f.id)
            try {
                if (d.plaintextLength != f.size) throw IOException("Şifrəli nüsxə gözlənilən ölçüdə deyil")
                currentCoroutineContext().ensureActive()
            } catch (e: Throwable) {
                d.close()
                throw e
            }
            d
        } catch (e: Throwable) {
            source.close()
            throw e
        } finally {
            dek.wipe()
        }
    }

    /**
     * Faylı yalnız Google Drive-dan silir, şifrəli nüsxəni telefonda saxlayır (status [FileStatus.LOCAL]).
     * Ardıcıllıq təhlükəsizlik üçündür: əvvəl şifrəli nüsxə daimi yerə (outbox, keş deyil) yazılır,
     * sonra status LOCAL olur (sinxronizasiya silməsin), yalnız bundan sonra Drive-dan silinir.
     * İlk Drive sorğusu alınmazsa (məs. şəbəkə yoxdur) status geri qaytarılır.
     */
    suspend fun removeFromDriveKeepLocal(id: String, onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        val f = files.get(id) ?: return@withContext
        if (f.status != FileStatus.SYNCED) return@withContext
        val email = users.byId(f.userId)?.driveEmail ?: throw IllegalStateException("Google Drive-a qoşulun")
        val src = ciphertextFile(f, onProgress) // lazım olsa Drive-dan endirilir
        val keep = store.outboxData(f.userId, f.id)
        if (src.absolutePath != keep.absolutePath) {
            val tmp = File(keep.path + ".part")
            src.copyTo(tmp, overwrite = true)
            if (!tmp.renameTo(keep)) { tmp.copyTo(keep, overwrite = true); tmp.delete() }
        }
        val chunk = keep.inputStream().use { StreamingCipher.readHeader(it).chunkSize }
        check(keep.length() == StreamingCipher.ciphertextSize(f.size, chunk)) { "Şifrəli nüsxə tam deyil — Drive-dan silinmədi" }
        files.setStatus(f.id, FileStatus.LOCAL, 1f)
        val api = drives.forAccount(email)
        try {
            f.driveMetaId?.let { api.delete(it) }
        } catch (e: Exception) {
            files.setStatus(f.id, FileStatus.SYNCED, 1f) // heç nə silinməyib — əvvəlki vəziyyət
            throw e
        }
        f.driveDataId?.let { api.delete(it) } // alınmasa: status LOCAL qalır, Drive-da yetim şifrəli data ola bilər
        files.clearDriveIds(f.id)
        store.cachedBlob(f.id).delete() // artıq outbox-dakı nüsxə var — təkrar saxlamırıq
    }

    /** Faylı həm Drive-dan, həm telefondan silir. */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val f = files.get(id) ?: return@withContext
        val email = users.byId(f.userId)?.driveEmail
        if (email != null && (f.driveDataId != null || f.driveMetaId != null)) {
            val api = drives.forAccount(email)
            f.driveMetaId?.let { api.delete(it) }
            f.driveDataId?.let { api.delete(it) }
        }
        store.removeLocal(f)
    }

    // ------------------------------------------------------------------ daxili

    /** Deşifrəni birbaşa verilən axına yazır (fayl yaddaşa tam yüklənmir). */
    private suspend fun decryptInto(f: FileEntity, out: OutputStream, onProgress: (Float) -> Unit) {
        val src = ciphertextFile(f, onProgress)
        val dek = session.requireKey()
        try {
            BufferedInputStream(src.inputStream(), 256 * 1024).use { input ->
                val buffered = BufferedOutputStream(out, 256 * 1024)
                StreamingCipher.decrypt(input, buffered, dek, f.id)
                buffered.flush()
            }
        } finally {
            dek.wipe()
        }
    }

    private val downloadLocks = ConcurrentHashMap<String, Mutex>()

    /** Lokal şifrəli nüsxə: əvvəl outbox, sonra endirmə keşi (keşə toxunuş vaxtı yenilənir ki, ən köhnələr silinsin). */
    private fun localCiphertext(f: FileEntity): File? {
        store.outboxData(f.userId, f.id).takeIf { it.exists() }?.let { return it }
        return store.cachedBlob(f.id).takeIf { it.exists() }?.also { it.setLastModified(System.currentTimeMillis()) }
    }

    /** Şifrəli faylı lokal mənbədən (outbox / keş) və ya Drive-dan endirərək tapır. */
    private suspend fun ciphertextFile(f: FileEntity, onProgress: (Float) -> Unit): File {
        localCiphertext(f)?.let { return it }
        return downloadLocks.getOrPut(f.id) { Mutex() }.withLock {
            // Gözləyərkən başqası endirməni bitirmiş ola bilər
            localCiphertext(f)?.let { return@withLock it }
            val email = users.byId(f.userId)?.driveEmail ?: throw IllegalStateException("Faylı açmaq üçün Google Drive-a qoşulun")
            val dataId = f.driveDataId ?: throw IllegalStateException("Fayl hələ Drive-a yüklənməyib")
            val tmp = store.partialBlob(f.id)
            val total = StreamingCipher.ciphertextSize(f.size).coerceAtLeast(1)
            drives.forAccount(email).download(dataId).use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(256 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress(done.toFloat() / total)
                    }
                }
            }
            requireComplete(tmp, f)
            val cached = store.cachedBlob(f.id)
            if (!tmp.renameTo(cached)) {
                tmp.copyTo(cached, overwrite = true)
                tmp.delete()
            }
            store.trimBlobCache(keepId = f.id)
            cached
        }
    }

    /**
     * Yarımçıq (kəsilmiş) endirmə keşə düşməsin: başlıqdakı chunk ölçüsünə görə gözlənilən ölçüdən qısadırsa rədd edilir.
     * eDrive faylı kimi tanınmırsa toxunulmur — deşifrə mərhələsi özü xəta verəcək.
     */
    private fun requireComplete(file: File, f: FileEntity) {
        val expected = try {
            file.inputStream().use { StreamingCipher.ciphertextSize(f.size.coerceAtLeast(0), StreamingCipher.readHeader(it).chunkSize) }
        } catch (e: CryptoException) {
            return
        }
        if (file.length() < expected) {
            file.delete()
            throw IOException("Endirmə yarımçıq qaldı — yenidən cəhd edin")
        }
    }
}
