package com.edrive.crypto

import com.edrive.crypto.CryptoConstants.AAD_FILE_KEY
import com.edrive.crypto.CryptoConstants.AES_GCM
import com.edrive.crypto.CryptoConstants.DEFAULT_CHUNK_SIZE
import com.edrive.crypto.CryptoConstants.FILE_FORMAT_VERSION
import com.edrive.crypto.CryptoConstants.FILE_MAGIC
import com.edrive.crypto.CryptoConstants.GCM_NONCE_BYTES
import com.edrive.crypto.CryptoConstants.GCM_TAG_BITS
import com.edrive.crypto.CryptoConstants.GCM_TAG_BYTES
import com.edrive.crypto.CryptoConstants.KEY_BYTES
import com.edrive.crypto.CryptoConstants.NONCE_PREFIX_BYTES
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.MessageDigest
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Böyük faylları yaddaşa tam yükləmədən AES-256-GCM ilə şifrələyən axın şifrəsi (STREAM konstruksiyası).
 *
 * Format (.edrv v1) Spring Boot versiyası ilə bayt-bayt eynidir:
 * ```
 *   magic "EDRV"(4) | version(1) | chunkSize(4) | wrappedFileKey(60) | noncePrefix(7)   = 76 bayt başlıq
 *   chunk[0..n]: (chunkSize + 16) bayt, sonuncu qısa ola bilər
 * ```
 * Chunk nonce = prefix(7) || index(4, BE) || final(1); hər chunk-ın AAD-ı tam başlıqdır.
 * Bit dəyişməsi, chunk yerdəyişməsi və faylın kəsilməsi aşkarlanır.
 */
object StreamingCipher {

    const val WRAPPED_KEY_BYTES = GCM_NONCE_BYTES + KEY_BYTES + GCM_TAG_BYTES
    val HEADER_BYTES = FILE_MAGIC.size + 1 + 4 + WRAPPED_KEY_BYTES + NONCE_PREFIX_BYTES

    class Header(val version: Byte, val chunkSize: Int, val wrappedFileKey: ByteArray, val noncePrefix: ByteArray, val raw: ByteArray)

    data class EncryptResult(val plaintextBytes: Long, val ciphertextBytes: Long, val chunks: Long, val sha256: String)

    /** Şifrəli faylın ölçüsü, açıq mətnin ölçüsünə görə. */
    fun ciphertextSize(plaintextBytes: Long, chunkSize: Int = DEFAULT_CHUNK_SIZE): Long {
        val chunks = if (plaintextBytes == 0L) 1 else (plaintextBytes + chunkSize - 1) / chunkSize
        return HEADER_BYTES + plaintextBytes + chunks * GCM_TAG_BYTES
    }

