package com.edrive.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/** Paroldan açar törədən funksiya (Argon2id). Android-də native, testlərdə BouncyCastle implementasiyası. */
fun interface PasswordKdf {
    fun deriveKey(password: CharArray, salt: ByteArray, params: KdfParams): ByteArray
}

@Serializable
data class KdfParams(val memoryKiB: Int, val iterations: Int, val parallelism: Int) {
    companion object {
        /** RFC 9106 tövsiyəsinə yaxın: 64 MiB, t=3, p=1 — Spring Boot versiyası ilə eyni. */
        val DEFAULT = KdfParams(64 * 1024, 3, 1)
    }
}

/**
 * Vault başlığı (vault.json). Sirr ehtiva etmir: salt və KDF parametrləri açıqdır,
 * DEK isə paroldan törədilmiş KEK ilə şifrələnib. Format Spring Boot-dakı VaultConfig ilə uyğundur.
 */
@Serializable
data class VaultHeader(
    val version: Int = 1,
    val kdf: Kdf,
    val wrappedDek: String,
    val createdAt: String,
    val keyId: String,
) {
    @Serializable
    data class Kdf(val algorithm: String, val memoryKiB: Int, val iterations: Int, val parallelism: Int, val salt: String) {
        fun params() = KdfParams(memoryKiB, iterations, parallelism)
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
        fun fromJson(s: String): VaultHeader = json.decodeFromString(serializer(), s)
    }
}

/**
 * Açar iyerarxiyası (envelope encryption):
 * ```
 *   parol ──Argon2id(salt)──► KEK ──AES-GCM──► DEK (təsadüfi 256-bit) ──► hər faylın açarı
 * ```
 */
class VaultKeys(private val kdf: PasswordKdf) {

    class Created(val header: VaultHeader, val dek: ByteArray)

    fun create(password: CharArray, nowIso: String, params: KdfParams = KdfParams.DEFAULT): Created {
        val dek = AesGcm.newKey()
        return Created(seal(dek, password, params, nowIso), dek)
    }

    /**
     * Parol dəyişməsi: eyni DEK yeni təsadüfi salt və yeni paroldan törədilən KEK ilə yenidən sarılır.
     * DEK, `keyId` və bütün fayllar dəyişmir. Köhnə başlıq nüsxəsi köhnə parolla hələ də açılır.
     *
     * @throws AuthenticationFailedException köhnə parol səhvdirsə
     */
    fun rewrap(header: VaultHeader, oldPassword: CharArray, newPassword: CharArray, params: KdfParams = header.kdf.params()): VaultHeader {
        val dek = unlock(header, oldPassword)
        try {
            return seal(dek, newPassword, params, header.createdAt)
        } finally {
            dek.wipe()
        }
    }

    private fun seal(dek: ByteArray, password: CharArray, params: KdfParams, createdAt: String): VaultHeader {
        val salt = AesGcm.randomBytes(16)
        val kek = kdf.deriveKey(password, salt, params)
        try {
            val b64 = Base64.getEncoder()
            return VaultHeader(
                kdf = VaultHeader.Kdf("argon2id", params.memoryKiB, params.iterations, params.parallelism, b64.encodeToString(salt)),
                wrappedDek = b64.encodeToString(AesGcm.seal(kek, dek, CryptoConstants.AAD_DEK)),
                createdAt = createdAt,
                keyId = AesGcm.keyId(dek),
            )
        } finally {
            kek.wipe()
        }
    }

    /** @throws AuthenticationFailedException parol səhvdirsə */
    fun unlock(header: VaultHeader, password: CharArray): ByteArray {
        val b64 = Base64.getDecoder()
        val kek = kdf.deriveKey(password, b64.decode(header.kdf.salt), header.kdf.params())
        try {
            return AesGcm.open(kek, b64.decode(header.wrappedDek), CryptoConstants.AAD_DEK)
        } finally {
            kek.wipe()
        }
    }
}
