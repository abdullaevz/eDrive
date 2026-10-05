package com.edrive.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Hər yüklənən faylın şifrəli "pasportu" (<id>.meta). Drive-da şifrəli saxlanılır:
 * ad, tip, ölçü və kiçik miniatür (thumbnail) burada olur. Beləliklə:
 *  - Drive-da fayl adları görünmür (yalnız təsadüfi ID-lər);
 *  - qalereya bütün faylları endirmədən, yalnız bu kiçik faylla dərhal qurulur;
 *  - telefon dəyişsə belə, siyahı Drive-dan bərpa olunur.
 */
@Serializable
data class ItemManifest(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val sha256: String,
    val createdAt: Long,
    val width: Int = 0,
    val height: Int = 0,
    /** Base64 JPEG miniatür (~320 px) — yalnız şəkil və videolar üçün. */
    val thumbnail: String? = null,
) {
    fun seal(dek: ByteArray): ByteArray =
        AesGcm.seal(dek, json.encodeToString(serializer(), this).toByteArray(), CryptoConstants.AAD_META + id)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** @throws AuthenticationFailedException açar səhvdirsə və ya fayl başqa ID-yə aiddirsə */
        fun open(dek: ByteArray, id: String, sealed: ByteArray): ItemManifest {
            val plain = AesGcm.open(dek, sealed, CryptoConstants.AAD_META + id)
            try {
                val m = json.decodeFromString(serializer(), plain.decodeToString())
                if (m.id != id) throw AuthenticationFailedException("Manifest id mismatch")
                return m
            } finally {
                plain.wipe()
            }
        }
    }
}
