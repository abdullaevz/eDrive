package com.edrive.app.data.vault

import com.edrive.app.data.AccountException
import com.edrive.app.data.Session
import com.edrive.app.data.db.dao.FileDao
import com.edrive.app.data.db.dao.FolderDao
import com.edrive.app.data.db.dao.UserDao
import com.edrive.app.data.db.entity.FileEntity
import com.edrive.app.data.db.entity.FileStatus
import com.edrive.app.data.db.entity.FolderEntity
import com.edrive.app.drive.DriveClient
import com.edrive.app.drive.DriveClientProvider
import com.edrive.app.drive.DriveLayout
import com.edrive.app.util.AppLog
import com.edrive.app.util.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "eDrive Storage" daxilində real Drive alt qovluqları: yaratma, adını dəyişmə, silmə, faylları köçürmə.
 * Qovluq adları Drive-da açıq mətndir (fayl adları və məzmun isə şifrəli qalır). Bütün əməliyyatlar internet tələb edir.
 */
@Singleton
class FolderService @Inject constructor(
    private val session: Session,
    private val users: UserDao,
    private val folders: FolderDao,
    private val files: FileDao,
    private val store: LocalVaultStore,
    private val drives: DriveClientProvider,
    private val clock: Clock,
) {
    /** Dolu qovluq silinəndə içindəkilərlə nə edilsin. */
    enum class DeleteMode { EMPTY_ONLY, MOVE_UP, WITH_CONTENTS }

    /** Qovluğun içindəkilər (alt qovluqlar daxil). [unsynced] — hələ Drive-a çatmamış fayllar. */
    data class Contents(val files: Int, val folders: Int, val unsynced: Int) {
        val isEmpty: Boolean get() = files == 0 && folders == 0
    }

    fun observe(userId: Long): Flow<List<FolderEntity>> = folders.observe(userId)

    suspend fun create(name: String, parentId: String?): FolderEntity = withContext(Dispatchers.IO) {
        val userId = session.requireUser().userId
        val clean = validName(name)
        val all = folders.all(userId)
        if (parentId != null && depth(parentId, all) >= DriveLayout.MAX_DEPTH) {
            throw AccountException("Ən çox ${DriveLayout.MAX_DEPTH} səviyyə iç-içə qovluq yaratmaq olar")
        }
        requireUnique(clean, parentId, all, exceptId = null)
        val (api, root) = drive(userId)
        val created = api.createFolder(clean, parentId ?: root)
        FolderEntity(created.id, userId, clean, parentId, clock.now()).also { folders.upsert(it) }
    }

    suspend fun rename(id: String, name: String) = withContext(Dispatchers.IO) {
        val userId = session.requireUser().userId
        val folder = folders.get(userId, id) ?: throw AccountException("Qovluq tapılmadı")
        val clean = validName(name)
        requireUnique(clean, folder.parentId, folders.all(userId), exceptId = id)
        drive(userId).first.rename(id, clean)
        folders.upsert(folder.copy(name = clean))
    }

    suspend fun contents(id: String): Contents = contents(listOf(id))

    /** Bir neçə qovluğun ümumi içindəkiləri (iç-içə seçilənlər iki dəfə sayılmır). */
    suspend fun contents(ids: Collection<String>): Contents {
        val userId = session.requireUser().userId
        val all = folders.all(userId)
        val roots = topLevel(ids, all)
        val tree = roots.flatMapTo(HashSet()) { descendants(it, all) + it }
        val inside = files.all(userId).filter { it.folderId in tree }
        return Contents(inside.size, tree.size - roots.size, inside.count { it.status != FileStatus.SYNCED && it.status != FileStatus.LOCAL })
    }

    /** Bir neçə qovluğu silir; seçilmiş qovluğun içindəki seçilmiş alt qovluq ayrıca emal olunmur. */
    suspend fun delete(ids: Collection<String>, mode: DeleteMode) {
        val userId = session.requireUser().userId
        topLevel(ids, folders.all(userId)).forEach { delete(it, mode) }
    }

    /**
     * Qovluqları [targetId] qovluğuna (`null` = kök) köçürür — Drive-da qovluq bütün içindəkiləri ilə birlikdə köçür.
     * Qovluğu özünə və ya öz alt qovluğuna köçürmək, dərinlik limitini aşmaq və eyni adlı qovluq yaratmaq olmaz.
     * @return köçürülən qovluqların sayı
     */
    suspend fun moveFolders(ids: Collection<String>, targetId: String?): Int = withContext(Dispatchers.IO) {
        val userId = session.requireUser().userId
        if (ids.isEmpty()) return@withContext 0
        val (api, root) = drive(userId)
        var moved = 0
        for (id in topLevel(ids, folders.all(userId))) {
            val all = folders.all(userId)
            val folder = all.firstOrNull { it.id == id } ?: continue
            if (folder.parentId == targetId) continue
            if (targetId == id || targetId in descendants(id, all)) {
                throw AccountException("\"${folder.name}\" qovluğunu öz içinə köçürmək olmaz")
            }
            val targetDepth = targetId?.let { depth(it, all) } ?: 0
            if (targetDepth + height(id, all) > DriveLayout.MAX_DEPTH) {
                throw AccountException("Köçürmədən sonra ${DriveLayout.MAX_DEPTH} səviyyədən dərin qovluq yaranır")
            }
            requireUnique(folder.name, targetId, all, exceptId = id)
            api.move(id, folder.parentId ?: root, targetId ?: root)
            folders.upsert(folder.copy(parentId = targetId))
            moved++
        }
        moved
    }

    suspend fun delete(id: String, mode: DeleteMode) = withContext(Dispatchers.IO) {
        val userId = session.requireUser().userId
        val folder = folders.get(userId, id) ?: return@withContext
        val all = folders.all(userId)
        val tree = descendants(id, all) + id
        if (files.all(userId).any { it.folderId in tree && (it.status == FileStatus.UPLOADING || it.status == FileStatus.ENCRYPTING) }) {
            throw AccountException("Qovluqda hazırda yüklənən fayl var — bitməsini gözləyin")
        }
        val (api, root) = drive(userId)
        when (mode) {
            DeleteMode.EMPTY_ONLY -> if (!contents(id).isEmpty) throw AccountException("Qovluq boş deyil")
            DeleteMode.MOVE_UP -> {
                moveFiles(files.all(userId).filter { it.folderId == id }.map { it.id }, folder.parentId)
                for (child in all.filter { it.parentId == id }) {
                    api.move(child.id, id, folder.parentId ?: root)
                    folders.upsert(child.copy(parentId = folder.parentId))
                }
            }
            DeleteMode.WITH_CONTENTS -> {
                files.all(userId).filter { it.folderId in tree }.forEach { store.removeLocal(it) }
                (tree - id).forEach { folders.delete(userId, it) }
            }
        }
        api.trash(id) // Drive zibilinə — 30 gün ərzində Drive-da bərpa oluna bilir
        folders.delete(userId, id)
        AppLog.i("folders", "Qovluq silindi ($mode)")
    }

    /**
     * Faylları [targetId] qovluğuna (`null` = kök) köçürür. Drive-dakı fayllarda əvvəl `.edrv`, sonra `.meta` köçürülür
     * (`.meta`-nın yeri faylın yeridir; yarımçıq qalsa sinxronlaşma düzəldir). Hələ yüklənməmiş fayllar yalnız lokal
     * qeyd olunur — yükləmə yeni qovluğa gedir. Hazırda yüklənən fayllar keçilir.
     * @return köçürülən faylların sayı
     */
    suspend fun moveFiles(ids: Collection<String>, targetId: String?): Int = withContext(Dispatchers.IO) {
        val userId = session.requireUser().userId
        if (targetId != null) folders.get(userId, targetId) ?: throw AccountException("Qovluq tapılmadı")
        var moved = 0
        var drive: Pair<DriveClient, String>? = null
        for (id in ids) {
            val f = files.get(userId, id) ?: continue
            if (f.folderId == targetId || f.status == FileStatus.UPLOADING || f.status == FileStatus.ENCRYPTING) continue
            if (f.isOnDrive) {
                val (api, root) = drive ?: drive(userId).also { drive = it }
                val from = f.folderId ?: root
                val to = targetId ?: root
                api.move(f.driveDataId!!, from, to)
                api.move(f.driveMetaId!!, from, to)
            }
            files.setFolder(userId, id, targetId)
            moved++
        }
        moved
    }

    private val FileEntity.isOnDrive: Boolean get() = driveDataId != null && driveMetaId != null

    private suspend fun drive(userId: Long): Pair<DriveClient, String> {
        val user = users.byId(userId)?.takeIf { it.isDriveReady } ?: throw AccountException("Əvvəlcə Google Drive-a qoşulun")
        return drives.forAccount(user.driveEmail!!) to user.driveRootFolderId!!
    }

    private fun validName(name: String): String {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > MAX_NAME) throw AccountException("Qovluq adı 1–$MAX_NAME simvol olmalıdır")
        if (clean.any { it == '/' || it == '\\' || it.isISOControl() }) throw AccountException("Qovluq adında / və \\ işarələri olmamalıdır")
        return clean
    }

    private fun requireUnique(name: String, parentId: String?, all: List<FolderEntity>, exceptId: String?) {
        if (all.any { it.parentId == parentId && it.id != exceptId && it.name.equals(name, ignoreCase = true) }) {
            throw AccountException("Bu adda qovluq artıq var")
        }
    }

    companion object {
        const val MAX_NAME = 60

        /** Qovluğun dərinliyi: kökün birbaşa altındakı qovluq = 1. */
        fun depth(id: String, all: List<FolderEntity>): Int {
            val byId = all.associateBy { it.id }
            var d = 0
            var cur: String? = id
            while (cur != null && d <= DriveLayout.MAX_DEPTH) {
                d++
                cur = byId[cur]?.parentId
            }
            return d
        }

        /** Qovluğun özü də daxil olmaqla neçə səviyyə tutduğu (alt qovluğu yoxdursa 1). */
        fun height(id: String, all: List<FolderEntity>): Int {
            val children = all.groupBy { it.parentId }
            fun h(f: String): Int = 1 + (children[f].orEmpty().maxOfOrNull { h(it.id) } ?: 0)
            return h(id)
        }

        /** Seçilmişlərdən başqa seçilmiş qovluğun içində olmayanlar. */
        fun topLevel(ids: Collection<String>, all: List<FolderEntity>): List<String> {
            val selected = ids.toSet()
            return ids.distinct().filter { id -> selected.none { other -> other != id && id in descendants(other, all) } }
        }

        /** Bütün nəsillər (alt qovluqlar, alt-alt qovluqlar…), qovluğun özü xaric. */
        fun descendants(id: String, all: List<FolderEntity>): Set<String> {
            val children = all.groupBy { it.parentId }
            val out = LinkedHashSet<String>()
            val queue = ArrayDeque(listOf(id))
            while (queue.isNotEmpty()) {
                for (c in children[queue.removeFirst()].orEmpty()) if (out.add(c.id)) queue += c.id
            }
            return out
        }
    }
}
