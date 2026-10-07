package com.edrive.app.ui.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edrive.app.data.AccountException
import com.edrive.app.data.AccountRepository
import com.edrive.app.data.Session
import com.edrive.app.data.VaultService
import com.edrive.app.data.db.entity.UserEntity
import com.edrive.app.data.vault.DriveConnectionService
import com.edrive.app.data.vault.DriveConnectionService.ConnectOutcome
import com.edrive.app.data.vault.DriveConnectionService.PendingDrive
import com.edrive.app.data.vault.RemoteVaultMonitor
import com.edrive.app.data.vault.SyncService
import com.edrive.app.drive.DriveAuthorizer
import com.edrive.app.drive.DriveConsentRequired
import com.edrive.app.ui.userMessage
import com.edrive.app.security.BiometricGate
import com.edrive.app.util.AppLog
import com.edrive.app.util.pdf.RecoveryDocuments
import com.edrive.crypto.VaultHeader
import com.edrive.crypto.wipe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Təhlükəsizlik açarı soruşulan hallar. */
sealed interface VaultPrompt {
    /** Drive-da vault yoxdur — yeni açar təyin edilir. */
    data class CreateKey(val drive: PendingDrive) : VaultPrompt
    /** Drive-da vault var — onun açarı daxil edilir. */
    data class EnterKey(val drive: PendingDrive, val remote: VaultHeader) : VaultPrompt
    /** Cihaz açarı əlçatan deyil — lokal vault açarla yenidən açılır. */
    data object UnlockLocal : VaultPrompt
    /** Ayarlarda açarın dəyişdirilməsi. */
    data object ChangeKey : VaultPrompt
}

sealed interface DriveEvent {
    data class Message(val text: String) : DriveEvent
    data class LaunchConsent(val pendingIntent: PendingIntent) : DriveEvent
    /** Google hesab seçmə pəncərəsi açılmalıdır. */
    data object PickAccount : DriveEvent
}

data class DriveUi(
    val busy: Boolean = false,
    val prompt: VaultPrompt? = null,
    val promptError: String? = null,
    /** Təhlükəsizlik açarı sənədi saxlanmağı gözləyir (yeni vault və ya açar dəyişməsindən sonra). */
    val keyDocumentPending: Boolean = false,
    val keyDocumentSavedAs: String? = null,
    /** Ayrılma təsdiqi: hələ Drive-a yüklənməmiş faylların sayı. */
    val disconnectUnsynced: Int? = null,
    /** Qoşulmadan sonra faylların Drive-dan bərpası gedir (null — getmir). */
    val restore: SyncService.Progress? = null,
)

/**
 * Google Drive bağlantısı və vault: qoşulma, Təhlükəsizlik açarı (yaratma, daxil etmə, dəyişmə),
 * açar sənədi, ayrılma və barmaq izi ayarı. Fayl siyahısı ilə iş [com.edrive.app.ui.home.HomeViewModel]-dədir.
 */
