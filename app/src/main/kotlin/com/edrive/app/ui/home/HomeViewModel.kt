package com.edrive.app.ui.home

import android.app.PendingIntent
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import com.edrive.app.data.AccountRepository
import com.edrive.app.data.Session
import com.edrive.app.data.db.dao.UserDao
import com.edrive.app.data.vault.FileAccessService
import com.edrive.app.data.vault.ImportService
import com.edrive.app.data.vault.SyncService
import com.edrive.app.data.vault.UploadScheduler
import com.edrive.app.data.vault.UploadService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.edrive.app.drive.DriveConsentRequired
import com.edrive.app.data.db.entity.FileEntity
import com.edrive.app.data.db.entity.UserEntity
import com.edrive.app.ui.userMessage
import com.edrive.app.util.AppLog
import com.edrive.app.data.VaultLockedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface HomeEvent {
    data class Message(val text: String) : HomeEvent
    data class LaunchConsent(val pendingIntent: PendingIntent) : HomeEvent
}

data class HomeUi(
    /** Toplu silmə/endirmənin gedişi (null = gedən əməliyyat yoxdur). */
    val batch: BatchProgress? = null,
    val syncing: Boolean = false,
    val importing: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    accounts: AccountRepository,
    private val fileAccess: FileAccessService,
    private val importer: ImportService,
    private val session: Session,
    private val sync: SyncService,
    private val uploadScheduler: UploadScheduler,
    private val uploads: UploadService,
    private val userDao: UserDao,
) : ViewModel() {
    private val unlocked = checkNotNull(session.state.value) { "Home yalnız açıq sessiyada yaradılır" }
    val userId: Long = unlocked.userId
    val username: String = unlocked.username

    fun beginExternalUi() = session.beginExternalUi()
    fun endExternalUi() = session.endExternalUi()


    val user: StateFlow<UserEntity?> = accounts.observeUser(userId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val files: StateFlow<List<FileEntity>?> = fileAccess.observeFiles(userId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _ui = MutableStateFlow(HomeUi())
    val ui: StateFlow<HomeUi> = _ui.asStateFlow()

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _selection = MutableStateFlow(SelectionState())
    val selection: StateFlow<SelectionState> = _selection.asStateFlow()
    private var batchJob: Job? = null

    fun startSelect() = _selection.update { it.copy(active = true) }
    fun toggleSelect(id: String) = _selection.update { s ->
        SelectionState(true, if (id in s.ids) s.ids - id else s.ids + id)
    }
    fun selectAll(ids: List<String>) = _selection.update { SelectionState(true, toggleAll(it.ids, ids)) }
    fun clearSelection() { _selection.value = SelectionState() }

    private fun selectedFiles(): List<FileEntity> = files.value.orEmpty().filter { it.id in _selection.value.ids }

    /**
     * Seçilmiş faylları YALNIZ Google Drive-dan silir; şifrəli nüsxə cihazda qalır (status LOCAL).
     * Drive-da olmayan fayllar (yüklənir, xətalı, artıq yalnız cihazda) keçilir.
     */
    fun deleteSelected() {
        if (batchJob?.isActive == true) return
        val chosen = selectedFiles()
        val targets = chosen.driveRemovable(chosen.map { it.id }.toSet())
        val skipped = chosen.size - targets.size
        if (targets.isEmpty()) {
            viewModelScope.launch { message("Seçilmiş fayllar Drive-da deyil — silinəcək bir şey yoxdur") }
            return
        }
        batchJob = viewModelScope.launch {
            session.beginExternalUi() // əməliyyat ortasında avtomatik kilid açarı sıfırlamasın
            var done = 0
            var failed = 0
            try {
                _ui.update { it.copy(batch = BatchProgress("Drive-dan silinir", 0, targets.size)) }
                for (f in targets) {
                    try {
                        fileAccess.removeFromDriveKeepLocal(f.id) { p ->
                            _ui.update { u -> u.copy(batch = BatchProgress("Drive-dan silinir", done, targets.size, p)) }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: VaultLockedException) {
                        throw e
                    } catch (e: Exception) {
                        failed++
                        AppLog.e("home", "Toplu silmə: fayl Drive-dan silinmədi", e)
                    }
                    done++
                    _ui.update { it.copy(batch = BatchProgress("Drive-dan silinir", done, targets.size)) }
                }
                message(batchSummary("Drive-dan silindi (cihazda qaldı)", done - failed, failed, skipped))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message(friendly(e))
            } finally {
                session.endExternalUi()
                _ui.update { it.copy(batch = null) }
                clearSelection()
            }
        }
    }

    /**
     * Seçilmiş faylları deşifrə edib cihaza yazır: şəkil/video → qalereya (Android 10+), qalanı → [folder].
     * [folder] verilməyibsə, qovluq tələb edən fayllar keçilir.
     */
    fun downloadSelected(folder: Uri?) {
        if (batchJob?.isActive == true) return
        val chosen = selectedFiles()
        val targets = chosen.exportable(chosen.map { it.id }.toSet())
        val skippedNotReady = chosen.size - targets.size
        if (targets.isEmpty()) {
            viewModelScope.launch { message("Seçilmiş fayllar hələ hazır deyil") }
            return
        }
        batchJob = viewModelScope.launch {
            session.beginExternalUi()
            var done = 0
            var failed = 0
            var noFolder = 0
            try {
                _ui.update { it.copy(batch = BatchProgress("Endirilir", 0, targets.size)) }
                for (f in targets) {
                    try {
                        val progress: (Float) -> Unit = { p ->
                            _ui.update { u -> u.copy(batch = BatchProgress("Endirilir", done, targets.size, p)) }
                        }
                        if (f.isMedia() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) fileAccess.exportToGallery(f.id, progress)
                        else if (folder != null) fileAccess.exportToFolder(f.id, folder, progress)
                        else noFolder++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: VaultLockedException) {
                        throw e
                    } catch (e: Exception) {
                        failed++
                        AppLog.e("home", "Toplu endirmə: fayl endirilmədi", e)
                    }
                    done++
                    _ui.update { it.copy(batch = BatchProgress("Endirilir", done, targets.size)) }
                }
                message(batchSummary("cihaza endirildi", done - failed - noFolder, failed, skippedNotReady + noFolder))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message(friendly(e))
            } finally {
                session.endExternalUi()
                _ui.update { it.copy(batch = null) }
                clearSelection()
            }
        }
    }

    private fun batchSummary(verb: String, ok: Int, failed: Int, skipped: Int): String = buildString {
        append("$ok fayl $verb")
        if (skipped > 0) append(" · $skipped keçildi")
        if (failed > 0) append(" · $failed alınmadı")
    }

    init {
        // Açılışda Drive ilə sinxronlaşma (başqa cihazdan əlavə/silinən fayllar) və yarımçıq yükləmələr
        // Silinmiş/yoxa çıxmış fayllar seçimdə qalmasın
        viewModelScope.launch {
            files.filterNotNull().collect { list ->
                val present = list.mapTo(HashSet()) { it.id }
                _selection.update { s -> if (s.ids.all { it in present }) s else s.copy(ids = s.ids.filterTo(HashSet()) { it in present }) }
            }
        }
        viewModelScope.launch {
            if (userDao.byId(userId)?.isDriveReady == true) {
                sync(quiet = true)
                uploadScheduler.schedule()
            }
        }
    }

    suspend fun thumbnail(id: String): ImageBitmap? = fileAccess.thumbnail(userId, id)

    fun sync(quiet: Boolean = false) {
        viewModelScope.launch {
            _ui.update { it.copy(syncing = true) }
            try {
                val n = sync.sync()
                if (!quiet) message(if (n > 0) "$n yeni fayl tapıldı" else "Hər şey sinxrondur")
            } catch (e: DriveConsentRequired) {
                if (!quiet) _events.send(HomeEvent.LaunchConsent(e.pendingIntent))
            } catch (e: Exception) {
                if (!quiet) message(friendly(e))
            } finally {
                _ui.update { it.copy(syncing = false) }
            }
        }
    }

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _ui.update { it.copy(importing = true) }
            try {
                importer.import(uris)
                message("${uris.size} fayl şifrələndi · Drive-a yüklənir")
            } catch (e: Exception) {
                message(friendly(e))
            } finally {
                _ui.update { it.copy(importing = false) }
            }
        }
    }

    fun retry(id: String) = viewModelScope.launch { uploads.retry(userId, id) }

    fun lock() = session.lock()

    private suspend fun message(text: String) = _events.send(HomeEvent.Message(text))

    private fun friendly(e: Exception): String {
        AppLog.e("home", "Əməliyyat alınmadı", e)
        return e.userMessage()
    }
}
