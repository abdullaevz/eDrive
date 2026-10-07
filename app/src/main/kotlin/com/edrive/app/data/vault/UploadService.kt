package com.edrive.app.data.vault

import javax.inject.Inject
import javax.inject.Singleton
import com.edrive.app.data.db.dao.FileDao
import com.edrive.app.data.db.dao.UserDao
import com.edrive.app.data.db.entity.FileStatus
import com.edrive.app.drive.DriveClient
import com.edrive.app.drive.DriveClientProvider
import com.edrive.app.drive.DriveConsentRequired
import com.edrive.app.drive.DriveException
import com.edrive.app.drive.DriveLayout
import com.edrive.app.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Yükləmə növbəsini Drive-a ötürür. DEK TƏLƏB ETMİR — outbox-dakı fayllar artıq şifrəlidir,
 * ona görə vault kilidli olsa belə (və ya tətbiq bağlı olsa belə, WorkManager vasitəsilə) işləyir.
 */
@Singleton
class UploadService @Inject constructor(
    private val users: UserDao,
    private val files: FileDao,
    private val store: LocalVaultStore,
    private val drives: DriveClientProvider,
    private val scheduler: UploadScheduler,
) {
    /** @return true — hamısı bitdi; false — bəziləri təkrar cəhd tələb edir */
    suspend fun processQueue(): Boolean = withContext(Dispatchers.IO) {
        var allDone = true
        for (f in files.uploadQueue()) {
            if (f.status == FileStatus.FAILED && f.error?.startsWith(LocalVaultStore.PERMANENT) == true) continue
            val user = users.byId(f.userId) ?: continue
            val folder = f.folderId ?: user.driveRootFolderId
            if (folder == null || user.driveEmail == null) {
                files.setStatus(f.userId, f.id, FileStatus.PENDING, error = "Google Drive qoşulmayıb")
                continue
            }
            val dataFile = store.outboxData(f.userId, f.id)
            val metaFile = store.outboxMeta(f.userId, f.id)
            if (!dataFile.exists() || !metaFile.exists()) {
                files.setStatus(f.userId, f.id, FileStatus.FAILED, error = "${LocalVaultStore.PERMANENT} Lokal şifrəli nüsxə tapılmadı")
                continue
            }
            try {
                val api = drives.forAccount(user.driveEmail)
                files.setStatus(f.userId, f.id, FileStatus.UPLOADING, 0f)
                val data = api.uploadLarge(DriveLayout.dataName(f.id), folder, dataFile) { p ->
                    files.setStatusBlocking(f.userId, f.id, FileStatus.UPLOADING, p)
                }
                val meta = api.uploadSmall(DriveLayout.metaName(f.id), folder, metaFile.readBytes(), DriveClient.OCTET)
                files.markSynced(f.userId, f.id, data.id, meta.id)
                AppLog.i("upload", "Fayl yükləndi (${f.size} bayt)")
                // Şifrəli nüsxəni keşdə saxlayırıq → yenidən endirmədən dərhal açılır
                dataFile.renameTo(store.cachedBlob(f.id)) || dataFile.delete()
                metaFile.delete()
            } catch (e: DriveConsentRequired) {
                AppLog.w("upload", "Drive icazəsi tələb olunur")
                files.setStatus(f.userId, f.id, FileStatus.FAILED, error = "Google Drive icazəsini yeniləyin (hesab menyusu → Qoşul)")
                allDone = false
            } catch (e: DriveException) {
                AppLog.e("upload", "Drive yükləmə xətası (HTTP ${e.code})", e)
                val retryable = e.code == 429 || e.code >= 500
                files.setStatus(f.userId, f.id, if (retryable) FileStatus.PENDING else FileStatus.FAILED, error = e.message)
                if (retryable) allDone = false
            } catch (e: IOException) {
                AppLog.w("upload", "Şəbəkə xətası, təkrar cəhd ediləcək", e)
                files.setStatus(f.userId, f.id, FileStatus.PENDING, error = "Şəbəkə xətası — yenidən cəhd ediləcək")
                allDone = false
            }
        }
        allDone
    }

    suspend fun retry(userId: Long, id: String) {
        files.setStatus(userId, id, FileStatus.PENDING)
        scheduler.schedule()
    }
}
