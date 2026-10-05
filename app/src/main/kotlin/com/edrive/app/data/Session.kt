package com.edrive.app.data

import javax.inject.Inject
import javax.inject.Singleton
import com.edrive.crypto.wipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Açıq vault-un vəziyyəti. DEK yalnız burada, yalnız RAM-da saxlanılır.
 * Kilidlənəndə massiv sıfırlanır.
 */
@Singleton
class Session @Inject constructor() {

    data class Unlocked(val userId: Long, val username: String, internal val dek: ByteArray)

    private val _state = MutableStateFlow<Unlocked?>(null)
    val state: StateFlow<Unlocked?> = _state.asStateFlow()

    /** Fon rejimində avtomatik kilidi müvəqqəti dayandırmaq üçün (fayl seçici, Google icazə pəncərəsi açıq olanda). */
    private val externalUi = AtomicInteger(0)
    val isInExternalUi: Boolean get() = externalUi.get() > 0

    /** Kilid zamanı yaddaşdakı keşləri (deşifrə olunmuş miniatürlər və s.) təmizləmək üçün. */
    private val lockListeners = mutableListOf<() -> Unit>()

    val current: Unlocked? get() = _state.value

    fun unlock(userId: Long, username: String, dek: ByteArray) {
        lock()
        _state.value = Unlocked(userId, username, dek.copyOf())
    }

    /** DEK-in surəti — çağıran tərəf işi bitəndə `wipe()` etməlidir. */
    fun requireKey(): ByteArray = current?.dek?.copyOf() ?: throw VaultLockedException()

    fun requireUser(): Unlocked = current ?: throw VaultLockedException()

    fun lock() {
        _state.value?.dek?.wipe()
        _state.value = null
        lockListeners.forEach { it() }
    }

    fun addLockListener(l: () -> Unit) { lockListeners += l }

    fun beginExternalUi() { externalUi.incrementAndGet() }
    fun endExternalUi() { externalUi.updateAndGet { maxOf(0, it - 1) } }
}

class VaultLockedException : IllegalStateException("Vault kilidlidir")
