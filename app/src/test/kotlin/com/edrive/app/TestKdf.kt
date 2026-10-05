package com.edrive.app

import com.edrive.crypto.PasswordKdf
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/** JVM testlərində native argon2kt yüklənmir — eyni standart alqoritmin BouncyCastle versiyası. */
val bcKdf = PasswordKdf { password, salt, p ->
    val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
        .withSalt(salt).withMemoryAsKB(p.memoryKiB).withIterations(p.iterations).withParallelism(p.parallelism)
        .build()
    ByteArray(32).also { Argon2BytesGenerator().apply { init(params) }.generateBytes(password, it) }
}
