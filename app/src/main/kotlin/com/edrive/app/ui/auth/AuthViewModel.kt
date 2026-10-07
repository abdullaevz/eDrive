package com.edrive.app.ui.auth

import android.content.Context
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edrive.app.data.AccountException
import com.edrive.app.data.AccountRepository
import com.edrive.app.data.PinLockedException
import com.edrive.app.data.PinPolicy
import com.edrive.app.data.Session
import com.edrive.app.security.BiometricGate
import com.edrive.app.util.AppLog
import com.edrive.app.util.pdf.RecoveryDocuments
import com.edrive.crypto.wipe
import dagger.hilt.android.lifecycle.HiltViewModel
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** LOGIN — PIN ilə giriş; REGISTER — yeni profil; PIN_DOCUMENT — qeydiyyatdan sonra PIN sənədi və barmaq izi. */
enum class AuthMode { LOGIN, REGISTER, PIN_DOCUMENT }

/** [legacy] — 1.x hesabı: hələ PIN-i yoxdur, köhnə parolla bir dəfə keçid etməlidir. */
data class KnownUser(val id: Long, val username: String, val biometric: Boolean, val legacy: Boolean)

data class AuthState(
    val mode: AuthMode = AuthMode.LOGIN,
    val username: String = "",
    val pin: String = "",
    val pinConfirm: String = "",
    /** Yalnız 1.x hesabının keçidi üçün: köhnə parol (indi Təhlükəsizlik açarı). */
    val legacyPassword: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val pinPdfSavedAs: String? = null,
    /** PIN sənədini saxlamaq (defolt: bəli). İstifadəçi söndürə bilər. */
    val savePinPdf: Boolean = true,
    val enableBiometricAfter: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val biometric: BiometricGate,
    private val session: Session,
) : ViewModel() {
    fun beginExternalUi() = session.beginExternalUi()
    fun endExternalUi() = session.endExternalUi()

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /** null = hələ yüklənir (DB-dən). */
    val users: StateFlow<List<KnownUser>?> = accounts.observeUsers()
        .map { list -> list.map { KnownUser(it.id, it.username, it.biometricEnabled && it.hasPin, legacy = !it.hasPin) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val biometricAvailable: Boolean get() = biometric.isAvailable()

    private var pending: AccountRepository.PendingAccount? = null

    fun setMode(m: AuthMode) = _state.update { it.copy(mode = m, error = null, pin = "", pinConfirm = "", legacyPassword = "") }
    fun setUsername(v: String) = _state.update { it.copy(username = v.trim(), error = null, pin = "") }
    fun setPinConfirm(v: String) = _state.update { it.copy(pinConfirm = v, error = null) }
    fun setLegacyPassword(v: String) = _state.update { it.copy(legacyPassword = v, error = null) }
    fun setEnableBiometric(v: Boolean) = _state.update { it.copy(enableBiometricAfter = v) }
    fun setSavePinPdf(v: Boolean) = _state.update { it.copy(savePinPdf = v) }

    /** Girişdə 4-cü rəqəm yazılan kimi avtomatik yoxlanılır. */
    fun setPin(v: String) {
        _state.update { it.copy(pin = v, error = null) }
        val s = _state.value
        if (s.mode == AuthMode.LOGIN && v.length == PinPolicy.LENGTH && !isLegacy(s.username)) login()
    }

    fun login() = launchBusy {
        val s = _state.value
        try {
            accounts.login(s.username, s.pin.toCharArray())
        } finally {
            _state.update { it.copy(pin = "") }
        }
    }

    /** 1.x hesabı: köhnə parol + yeni PIN. Vault və fayllar dəyişmir. */
    fun migrateLegacy() = launchBusy {
        val s = _state.value
        accounts.migrateLegacy(s.username, s.legacyPassword.toCharArray(), s.pin.toCharArray(), s.pinConfirm.toCharArray())
        _state.update { AuthState(username = s.username) }
    }

    fun register() = launchBusy {
        val s = _state.value
        pending = accounts.register(s.username, s.pin.toCharArray(), s.pinConfirm.toCharArray())
        _state.update { it.copy(mode = AuthMode.PIN_DOCUMENT, pinConfirm = "", enableBiometricAfter = biometricAvailable) }
    }

    /** PIN sənədi istifadəçinin seçdiyi yerə (Storage Access Framework) yazılır. */
    fun savePinDocument(context: Context, uri: Uri) = launchBusy {
        val s = _state.value
        val pin = s.pin.toCharArray()
        try {
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)!!.use { RecoveryDocuments.writePin(it, s.username, pin) }
            }
        } finally {
            pin.wipe()
        }
        _state.update { it.copy(pinPdfSavedAs = uri.lastPathSegment?.substringAfterLast('/') ?: "eDrive PIN.pdf") }
    }

    fun finishRegistration(activity: FragmentActivity) = launchBusy {
        val p = pending ?: return@launchBusy
        val wantBio = _state.value.enableBiometricAfter
        accounts.activate(p)
        pending = null
        _state.value = AuthState(username = p.username)
        if (wantBio) runCatching { accounts.setBiometric(activity, true) }
    }

    fun biometricLogin(activity: FragmentActivity, userId: Long) = launchBusy {
        accounts.loginWithBiometric(activity, userId) // false — istifadəçi "PIN ilə" seçdi, səssizcə keçirik
    }

    /**
     * Giriş ekranı hər dəfə görünəndə (soyuq start, kilid, fondan qayıdış) çağırılır:
     * seçilmiş (yoxsa son) hesabda barmaq izi aktivdirsə, əvvəlcə o soruşulur.
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

    private fun isLegacy(username: String) = users.value?.firstOrNull { it.username.equals(username, true) }?.legacy == true

    private fun launchBusy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                block()
            } catch (e: PinLockedException) {
                val until = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(e.untilMillis))
                _state.update { it.copy(error = "Çox yanlış cəhd. $until-dək gözləyin.") }
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
}
