package com.edrive.app.security

import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Barmaq izi ilə DEK-in qorunması üçün abstraksiya (testlərdə saxtası istifadə olunur). */
interface BiometricKeyStore {
    class Wrapped(val iv: ByteArray, val ciphertext: ByteArray)
    class InvalidatedException : Exception("Biometrik açar etibarsızdır (barmaq izləri dəyişib)")
    class BiometricCancelled(msg: String) : Exception(msg)

    fun isAvailable(): Boolean
    suspend fun enroll(activity: FragmentActivity, userId: Long, dek: ByteArray): Wrapped
    suspend fun unlock(activity: FragmentActivity, userId: Long, wrapped: BiometricKeyStore.Wrapped): ByteArray
    fun deleteKey(userId: Long)
}

/**
 * Barmaq izi ilə giriş.
 *
 * DEK telefonun təhlükəsiz çipindəki (Android Keystore / TEE / StrongBox) AES açarı ilə şifrələnir.
 * Bu açar çipdən heç vaxt çıxmır və yalnız uğurlu biometrik təsdiqdən dərhal sonra işləyir.
 * Yeni barmaq izi əlavə olunsa, açar avtomatik etibarsız olur → yenidən parolla daxil olmaq lazımdır.
 */
@Singleton
class BiometricVault @Inject constructor(@ApplicationContext private val context: Context) : BiometricKeyStore {

    override fun isAvailable(): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** Biometrik təsdiq alıb DEK-i şifrələyir. */
    override suspend fun enroll(activity: FragmentActivity, userId: Long, dek: ByteArray): BiometricKeyStore.Wrapped {
        deleteKey(userId)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, createKey(userId)) }
        val authed = authenticate(activity, cipher, "Barmaq izini aktivləşdir", "Növbəti girişlərdə parol əvəzinə barmaq izi istifadə ediləcək")
        return BiometricKeyStore.Wrapped(authed.iv, authed.doFinal(dek))
    }

    /** Biometrik təsdiq alıb DEK-i açır. */
    override suspend fun unlock(activity: FragmentActivity, userId: Long, wrapped: BiometricKeyStore.Wrapped): ByteArray {
        val key = loadKey(userId) ?: throw BiometricKeyStore.InvalidatedException()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, wrapped.iv))
        } catch (e: KeyPermanentlyInvalidatedException) {
            deleteKey(userId)
            throw BiometricKeyStore.InvalidatedException()
        }
        val authed = authenticate(activity, cipher, "eDrive-ı aç", "Vault-u açmaq üçün barmaq izinizi təsdiqləyin")
        return authed.doFinal(wrapped.ciphertext)
    }

    override fun deleteKey(userId: Long) {
        keyStore().deleteEntry(alias(userId))
    }

    private suspend fun authenticate(activity: FragmentActivity, cipher: Cipher, title: String, subtitle: String): Cipher =
        suspendCancellableCoroutine { cont ->
            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val c = result.cryptoObject?.cipher
                        if (c != null) cont.resume(c) else cont.resumeWithException(IllegalStateException("Cipher yoxdur"))
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (cont.isActive) cont.resumeWithException(BiometricKeyStore.BiometricCancelled(errString.toString()))
                    }
                })
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText("Parol ilə")
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .build()
            prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
            cont.invokeOnCancellation { prompt.cancelAuthentication() }
        }


    private fun createKey(userId: Long): SecretKey {
        val spec = KeyGenParameterSpec.Builder(alias(userId), KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    private fun loadKey(userId: Long): SecretKey? = keyStore().getKey(alias(userId), null) as? SecretKey

    private fun keyStore() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun alias(userId: Long) = "edrive_bio_$userId"

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