@HiltViewModel
class DriveViewModel @Inject constructor(
    private val session: Session,
    private val accounts: AccountRepository,
    private val vaults: VaultService,
    private val connection: DriveConnectionService,
    private val monitor: RemoteVaultMonitor,
    private val driveAuth: DriveAuthorizer,
    private val biometric: BiometricGate,
) : ViewModel() {
    private val unlocked = checkNotNull(session.state.value) { "Yalnız açıq sessiyada yaradılır" }
    val userId: Long = unlocked.userId

    val user: StateFlow<UserEntity?> = accounts.observeUser(userId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _ui = MutableStateFlow(DriveUi())
    val ui: StateFlow<DriveUi> = _ui.asStateFlow()

    private val _events = Channel<DriveEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val biometricAvailable: Boolean get() = biometric.isAvailable()

    /** Sənəd üçün açar yalnız saxlanana qədər yaddaşda qalır. */
    private var keyDocument: KeyDocument? = null

    private class KeyDocument(val email: String, val header: VaultHeader, val key: CharArray)

    init {
        viewModelScope.launch {
            val u = accounts.observeUser(userId).first() ?: return@launch
            when {
                u.hasVault && session.current?.hasVault == false -> _ui.update { it.copy(prompt = VaultPrompt.UnlockLocal) }
                u.isDriveReady -> checkRemoteVault(u)
            }
        }
    }

    fun beginExternalUi() = session.beginExternalUi()
    fun endExternalUi() = session.endExternalUi()

    /** Hər qoşulmada hesab seçmə pəncərəsi açılır (Google əvvəlki hesabı avtomatik seçməsin deyə). */
    fun connect() {
        if (_ui.value.busy) return
        viewModelScope.launch {
            session.beginExternalUi()
            _events.send(DriveEvent.PickAccount)
        }
    }

    fun onAccountPicked(email: String?) {
        if (email.isNullOrBlank()) return
        op { handle(connection.startConnect(email)) }
    }

    fun onConsentResult(data: Intent?) = op {
        handle(connection.finishConnect(driveAuth.tokenFromConsentResult(data)))
    }

    /** Açar dialoqunun təsdiqi. [old] yalnız [VaultPrompt.ChangeKey] üçündür. */
    fun submitKey(key: String, confirm: String, old: String = "") {
        val prompt = _ui.value.prompt ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, promptError = null) }
            try {
                when (prompt) {
                    is VaultPrompt.CreateKey -> {
                        val header = connection.createVault(prompt.drive, key.toCharArray(), confirm.toCharArray())
                        _ui.update { it.copy(prompt = null) }
                        offerKeyDocument(prompt.drive.email, header, key)
                        restore(showProgress = false)
                        message("Vault yaradıldı və Google Drive qoşuldu")
                    }
                    is VaultPrompt.EnterKey -> {
                        connection.adoptVault(prompt.drive, prompt.remote, key.toCharArray())
                        _ui.update { it.copy(prompt = null) } // açar qəbul olundu — dialoq bağlanır, bərpa gedişi göstərilir
                        restore(showProgress = true)
                    }
                    VaultPrompt.UnlockLocal -> {
                        vaults.unlockWithKey(userId, key.toCharArray())
                        message("Vault açıldı")
                    }
                    VaultPrompt.ChangeKey -> {
                        val header = connection.changeKey(old.toCharArray(), key.toCharArray(), confirm.toCharArray())
                        offerKeyDocument(user.value?.driveEmail.orEmpty(), header, key)
                        message("Təhlükəsizlik açarı dəyişdirildi")
                    }
                }
                _ui.update { it.copy(prompt = null) }
            } catch (e: AccountException) {
                _ui.update { it.copy(promptError = e.message) }
            } catch (e: DriveConsentRequired) {
                session.beginExternalUi()
                _events.send(DriveEvent.LaunchConsent(e.pendingIntent))
            } catch (e: Exception) {
                _ui.update { it.copy(promptError = friendly(e)) }
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    /** Qoşulma yarımçıq dayandırılır — profilə heç nə yazılmayıb. */
    fun cancelPrompt() = _ui.update { it.copy(prompt = null, promptError = null) }

    fun startKeyChange() = _ui.update { it.copy(prompt = VaultPrompt.ChangeKey, promptError = null) }

    fun saveKeyDocument(context: Context, uri: Uri) {
        val doc = keyDocument ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)!!.use { RecoveryDocuments.writeSecurityKey(it, doc.email, doc.key, doc.header) }
                }
                _ui.update { it.copy(keyDocumentSavedAs = uri.lastPathSegment?.substringAfterLast('/') ?: "eDrive açar.pdf") }
            } catch (e: Exception) {
                message(friendly(e))
            }
        }
    }

    /** Sənəd pəncərəsi bağlanır, açar yaddaşdan silinir. */
    fun closeKeyDocument() {
        keyDocument?.key?.wipe()
        keyDocument = null
        _ui.update { it.copy(keyDocumentPending = false, keyDocumentSavedAs = null) }
    }

    /** Ayrılmadan əvvəl: neçə fayl hələ Drive-a çatmayıb (onlar itəcək). */
    fun requestDisconnect() = op {
        _ui.update { it.copy(disconnectUnsynced = connection.unsyncedCount()) }
    }

    fun cancelDisconnect() = _ui.update { it.copy(disconnectUnsynced = null) }

    fun disconnect() = op {
        _ui.update { it.copy(disconnectUnsynced = null) }
        connection.disconnect()
        message("Google Drive ayrıldı. Fayllar Drive-da şifrəli qalır.")
    }

    fun setBiometric(activity: FragmentActivity, enabled: Boolean) = viewModelScope.launch {
        try {
            accounts.setBiometric(activity, enabled)
        } catch (e: Exception) {
            message(friendly(e))
        }
    }

    private fun offerKeyDocument(email: String, header: VaultHeader, key: String) {
        keyDocument?.key?.wipe()
        keyDocument = KeyDocument(email, header, key.toCharArray())
        _ui.update { it.copy(keyDocumentPending = true, keyDocumentSavedAs = null) }
    }

    private suspend fun handle(r: ConnectOutcome) {
        when (r) {
            is ConnectOutcome.Connected -> restore(showProgress = true)
            is ConnectOutcome.NeedsNewKey -> _ui.update { it.copy(prompt = VaultPrompt.CreateKey(r.drive), promptError = null) }
            is ConnectOutcome.NeedsKey -> _ui.update { it.copy(prompt = VaultPrompt.EnterKey(r.drive, r.remote), promptError = null) }
        }
    }

    /**
     * Drive-dakı faylları gətirir. [showProgress] — ekranda bərpa kartı (yeni vault-da gətiriləcək fayl olmur).
     * Xəta dialoqda yox, mesaj kimi göstərilir, çünki açar dialoqu artıq bağlanıb.
     */
    private suspend fun restore(showProgress: Boolean) {
        if (showProgress) _ui.update { it.copy(restore = SyncService.Progress(0, null)) }
        try {
            val n = connection.restore { p -> if (showProgress) _ui.update { it.copy(restore = p) } }
            if (showProgress) message(if (n > 0) "Google Drive qoşuldu · $n fayl bərpa olundu" else "Google Drive qoşuldu")
        } catch (e: Exception) {
            message("Drive qoşuldu, amma fayllar gətirilmədi: ${friendly(e)}. Sinxron düyməsi ilə yenidən cəhd edin.")
        } finally {
            _ui.update { it.copy(restore = null) }
        }
    }

    /** Açılışda Drive-dakı vault-u yerli nüsxə ilə müqayisə edir (şəbəkə yoxdursa səssizcə keçir). */
    private suspend fun checkRemoteVault(u: UserEntity) {
        val status = try {
            monitor.check(u)
        } catch (e: Exception) {
            AppLog.w("drive", "Drive-dakı vault yoxlanılmadı", e)
            return
        }
        when (status) {
            RemoteVaultMonitor.Status.KEY_CHANGED -> message("Təhlükəsizlik açarı başqa cihazda dəyişdirilib")
            RemoteVaultMonitor.Status.DIFFERENT_VAULT ->
                message("Drive-dakı vault dəyişib (başqa açar) — təhlükəsizlik üçün yükləmə dayandırıldı")
            RemoteVaultMonitor.Status.RESTORED -> message("Drive-da vault.json yox idi — bu cihazdakı nüsxədən bərpa olundu")
            RemoteVaultMonitor.Status.SAME -> Unit
        }
    }

    private fun op(block: suspend () -> Unit) {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: DriveConsentRequired) {
                session.beginExternalUi()
                _events.send(DriveEvent.LaunchConsent(e.pendingIntent))
            } catch (e: Exception) {
                message(friendly(e))
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun message(text: String) = _events.send(DriveEvent.Message(text))

    private fun friendly(e: Exception): String {
        AppLog.e("drive", "Drive əməliyyatı alınmadı", e)
        return e.userMessage()
    }

    override fun onCleared() {
        keyDocument?.key?.wipe()
    }
}
