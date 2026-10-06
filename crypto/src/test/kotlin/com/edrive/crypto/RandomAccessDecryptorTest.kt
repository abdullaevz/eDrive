package com.edrive.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import kotlin.random.Random

class RandomAccessDecryptorTest {
    private val cs = 1024 // StreamingCipher.readHeader minimum
    private val dek = AesGcm.newKey()

    private class BytesSource(private val data: ByteArray) : CiphertextSource {
        override val length: Long = data.size.toLong()
        override fun readFully(position: Long, buffer: ByteArray, offset: Int, len: Int) {
            if (position < 0 || position + len > data.size) throw EOFException()
            System.arraycopy(data, position.toInt(), buffer, offset, len)
        }
        override fun close() {}
    }

    private fun enc(plain: ByteArray, id: String = "f", chunk: Int = cs) =
        ByteArrayOutputStream().also { StreamingCipher.encrypt(ByteArrayInputStream(plain), it, dek, id, chunk) }.toByteArray()

    private fun open(ct: ByteArray, id: String = "f", key: ByteArray = dek, cache: Int = 4) =
        RandomAccessDecryptor.open(BytesSource(ct), key, id, cache)

    private fun readAll(d: RandomAccessDecryptor, step: Int = 777): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(step)
        var pos = 0L
        while (true) {
            val n = d.read(pos, buf, 0, buf.size)
            if (n < 0) break
            out.write(buf, 0, n)
            pos += n
        }
        return out.toByteArray()
    }

    @Test fun matchesPlaintextForVariousSizes() {
        for (size in listOf(0, 1, cs - 1, cs, cs + 1, 3 * cs, 3 * cs + 17, 100_000)) {
            val plain = Random(size).nextBytes(size)
            val d = open(enc(plain))
            assertEquals("size=$size", size.toLong(), d.plaintextLength)
            assertArrayEquals("size=$size", plain, readAll(d))
        }
    }

    @Test fun chunkCountMatchesGeometry() {
        assertEquals(1L, open(enc(ByteArray(0))).chunkCount)
        assertEquals(1L, open(enc(ByteArray(cs))).chunkCount)
        assertEquals(2L, open(enc(ByteArray(cs + 1))).chunkCount)
        assertEquals(3L, open(enc(ByteArray(3 * cs))).chunkCount)
    }

    @Test fun randomRangesMatchPlaintext() {
        val plain = Random(7).nextBytes(50_000)
        val d = open(enc(plain))
        val rnd = Random(42)
        repeat(500) {
            val pos = rnd.nextInt(0, plain.size + 1)
            val len = rnd.nextInt(1, 5000)
            val buf = ByteArray(len)
            val n = d.read(pos.toLong(), buf, 0, len)
            if (pos >= plain.size) {
                assertEquals(-1, n)
            } else {
                val expected = minOf(len, plain.size - pos)
                assertEquals(expected, n)
                assertArrayEquals(plain.copyOfRange(pos, pos + expected), buf.copyOf(n))
            }
        }
    }

    @Test fun readsAcrossChunkBoundaryAtOffset() {
        val plain = Random(8).nextBytes(5 * cs)
        val d = open(enc(plain))
        val buf = ByteArray(2000)
        assertEquals(1995, d.read(cs - 3L, buf, 5, 1995))
        assertArrayEquals(plain.copyOfRange(cs - 3, cs - 3 + 1995), buf.copyOfRange(5, 2000))
    }

    @Test fun endOfFileAndZeroLength() {
        val plain = Random(9).nextBytes(2 * cs + 5)
        val d = open(enc(plain))
        val buf = ByteArray(10)
        assertEquals(-1, d.read(plain.size.toLong(), buf, 0, 10))
        assertEquals(-1, d.read(plain.size + 100L, buf, 0, 10))
        assertEquals(0, d.read(0, buf, 0, 0))
        assertEquals(5, d.read(plain.size - 5L, buf, 0, 10))
    }

    @Test fun tamperedChunkFailsOnlyThatChunk() {
        val plain = Random(10).nextBytes(4 * cs)
        val ct = enc(plain)
        val idx = StreamingCipher.HEADER_BYTES + (cs + 16) + 100 // chunk 1
        ct[idx] = (ct[idx].toInt() xor 1).toByte()
        val d = open(ct)
        val buf = ByteArray(10)
        assertEquals(10, d.read(0, buf, 0, 10))
        assertEquals(10, d.read(2L * cs, buf, 0, 10))
        assertThrows(AuthenticationFailedException::class.java) { d.read(cs.toLong(), buf, 0, 10) }
    }

    @Test fun truncationAtChunkBoundaryDetected() {
        val ct = enc(Random(11).nextBytes(3 * cs))
        val cut = ct.copyOf(StreamingCipher.HEADER_BYTES + 2 * (cs + 16))
        val d = open(cut) // həndəsə düzgündür, amma 1-ci chunk artıq "sonuncu" sayılır
        val buf = ByteArray(10)
        assertEquals(10, d.read(0, buf, 0, 10))
        assertThrows(AuthenticationFailedException::class.java) { d.read(cs.toLong(), buf, 0, 10) }
    }

    @Test fun truncationInsideLastChunkDetected() {
        val ct = enc(Random(12).nextBytes(3 * cs + 100))
        val d = open(ct.copyOf(ct.size - 10))
        val buf = ByteArray(10)
        assertThrows(AuthenticationFailedException::class.java) { d.read(3L * cs, buf, 0, 10) }
    }

    @Test fun truncationLeavingLessThanTagRejectedAtOpen() {
        val ct = enc(Random(13).nextBytes(3 * cs + 100))
        val tail = (ct.size - StreamingCipher.HEADER_BYTES) % (cs + 16) // son chunk-ın ölçüsü
        assertThrows(AuthenticationFailedException::class.java) { open(ct.copyOf(ct.size - tail + 5)) }
    }

    @Test fun appendedBytesDetected() {
        val ct = enc(Random(14).nextBytes(3 * cs)) // son chunk tam
        val d = open(ct + ByteArray(100) { 1 })
        val buf = ByteArray(10)
        assertThrows(AuthenticationFailedException::class.java) { d.read(2L * cs, buf, 0, 10) }
    }

    @Test fun wrongKeyOrFileIdRejected() {
        val ct = enc(Random(15).nextBytes(500), "A")
        assertThrows(AuthenticationFailedException::class.java) { open(ct, "B") }
        assertThrows(AuthenticationFailedException::class.java) { open(ct, "A", AesGcm.newKey()) }
    }

    @Test fun notAnEdriveFile() {
        assertThrows(CryptoException::class.java) { open(Random(16).nextBytes(500)) }
        assertThrows(CryptoException::class.java) { open(ByteArray(10)) }
    }

    @Test fun equalsSequentialDecryptWithDefaultChunkSize() {
        val plain = Random(17).nextBytes(200_000)
        val ct = enc(plain, chunk = CryptoConstants.DEFAULT_CHUNK_SIZE)
        val sequential = ByteArrayOutputStream().also { StreamingCipher.decrypt(ByteArrayInputStream(ct), it, dek, "f") }.toByteArray()
        assertArrayEquals(sequential, readAll(open(ct), step = 10_000))
        assertArrayEquals(plain, sequential)
    }

    @Test fun tinyCacheStillCorrectWhenReadingBackwards() {
        val plain = Random(18).nextBytes(10 * cs + 3)
        val d = open(enc(plain), cache = 1)
        for (pos in plain.size - 1 downTo 0 step 333) {
            val buf = ByteArray(50)
            val n = d.read(pos.toLong(), buf, 0, 50)
            assertArrayEquals(plain.copyOfRange(pos, pos + n), buf.copyOf(n))
        }
    }

    @Test fun decryptsFileProducedBySpringBootAtRandomOffsets() {
        val sampleDek = ByteArray(32) { it.toByte() }
        val ct = javaClass.getResourceAsStream("/backend-sample.edrv")!!.readBytes()
        val d = RandomAccessDecryptor.open(BytesSource(ct), sampleDek, "backend-sample")
        val expected = ByteArray(150_000) { (it * 31 + 7).toByte() }
        assertEquals(150_000L, d.plaintextLength)
        assertArrayEquals(expected, readAll(d, step = 4096))
        val rnd = Random(3)
        repeat(100) {
            val pos = rnd.nextInt(0, 150_000)
            val len = rnd.nextInt(1, 3000)
            val buf = ByteArray(len)
            val n = d.read(pos.toLong(), buf, 0, len)
            assertArrayEquals(expected.copyOfRange(pos, pos + n), buf.copyOf(n))
        }
    }

    @Test fun closedDecryptorRejectsReadsAndCloseIsIdempotent() {
        val d = open(enc(Random(19).nextBytes(500)))
        d.close()
        d.close()
        assertThrows(IllegalStateException::class.java) { d.read(0, ByteArray(1), 0, 1) }
    }

    @Test fun fileSourceRoundTrip() {
        val plain = Random(20).nextBytes(70_000)
        val f = File.createTempFile("edrive", ".edrv")
        try {
            f.writeBytes(enc(plain, chunk = 4096))
            FileCiphertextSource(f).use { src ->
                val d = RandomAccessDecryptor.open(src, dek, "f")
                assertArrayEquals(plain, readAll(d))
            }
        } finally {
            f.delete()
        }
    }
}
