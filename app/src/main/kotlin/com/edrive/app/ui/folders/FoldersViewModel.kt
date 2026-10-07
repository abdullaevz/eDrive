package com.edrive.app.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edrive.app.data.AccountException
import com.edrive.app.data.Session
import com.edrive.app.data.db.entity.FolderEntity
import com.edrive.app.data.vault.FolderService
import com.edrive.app.data.vault.FolderService.DeleteMode
import com.edrive.app.ui.userMessage
import com.edrive.app.util.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Açıq qovluq: yolu (kökdən) və birbaşa alt qovluqları. [current] `null` — "eDrive Storage" kökü. */
data class FolderLocation(
    val current: FolderEntity? = null,
    val path: List<FolderEntity> = emptyList(),
    val children: List<FolderEntity> = emptyList(),
    val all: List<FolderEntity> = emptyList(),
) {
    val currentId: String? get() = current?.id
}

/** Hansı qovluq dialoqu açıqdır. */
sealed interface FolderDialog {
    data object Create : FolderDialog
    data class Rename(val folder: FolderEntity) : FolderDialog
    data class Delete(val folders: List<FolderEntity>, val contents: FolderService.Contents) : FolderDialog
    data class Move(val fileIds: Set<String>, val folderIds: Set<String>) : FolderDialog {
        val count: Int get() = fileIds.size + folderIds.size
    }
}

data class FolderUi(val dialog: FolderDialog? = null, val busy: Boolean = false, val error: String? = null)

/** Qovluq naviqasiyası və əməliyyatları. Fayl siyahısı [com.edrive.app.ui.home.HomeViewModel]-də, burada yalnız qovluqlar. */
@HiltViewModel
class FoldersViewModel @Inject constructor(
    session: Session,
    private val service: FolderService,
) : ViewModel() {
    private val userId = checkNotNull(session.state.value) { "Yalnız açıq sessiyada yaradılır" }.userId

    private val currentId = MutableStateFlow<String?>(null)

    val location: StateFlow<FolderLocation> = combine(service.observe(userId), currentId) { all, id ->
        val byId = all.associateBy { it.id }
        val current = id?.let(byId::get)
        val path = generateSequence(current) { f -> f.parentId?.let(byId::get) }.toList().reversed()
        FolderLocation(current, path, all.filter { it.parentId == current?.id }, all)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FolderLocation())

    private val _ui = MutableStateFlow(FolderUi())
    val ui: StateFlow<FolderUi> = _ui.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun open(id: String?) { currentId.value = id }

    /** @return false — artıq kökdəyik */
    fun up(): Boolean {
        val cur = location.value.current ?: return false
        currentId.value = cur.parentId
        return true
    }

    fun showCreate() = show(FolderDialog.Create)
    fun showRename(folder: FolderEntity) = show(FolderDialog.Rename(folder))
    fun showMove(fileIds: Set<String>, folderIds: Set<String>) = show(FolderDialog.Move(fileIds, folderIds))
    fun dismiss() = _ui.update { FolderUi() }

    fun showDelete(folders: List<FolderEntity>) = viewModelScope.launch {
        if (folders.isNotEmpty()) show(FolderDialog.Delete(folders, service.contents(folders.map { it.id })))
    }

    fun create(name: String) = perform { service.create(name, currentId.value) }

    fun rename(name: String) = perform {
        val d = _ui.value.dialog as? FolderDialog.Rename ?: return@perform
        service.rename(d.folder.id, name)
    }

    /** [onDone] — silmə bitəndə (seçimi təmizləmək üçün). */
    fun delete(mode: DeleteMode, onDone: () -> Unit) = perform {
        val d = _ui.value.dialog as? FolderDialog.Delete ?: return@perform
        val all = location.value.all
        val gone = d.folders.flatMapTo(HashSet()) { FolderService.descendants(it.id, all) + it.id }
        service.delete(d.folders.map { it.id }, mode)
        if (currentId.value in gone) currentId.value = null // açıq qovluq silinib — kökə qayıdılır
        _messages.send(d.folders.singleOrNull()?.let { "\"${it.name}\" silindi" } ?: "${d.folders.size} qovluq silindi")
        onDone()
    }

    /** Seçilmiş qovluq və faylları köçürür. [onDone] — köçürmə bitəndə (seçimi təmizləmək üçün). */
    fun move(targetId: String?, onDone: () -> Unit) = perform {
        val d = _ui.value.dialog as? FolderDialog.Move ?: return@perform
        val folders = service.moveFolders(d.folderIds, targetId)
        val files = service.moveFiles(d.fileIds, targetId)
        val skipped = d.count - folders - files
        _messages.send(
            listOfNotNull(
                "$folders qovluq".takeIf { folders > 0 },
                "$files fayl".takeIf { files > 0 },
            ).joinToString(" və ").ifEmpty { "Heç nə" } + " köçürüldü" + if (skipped > 0) " · $skipped keçildi" else "",
        )
        onDone()
    }

    private fun show(d: FolderDialog) = _ui.update { FolderUi(dialog = d) }

    /** Dialoq əməliyyatı: uğurlu olsa dialoq bağlanır, xəta olsa dialoqda göstərilir. */
    private fun perform(block: suspend () -> Unit) {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                block()
                _ui.value = FolderUi()
            } catch (e: AccountException) {
                _ui.update { it.copy(busy = false, error = e.message) }
            } catch (e: Exception) {
                AppLog.e("folders", "Qovluq əməliyyatı alınmadı", e)
                _ui.update { it.copy(busy = false, error = e.userMessage()) }
            }
        }
    }
}
