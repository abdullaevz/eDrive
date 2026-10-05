package com.edrive.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

/** Testlərdə Argon2id — BouncyCastle (Android-də native argon2kt eyni nəticəni verir, alqoritm standartdır). */
val bcArgon2 = PasswordKdf { password, salt, p ->
    val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
        .withSalt(salt).withMemoryAsKB(p.memoryKiB).withIterations(p.iterations).withParallelism(p.parallelism)
        .build()
    ByteArray(32).also { out -> Argon2BytesGenerator().apply { init(params) }.generateBytes(password, out) }
}

private val FAST = KdfParams(memoryKiB = 1024, iterations = 1, parallelism = 1)

class StreamingCipherTest {
    private val cs = 4096
    private val dek = AesGcm.newKey()

    private fun enc(plain: ByteArray, id: String = "f") =
        ByteArrayOutputStream().also { StreamingCipher.encrypt(ByteArrayInputStream(plain), it, dek, id, cs) }.toByteArray()

    private fun dec(ct: ByteArray, id: String = "f", key: ByteArray = dek) =
        ByteArrayOutputStream().also { StreamingCipher.decrypt(ByteArrayInputStream(ct), it, key, id) }.toByteArray()

    @Test fun roundTripVariousSizes() {
        for (size in listOf(0, 1, cs - 1, cs, cs + 1, 3 * cs, 3 * cs + 17, 100_000)) {
            val plain = Random(size).nextBytes(size)
            val ct = enc(plain)
            assertEquals("size=$size", StreamingCipher.ciphertextSize(size.toLong(), cs), ct.size.toLong())
            assertArrayEquals("size=$size", plain, dec(ct))
        }
    }

    @Test fun nonDeterministic() {
        val p = Random(1).nextBytes(5000)
        assertNotEquals(enc(p).toList(), enc(p).toList())
    }

    @Test fun detectsBitFlip() {
        val ct = enc(Random(2).nextBytes(3 * cs))
        ct[StreamingCipher.HEADER_BYTES + cs + 100] = (ct[StreamingCipher.HEADER_BYTES + cs + 100].toInt() xor 1).toByte()
        assertThrows(AuthenticationFailedException::class.java) { dec(ct) }
    }

    @Test fun detectsTruncation() {
        val ct = enc(Random(3).nextBytes(3 * cs))
        val cut = ct.copyOf(StreamingCipher.HEADER_BYTES + 2 * (cs + 16))
        assertThrows(AuthenticationFailedException::class.java) { dec(cut) }
    }

    @Test fun boundToFileId() {
        val ct = enc(Random(4).nextBytes(500), "A")
        assertThrows(AuthenticationFailedException::class.java) { dec(ct, "B") }
    }

    @Test fun wrongKeyFails() {
        val ct = enc(Random(5).nextBytes(500))
        assertThrows(AuthenticationFailedException::class.java) { dec(ct, key = AesGcm.newKey()) }
    }
}

class VaultTest {
    private val keys = VaultKeys(bcArgon2)

    @Test fun createAndUnlock() {
        val created = keys.create("my-password".toCharArray(), "2026-10-05T00:00:00Z", FAST)
        val header = VaultHeader.fromJson(created.header.toJson())
        assertArrayEquals(created.dek, keys.unlock(header, "my-password".toCharArray()))
        assertEquals(AesGcm.keyId(created.dek), header.keyId)
    }

    @Test fun wrongPasswordRejected() {
        val created = keys.create("my-password".toCharArray(), "now", FAST)
        assertThrows(AuthenticationFailedException::class.java) { keys.unlock(created.header, "other-pass".toCharArray()) }
    }

    @Test fun argon2MatchesRfc9106Style() {
        // Eyni giriş həmişə eyni açarı verməlidir (determinizm)
        val salt = ByteArray(16) { it.toByte() }
        assertArrayEquals(bcArgon2.deriveKey("x".toCharArray(), salt, FAST), bcArgon2.deriveKey("x".toCharArray(), salt, FAST))
    }
}

class ManifestTest {
    @Test fun sealOpen() {
        val dek = AesGcm.newKey()
        val m = ItemManifest("id1", "şəkil.jpg", "image/jpeg", 1234, "ab", 1L, 10, 20, "AAAA")
        assertEquals(m, ItemManifest.open(dek, "id1", m.seal(dek)))
        assertThrows(AuthenticationFailedException::class.java) { ItemManifest.open(dek, "id2", m.seal(dek)) }
    }
}

/** Spring Boot (Java) versiyasının şifrələdiyi fayl Kotlin tərəfindən açılır → format tam uyğundur. */
class BackendCompatibilityTest {
    @Test fun decryptsFileProducedBySpringBootApp() {
        val dek = ByteArray(32) { it.toByte() }
        val ct = javaClass.getResourceAsStream("/backend-sample.edrv")!!.readBytes()
        val out = ByteArrayOutputStream()
        StreamingCipher.decrypt(ByteArrayInputStream(ct), out, dek, "backend-sample")
        val expected = ByteArray(150_000) { (it * 31 + 7).toByte() }
        assertArrayEquals(expected, out.toByteArray())
    }
}
