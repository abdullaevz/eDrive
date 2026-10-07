package com.edrive.app.ui.auth

import android.content.Context
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.edrive.app.data.Session
import androidx.lifecycle.viewModelScope
import com.edrive.app.data.AccountException
import com.edrive.app.data.AccountRepository
import com.edrive.app.security.BiometricKeyStore
import com.edrive.app.util.AppLog
import com.edrive.app.util.RecoveryPdf
import com.edrive.crypto.wipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AuthMode { LOGIN, REGISTER, RECOVERY }

data class KnownUser(val id: Long, val username: String, val biometric: Boolean)

data class AuthState(
    val mode: AuthMode = AuthMode.LOGIN,
    val username: String = "",
    val password: String = "",
    val confirm: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val recoverySavedAs: String? = null,
    val enableBiometricAfter: Boolean = false,
    /** Bərpa PDF-ini saxlamaq (defolt: bəli). İstifadəçi söndürə bilər. */
    val saveRecoveryPdf: Boolean = true,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val biometric: BiometricKeyStore,
    private val session: Session,
) : ViewModel() {
    fun beginExternalUi() = session.beginExternalUi()
    fun endExternalUi() = session.endExternalUi()


    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /** null = hələ yüklənir (DB-dən). */
    val users: StateFlow<List<KnownUser>?> = accounts.observeUsers()
        .map { list -> list.map { KnownUser(it.id, it.username, it.bioWrappedDek != null) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val biometricAvailable: Boolean get() = biometric.isAvailable()

    private var pending: AccountRepository.PendingAccount? = null

    fun setMode(m: AuthMode) = _state.update { it.copy(mode = m, error = null, password = "", confirm = "") }
    fun setUsername(v: String) = _state.update { it.copy(username = v.trim(), error = null) }
    fun setPassword(v: String) = _state.update { it.copy(password = v, error = null) }
    fun setConfirm(v: String) = _state.update { it.copy(confirm = v, error = null) }
    fun setEnableBiometric(v: Boolean) = _state.update { it.copy(enableBiometricAfter = v) }
    fun setSaveRecoveryPdf(v: Boolean) = _state.update { it.copy(saveRecoveryPdf = v) }

    fun login() = launchBusy {
        val s = _state.value
        accounts.login(s.username, s.password.toCharArray())
        _state.update { it.copy(password = "") }
    }

    fun register() = launchBusy {
        val s = _state.value
        if (s.password != s.confirm) throw AccountException("Parollar uyğun gəlmir")
        pending = accounts.register(s.username, s.password.toCharArray())
        _state.update { it.copy(mode = AuthMode.RECOVERY, enableBiometricAfter = biometricAvailable) }
    }

    /** Bərpa PDF-i istifadəçinin seçdiyi yerə (Storage Access Framework) yazılır. */
    fun saveRecovery(context: Context, uri: Uri) = launchBusy {
        val s = _state.value
        val pwd = s.password.toCharArray()
        try {
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)!!.use { RecoveryPdf.write(it, s.username, pwd) }
            }
        } finally {
            pwd.wipe()
        }
        _state.update { it.copy(recoverySavedAs = uri.lastPathSegment?.substringAfterLast('/') ?: "eDrive bərpa sənədi.pdf") }
    }

    fun finishRegistration(activity: FragmentActivity) = launchBusy {
        val p = pending ?: return@launchBusy
        val wantBio = _state.value.enableBiometricAfter
        accounts.activate(p)
        pending = null
        _state.value = AuthState(username = p.username)
        if (wantBio) runCatching { accounts.enableBiometric(activity) }
    }

    fun biometricLogin(activity: FragmentActivity, userId: Long) = launchBusy {
        try {
            accounts.loginWithBiometric(activity, userId)
        } catch (e: BiometricKeyStore.BiometricCancelled) {
            // istifadəçi "Parol ilə" seçdi — səssizcə keçirik
        }
    }

    /**
     * Giriş ekranı hər dəfə görünəndə (soyuq start, kilid, fondan qayıdış) çağırılır:
     * seçilmiş (yoxsa son) hesabda barmaq izi aktivdirsə, əvvəlcə o soruşulur.
     * İstifadəçi "Parol ilə" seçərsə, ekran yenidən açılana qədər təkrar soruşulmur.
     */
    fun autoBiometric(activity: FragmentActivity) {
        val s = _state.value
        if (s.mode != AuthMode.LOGIN || s.busy) return
        val list = users.value ?: return
        val target = list.firstOrNull { it.username.equals(s.username, ignoreCase = true) } ?: list.firstOrNull() ?: return
        if (s.username.isEmpty()) _state.update { it.copy(username = target.username) }
        if (target.biometric && biometricAvailable) biometricLogin(activity, target.id)
    }

    fun prefillLastUser() {
        if (_state.value.username.isEmpty()) users.value?.firstOrNull()?.let { u -> _state.update { it.copy(username = u.username) } }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                block()
            } catch (e: AccountException) {
                AppLog.w("auth", "Giriş/qeydiyyat rədd edildi: ${e.message}")
                _state.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                AppLog.e("auth", "Gözlənilməz xəta", e)
                _state.update { it.copy(error = e.message ?: "Gözlənilməz xəta") }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    override fun onCleared() {
        pending?.dek?.wipe()
    }
}
