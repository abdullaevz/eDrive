package com.edrive.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vault açarının (DEK) bu cihazda saxlanması. DEK cihazın Keystore açarı ilə şifrələnir, beləliklə
 * Təhlükəsizlik açarı yalnız Drive-a qoşulanda soruşulur, sonrakı açılışlarda vault avtomatik yüklənir.
 */
interface DeviceKeyStore {
    class Wrapped(val iv: ByteArray, val ciphertext: ByteArray)

    /** Cihaz açarı yoxdur və ya etibarsızdır — vault Təhlükəsizlik açarı ilə yenidən açılmalıdır. */
    class KeyUnavailableException(cause: Throwable? = null) : Exception("Cihaz açarı əlçatan deyil", cause)

    fun wrap(userId: Long, dek: ByteArray): Wrapped

    /** @throws KeyUnavailableException */
    fun unwrap(userId: Long, wrapped: Wrapped): ByteArray

    fun delete(userId: Long)
}

/** Keystore-dakı AES-256-GCM açarı ilə ([DeviceKeyStore]). Açar çipdən çıxmır və biometrikdən asılı deyil. */
@Singleton
class KeystoreDeviceKeys @Inject constructor() : DeviceKeyStore {

    override fun wrap(userId: Long, dek: ByteArray): DeviceKeyStore.Wrapped {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, loadKey(userId) ?: createKey(userId)) }
        return DeviceKeyStore.Wrapped(cipher.iv, cipher.doFinal(dek))
    }

    override fun unwrap(userId: Long, wrapped: DeviceKeyStore.Wrapped): ByteArray {
        val key = loadKey(userId) ?: throw DeviceKeyStore.KeyUnavailableException()
        return try {
            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, wrapped.iv))
                doFinal(wrapped.ciphertext)
            }
        } catch (e: GeneralSecurityException) {
            throw DeviceKeyStore.KeyUnavailableException(e)
        }
    }

    override fun delete(userId: Long) {
        keyStore().apply {
            deleteEntry(alias(userId))
            deleteEntry(LEGACY_BIOMETRIC_PREFIX + userId) // 1.x: biometrik təsdiq tələb edən köhnə açar
        }
    }

    private fun createKey(userId: Long): SecretKey {
        val spec = KeyGenParameterSpec.Builder(alias(userId), KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    private fun loadKey(userId: Long): SecretKey? = keyStore().getKey(alias(userId), null) as? SecretKey

    private fun keyStore() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun alias(userId: Long) = "edrive_device_$userId"

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val LEGACY_BIOMETRIC_PREFIX = "edrive_bio_"
    }
}
