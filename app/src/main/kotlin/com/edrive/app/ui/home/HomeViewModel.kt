package com.edrive.app.ui.home

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edrive.app.AppContainer
import com.edrive.app.data.vault.DriveConnectionService.ConnectOutcome
import com.edrive.app.drive.DriveConsentRequired
import com.edrive.app.data.db.entity.FileEntity
import com.edrive.app.data.db.entity.UserEntity
import com.edrive.app.drive.DriveException
import com.edrive.app.util.AppLog
import com.edrive.crypto.VaultHeader
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface HomeEvent {
    data class Message(val text: String) : HomeEvent
    data class LaunchConsent(val pendingIntent: PendingIntent) : HomeEvent
}

data class HomeUi(
    val driveBusy: Boolean = false,
    val syncing: Boolean = false,
    val importing: Boolean = false,
    /** Drive-da başqa cihazda yaradılmış vault tapıldı — parol dialoqu göstərilir. */
    val remoteVault: VaultHeader? = null,
    val remotePasswordError: String? = null,
)

class HomeViewModel(private val c: AppContainer, val userId: Long, val username: String) : ViewModel() {

    val user: StateFlow<UserEntity?> = c.accounts.observeUser(userId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val files: StateFlow<List<FileEntity>?> = c.fileAccess.observeFiles(userId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _ui = MutableStateFlow(HomeUi())
    val ui: StateFlow<HomeUi> = _ui.asStateFlow()

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val biometricAvailable: Boolean get() = c.biometric.isAvailable()

    init {
        // Açılışda Drive ilə sinxronlaşma (başqa cihazdan əlavə/silinən fayllar) və yarımçıq yükləmələr
        viewModelScope.launch {
            if (c.db.users().byId(userId)?.driveUserFolderId != null) {
                sync(quiet = true)
                c.uploadScheduler.schedule()
            }
        }
    }

    suspend fun thumbnail(id: String): ImageBitmap? = c.fileAccess.thumbnail(userId, id)

    fun connectDrive() = driveOp { handle(c.connection.startConnect()) }

    fun onConsentResult(data: Intent?) = driveOp {
        val token = c.driveAuth.tokenFromConsentResult(data)
        handle(c.connection.finishConnect(token))
    }

    fun submitRemotePassword(password: String) {
        val remote = _ui.value.remoteVault ?: return
        viewModelScope.launch {
            _ui.update { it.copy(driveBusy = true, remotePasswordError = null) }
            try {
                val r = c.connection.adoptRemote(remote, password.toCharArray())
                _ui.update { it.copy(remoteVault = null) }
                handle(r)
            } catch (e: Exception) {
                _ui.update { it.copy(remotePasswordError = e.message) }
            } finally {
                _ui.update { it.copy(driveBusy = false) }
            }
        }
    }

    fun cancelRemotePassword() {
        viewModelScope.launch {
            c.connection.disconnect()
            _ui.update { it.copy(remoteVault = null, remotePasswordError = null) }
        }
    }

    private suspend fun handle(r: ConnectOutcome) {
        when (r) {
            is ConnectOutcome.Connected -> message(
                if (r.imported > 0) "Google Drive qoşuldu · ${r.imported} fayl bərpa olundu" else "Google Drive qoşuldu: ${r.email}",
            )
            is ConnectOutcome.NeedsPassword -> _ui.update { it.copy(remoteVault = r.remote) }
        }
    }

    fun disconnectDrive() = driveOp {
        c.connection.disconnect()
        message("Google Drive ayrıldı. Fayllar Drive-da şifrəli qalır.")
    }

    fun sync(quiet: Boolean = false) {
        viewModelScope.launch {
            _ui.update { it.copy(syncing = true) }
            try {
                val n = c.sync.sync()
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
                c.importer.import(uris)
                message("${uris.size} fayl şifrələndi · Drive-a yüklənir")
            } catch (e: Exception) {
                message(friendly(e))
            } finally {
                _ui.update { it.copy(importing = false) }
            }
        }
    }

    fun retry(id: String) = viewModelScope.launch { c.uploads.retry(id) }

    fun enableBiometric(activity: FragmentActivity) = viewModelScope.launch {
        try {
            c.accounts.enableBiometric(activity)
            message("Barmaq izi ilə giriş aktivləşdirildi")
        } catch (e: Exception) {
            message(e.message ?: "Alınmadı")
        }
    }

    fun disableBiometric() = viewModelScope.launch {
        c.accounts.disableBiometric()
        message("Barmaq izi ilə giriş söndürüldü")
    }

    fun lock() = c.session.lock()

    private fun driveOp(block: suspend () -> Unit) {
        if (_ui.value.driveBusy) return
        viewModelScope.launch {
            _ui.update { it.copy(driveBusy = true) }
            try {
                block()
            } catch (e: DriveConsentRequired) {
                c.session.beginExternalUi()
                _events.send(HomeEvent.LaunchConsent(e.pendingIntent))
            } catch (e: Exception) {
                message(friendly(e))
            } finally {
                _ui.update { it.copy(driveBusy = false) }
            }
        }
    }

    private suspend fun message(text: String) = _events.send(HomeEvent.Message(text))

    private fun friendly(e: Exception): String {
        AppLog.e("home", "Əməliyyat alınmadı", e)
        return friendlyText(e)
    }

    private fun friendlyText(e: Exception): String = when (e) {
        is IOException -> "İnternet bağlantısını yoxlayın"
        is DriveException -> if (e.code == 403) "Google Drive girişi rədd edildi (403). Drive API aktivdirmi?" else e.message ?: "Drive xətası"
        is com.google.android.gms.common.api.ApiException ->
            "Google girişi alınmadı (kod ${e.statusCode}). Google Cloud Console-da Android OAuth client (paket adı + SHA-1) qeyd olunubmu?"
        else -> e.message ?: "Gözlənilməz xəta"
    }
}
