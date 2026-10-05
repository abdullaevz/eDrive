package com.edrive.crypto

import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Bütün kriptoqrafik sabitlər — Spring Boot versiyası ilə eynidir (format uyğunluğu). */
object CryptoConstants {
    const val AES_GCM = "AES/GCM/NoPadding"
    const val KEY_BYTES = 32            // AES-256
    const val GCM_NONCE_BYTES = 12      // 96-bit nonce
    const val GCM_TAG_BITS = 128
    const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
    const val DEFAULT_CHUNK_SIZE = 64 * 1024
    const val NONCE_PREFIX_BYTES = 7

    val FILE_MAGIC = byteArrayOf('E'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(), 'V'.code.toByte())
    const val FILE_FORMAT_VERSION: Byte = 1

    const val AAD_DEK = "edrive:v1:dek"
    const val AAD_FILE_KEY = "edrive:v1:file-key:"
    const val AAD_META = "edrive:v1:meta:"
    const val AAD_THUMB = "edrive:v1:thumb:"
    const val KEY_ID_LABEL = "edrive:v1:key-id"
}

open class CryptoException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** GCM teqi uyğun gəlmədi: səhv açar/parol və ya dəyişdirilmiş data. */
class AuthenticationFailedException(message: String, cause: Throwable? = null) : CryptoException(message, cause)

/** Kiçik blob-lar üçün AES-256-GCM. Çıxış: nonce(12) || ciphertext || tag(16). */
object AesGcm {
    private val random = SecureRandom()

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { random.nextBytes(it) }

    fun newKey(): ByteArray = randomBytes(CryptoConstants.KEY_BYTES)

    fun seal(key: ByteArray, plaintext: ByteArray, aad: String): ByteArray =
        seal(key, plaintext, aad.toByteArray(StandardCharsets.UTF_8))

    fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray = try {
        val nonce = randomBytes(CryptoConstants.GCM_NONCE_BYTES)
        val c = Cipher.getInstance(CryptoConstants.AES_GCM)
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(CryptoConstants.GCM_TAG_BITS, nonce))
        c.updateAAD(aad)
        nonce + c.doFinal(plaintext)
    } catch (e: GeneralSecurityException) {
        throw CryptoException("AES-GCM encryption failed", e)
    }

    fun open(key: ByteArray, sealed: ByteArray, aad: String): ByteArray =
        open(key, sealed, aad.toByteArray(StandardCharsets.UTF_8))

    fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray {
        if (sealed.size < CryptoConstants.GCM_NONCE_BYTES + CryptoConstants.GCM_TAG_BYTES) {
            throw AuthenticationFailedException("Ciphertext too short")
        }
        return try {
            val c = Cipher.getInstance(CryptoConstants.AES_GCM)
            c.init(
                Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"),
                GCMParameterSpec(CryptoConstants.GCM_TAG_BITS, sealed, 0, CryptoConstants.GCM_NONCE_BYTES),
            )
            c.updateAAD(aad)
            c.doFinal(sealed, CryptoConstants.GCM_NONCE_BYTES, sealed.size - CryptoConstants.GCM_NONCE_BYTES)
        } catch (e: AEADBadTagException) {
            throw AuthenticationFailedException("GCM tag verification failed", e)
        } catch (e: GeneralSecurityException) {
            throw CryptoException("AES-GCM decryption failed", e)
        }
    }

    /** Açarın açıq identifikatoru: açarın özünü açmadan iki vault-un eyni açarı paylaşıb-paylaşmadığını yoxlamaq üçün. */
    fun keyId(key: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(CryptoConstants.KEY_ID_LABEL.toByteArray()).copyOf(16).toHex()
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

fun ByteArray.wipe() = fill(0)
fun CharArray.wipe() = fill('\u0000')