    fun encrypt(
        input: InputStream,
        output: OutputStream,
        dek: ByteArray,
        fileId: String,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        onProgress: (plaintextBytes: Long) -> Unit = {},
    ): EncryptResult {
        val fileKey = AesGcm.newKey()
        try {
            val wrapped = AesGcm.seal(dek, fileKey, AAD_FILE_KEY + fileId)
            val prefix = AesGcm.randomBytes(NONCE_PREFIX_BYTES)
            val header = ByteBuffer.allocate(HEADER_BYTES)
                .put(FILE_MAGIC).put(FILE_FORMAT_VERSION).putInt(chunkSize)
                .put(wrapped).put(prefix)
                .array()
            output.write(header)

            val sha = MessageDigest.getInstance("SHA-256")
            val cipher = Cipher.getInstance(AES_GCM)
            val keySpec = SecretKeySpec(fileKey, "AES")

            var plain = 0L
            var cipherBytes = header.size.toLong()
            var index = 0
            var current = input.readNBytesCompat(chunkSize)
            while (true) {
                var next: ByteArray? = null
                val last = if (current.size < chunkSize) true else {
                    next = input.readNBytesCompat(chunkSize)
                    next.isEmpty()
                }
                sha.update(current)
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, chunkNonce(prefix, index, last)))
                cipher.updateAAD(header)
                val ct = cipher.doFinal(current)
                output.write(ct)
                plain += current.size
                cipherBytes += ct.size
                index++
                onProgress(plain)
                if (last) break
                current = next!!
            }
            output.flush()
            return EncryptResult(plain, cipherBytes, index.toLong(), sha.digest().toHex())
        } catch (e: GeneralSecurityException) {
            throw CryptoException("Stream encryption failed", e)
        } finally {
            fileKey.wipe()
        }
    }

    /** Yalnız autentifikasiyadan keçmiş chunk-lar çıxışa yazılır. */
    fun decrypt(
        input: InputStream,
        output: OutputStream,
        dek: ByteArray,
        fileId: String,
        onProgress: (plaintextBytes: Long) -> Unit = {},
    ): Long {
        val h = readHeader(input)
        val fileKey = AesGcm.open(dek, h.wrappedFileKey, AAD_FILE_KEY + fileId)
        try {
            val cipher = Cipher.getInstance(AES_GCM)
            val keySpec = SecretKeySpec(fileKey, "AES")
            val encChunk = h.chunkSize + GCM_TAG_BYTES
            var plain = 0L
            var index = 0
            var current = input.readNBytesCompat(encChunk)
            while (true) {
                if (current.size < GCM_TAG_BYTES) throw AuthenticationFailedException("File truncated at chunk $index")
                var next: ByteArray? = null
                val last = if (current.size < encChunk) true else {
                    next = input.readNBytesCompat(encChunk)
                    next.isEmpty()
                }
                cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, chunkNonce(h.noncePrefix, index, last)))
                cipher.updateAAD(h.raw)
                val pt = try {
                    cipher.doFinal(current)
                } catch (e: AEADBadTagException) {
                    throw AuthenticationFailedException("Chunk $index failed authentication (tampered or truncated)", e)
                }
                output.write(pt)
                plain += pt.size
                index++
                onProgress(plain)
                if (last) break
                current = next!!
            }
            output.flush()
            return plain
        } catch (e: GeneralSecurityException) {
            throw CryptoException("Stream decryption failed", e)
        } finally {
            fileKey.wipe()
        }
    }

    fun readHeader(input: InputStream): Header {
        val raw = ByteArray(HEADER_BYTES)
        try {
            DataInputStream(input).readFully(raw)
        } catch (e: EOFException) {
            throw CryptoException("Not an eDrive file: header too short")
        }
        val bb = ByteBuffer.wrap(raw)
        val magic = ByteArray(FILE_MAGIC.size).also { bb.get(it) }
        if (!magic.contentEquals(FILE_MAGIC)) throw CryptoException("Not an eDrive file: bad magic")
        val version = bb.get()
        if (version != FILE_FORMAT_VERSION) throw CryptoException("Unsupported file format version $version")
        val chunkSize = bb.int
        if (chunkSize !in 1024..16 * 1024 * 1024) throw CryptoException("Invalid chunk size $chunkSize")
        val wrapped = ByteArray(WRAPPED_KEY_BYTES).also { bb.get(it) }
        val prefix = ByteArray(NONCE_PREFIX_BYTES).also { bb.get(it) }
        return Header(version, chunkSize, wrapped, prefix, raw)
    }

    internal fun chunkNonce(prefix: ByteArray, index: Int, last: Boolean): ByteArray =
        ByteBuffer.allocate(GCM_NONCE_BYTES).put(prefix).putInt(index).put(if (last) 1 else 0).array()

    /** InputStream.readNBytes Android API 33+ tələb edir — minSdk 26 üçün öz versiyamız. */
    private fun InputStream.readNBytesCompat(n: Int): ByteArray {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = read(buf, off, n - off)
            if (r < 0) break
            off += r
        }
        return if (off == n) buf else buf.copyOf(off)
    }
}
