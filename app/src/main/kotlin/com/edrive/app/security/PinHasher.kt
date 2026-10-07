package com.edrive.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/** PIN-in yoxlama dəyərini hesablayır (PIN-in özü heç yerdə saxlanılmır). */
fun interface PinHasher {
    fun hash(pin: CharArray, salt: ByteArray): ByteArray
}

/**
 * PIN hash = HMAC-SHA256(Keystore açarı, salt ‖ PIN).
 *
 * 4 rəqəmli PIN-in cəmi 10 000 variantı var; düz hash bazadan çıxarılsa saniyələrdə sınardı.
 * HMAC açarı isə telefonun Keystore-undadır və oradan çıxmır — hesablama yalnız bu cihazda mümkündür.
 */
@Singleton
class KeystorePinHasher @Inject constructor() : PinHasher {

    override fun hash(pin: CharArray, salt: ByteArray): ByteArray {
        val input = salt + String(pin).toByteArray(Charsets.UTF_8)
        try {
            return Mac.getInstance(ALGORITHM).run {
                init(key())
                doFinal(input)
            }
        } finally {
            input.fill(0)
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN).build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "edrive_pin_hmac"
        const val ALGORITHM = "HmacSHA256"
    }
}
