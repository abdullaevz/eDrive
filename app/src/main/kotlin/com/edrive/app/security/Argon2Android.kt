package com.edrive.app.security

import com.edrive.crypto.KdfParams
import com.edrive.crypto.PasswordKdf
import com.edrive.crypto.wipe
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import com.lambdapioneer.argon2kt.Argon2Version
import java.nio.ByteBuffer
import java.nio.CharBuffer

/**
 * Argon2id — native (C) implementasiya. Saf Java versiyasından telefonda bir neçə dəfə sürətlidir.
 * Nəticə standartdır: Spring Boot (BouncyCastle) ilə eyni parol+salt eyni açarı verir.
 */
class Argon2Android : PasswordKdf {
    private val argon2 = Argon2Kt()

    override fun deriveKey(password: CharArray, salt: ByteArray, params: KdfParams): ByteArray {
        val pwd = utf8(password)
        try {
            val result = argon2.hash(
                mode = Argon2Mode.ARGON2_ID,
                password = pwd,
                salt = salt,
                tCostInIterations = params.iterations,
                mCostInKibibyte = params.memoryKiB,
                parallelism = params.parallelism,
                hashLengthInBytes = 32,
                version = Argon2Version.V13,
            )
            return result.rawHashAsByteArray()
        } finally {
            pwd.wipe()
        }
    }

    private fun utf8(chars: CharArray): ByteArray {
        val bb: ByteBuffer = Charsets.UTF_8.encode(CharBuffer.wrap(chars))
        val out = ByteArray(bb.remaining()).also { bb.get(it) }
        if (bb.hasArray()) bb.array().fill(0)
        return out
    }
}
