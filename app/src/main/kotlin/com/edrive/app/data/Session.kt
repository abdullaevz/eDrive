package com.edrive.app.data

import javax.inject.Inject
import javax.inject.Singleton
import com.edrive.crypto.wipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Açıq sessiyanın vəziyyəti: hansı profil PIN/biometriklə açılıb və (varsa) vault açarı.
 * DEK yalnız burada, yalnız RAM-da saxlanılır; kilidlənəndə və ya vault ayrılanda massiv sıfırlanır.
 */
@Singleton
class Session @Inject constructor() {

    /** [dek] `null` — profilin hələ vault-u yoxdur (Drive-a qoşulmayıb) və ya Təhlükəsizlik açarı tələb olunur. */
    class Unlocked(val userId: Long, val username: String, internal val dek: ByteArray?) {
        val hasVault: Boolean get() = dek != null
    }

    private val _state = MutableStateFlow<Unlocked?>(null)
    val state: StateFlow<Unlocked?> = _state.asStateFlow()

    /** Fon rejimində avtomatik kilidi müvəqqəti dayandırmaq üçün (fayl seçici, Google icazə pəncərəsi açıq olanda). */
    private val externalUi = AtomicInteger(0)
    val isInExternalUi: Boolean get() = externalUi.get() > 0

    /** Kilid zamanı yaddaşdakı keşləri (deşifrə olunmuş miniatürlər və s.) təmizləmək üçün. */
    private val lockListeners = CopyOnWriteArrayList<() -> Unit>()

    val current: Unlocked? get() = _state.value

    fun unlock(userId: Long, username: String, dek: ByteArray?) {
        lock()
        _state.value = Unlocked(userId, username, dek?.copyOf())
    }

    /** Açıq sessiyaya vault açarını bağlayır (vault yaradılanda və ya Drive-dakından götürüləndə). */
    fun attachVault(dek: ByteArray) {
        val s = requireUser()
        s.dek?.wipe()
        _state.value = Unlocked(s.userId, s.username, dek.copyOf())
    }

    /** Vault-u sessiyadan ayırır (Drive-dan ayrılanda). Profil açıq qalır. */
    fun detachVault() {
        val s = current ?: return
        s.dek?.wipe()
        lockListeners.forEach { it() }
        _state.value = Unlocked(s.userId, s.username, null)
    }

    /** DEK-in surəti — çağıran tərəf işi bitəndə `wipe()` etməlidir. */
    fun requireKey(): ByteArray = requireUser().dek?.copyOf() ?: throw VaultLockedException()

    fun requireUser(): Unlocked = current ?: throw SessionLockedException()

    fun lock() {
        _state.value?.dek?.wipe()
        _state.value = null
        lockListeners.forEach { it() }
    }

    fun addLockListener(l: () -> Unit) { lockListeners += l }

    /** Ömrü bitən komponentlər (məs. video pleyer) dinləyicisini silməlidir ki, sızma olmasın. */
    fun removeLockListener(l: () -> Unit) { lockListeners.remove(l) }

    fun beginExternalUi() { externalUi.incrementAndGet() }
    fun endExternalUi() { externalUi.updateAndGet { maxOf(0, it - 1) } }
}

/** Profil kilidlidir (PIN tələb olunur). */
class SessionLockedException : IllegalStateException("Proqram kilidlidir")

/** Profil açıqdır, amma vault açarı yoxdur (Drive-a qoşulmayıb və ya Təhlükəsizlik açarı tələb olunur). */
class VaultLockedException : IllegalStateException("Vault hazır deyil — Google Drive-a qoşulun")
