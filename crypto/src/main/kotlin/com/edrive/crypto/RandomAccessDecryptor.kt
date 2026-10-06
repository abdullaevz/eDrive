package com.edrive.crypto

import com.edrive.crypto.CryptoConstants.AAD_FILE_KEY
import com.edrive.crypto.CryptoConstants.AES_GCM
import com.edrive.crypto.CryptoConstants.GCM_TAG_BITS
import com.edrive.crypto.CryptoConstants.GCM_TAG_BYTES
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Şifrəli faylın baytlarına ixtiyari mövqedən oxuma girişi (fayl, yaddaş, və s.). */
interface CiphertextSource : AutoCloseable {
    val length: Long

    /** [position]-dan başlayaraq tam [len] bayt oxuyur; mənbə qısadırsa [EOFException] atır. */
    fun readFully(position: Long, buffer: ByteArray, offset: Int, len: Int)
}

/** Diskdəki şifrəli fayl (`.edrv`). Mövqeli oxuma thread-safe-dir. */
class FileCiphertextSource(file: File) : CiphertextSource {
    private val raf = RandomAccessFile(file, "r")
    private val channel: FileChannel = raf.channel
    override val length: Long = channel.size()

    override fun readFully(position: Long, buffer: ByteArray, offset: Int, len: Int) {
        val bb = ByteBuffer.wrap(buffer, offset, len)
        var pos = position
        while (bb.hasRemaining()) {
            val n = channel.read(bb, pos)
            if (n < 0) throw EOFException("Unexpected end of ciphertext at $pos")
            pos += n
        }
    }

    override fun close() = raf.close()
}

/**
 * `.edrv` faylından ixtiyari mövqedən açıq mətn oxuyur (video axtarışı üçün).
 *
 * Format DƏYİŞMİR — [StreamingCipher] ilə yazılmış hər fayl açılır (Spring Boot-dan gələnlər də).
 * Chunk-ların nonce-u yalnız indeksdən və "sonuncu" bayrağından asılıdır, ona görə lazım olan chunk
 * birbaşa tapılıb deşifrə olunur. "Sonuncu" bayrağı şifrəli faylın ölçüsündən hesablanır:
 * fayl kəsilibsə və ya sonuna əlavə yazılıbsa, uyğun chunk autentifikasiyadan keçmir.
 *
 * Yalnız autentifikasiyadan keçmiş chunk-lar qaytarılır. Son oxunan bir neçə açıq mətn chunk-ı yaddaşda
 * saxlanılır ([close] zamanı sıfırlanır). Bütün metodlar thread-safe-dir.
 */
class RandomAccessDecryptor private constructor(
    private val source: CiphertextSource,
    private val header: StreamingCipher.Header,
    private val keySpec: SecretKeySpec,
    private val cacheChunks: Int,
) : AutoCloseable {

    private val chunkSize = header.chunkSize
    private val encChunk = chunkSize + GCM_TAG_BYTES

    /** Chunk sayı (boş fayl da 1 chunk-dır). */
    val chunkCount: Long

    /** Açıq mətnin ölçüsü (bayt). */
    val plaintextLength: Long

    private val lastChunkLen: Int
    private val cache = LinkedHashMap<Int, ByteArray>(16, 0.75f, true)
    private var closed = false

    init {
        val body = source.length - StreamingCipher.HEADER_BYTES
        if (body < GCM_TAG_BYTES) throw AuthenticationFailedException("File truncated: no chunks")
        val count = (body + encChunk - 1) / encChunk
        val last = body - (count - 1) * encChunk
        if (last < GCM_TAG_BYTES) throw AuthenticationFailedException("File truncated inside the last chunk")
        if (count > Int.MAX_VALUE) throw CryptoException("File too large")
        chunkCount = count
        lastChunkLen = last.toInt()
        plaintextLength = body - count * GCM_TAG_BYTES
    }

    /**
     * [position] mövqeyindən ən çoxu [length] açıq mətn bayt oxuyub [dst]-yə yazır.
     * @return oxunan bayt sayı; fayl sonundadırsa -1; [length] == 0 olarsa 0
     * @throws AuthenticationFailedException chunk dəyişdirilibsə/kəsilibsə
     */
    @Synchronized
    fun read(position: Long, dst: ByteArray, offset: Int, length: Int): Int {
        check(!closed) { "Decryptor closed" }
        require(position >= 0) { "negative position" }
        if (length == 0) return 0
        if (position >= plaintextLength) return -1
        var pos = position
        var out = offset
        var remaining = minOf(length.toLong(), plaintextLength - position).toInt()
        var total = 0
        while (remaining > 0) {
            val index = (pos / chunkSize).toInt()
            val chunk = chunk(index)
            val inChunk = (pos - index.toLong() * chunkSize).toInt()
            val n = minOf(remaining, chunk.size - inChunk)
            System.arraycopy(chunk, inChunk, dst, out, n)
            pos += n
            out += n
            remaining -= n
            total += n
        }
        return total
    }

    private fun chunk(index: Int): ByteArray {
        cache[index]?.let { return it }
        val last = index.toLong() == chunkCount - 1
        val start = StreamingCipher.HEADER_BYTES + index.toLong() * encChunk
        val len = if (last) lastChunkLen else encChunk
        val ct = ByteArray(len)
        source.readFully(start, ct, 0, len)
        val pt = try {
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(
                Cipher.DECRYPT_MODE, keySpec,
                GCMParameterSpec(GCM_TAG_BITS, StreamingCipher.chunkNonce(header.noncePrefix, index, last)),
            )
            cipher.updateAAD(header.raw)
            cipher.doFinal(ct)
        } catch (e: AEADBadTagException) {
            throw AuthenticationFailedException("Chunk $index failed authentication (tampered or truncated)", e)
        } catch (e: GeneralSecurityException) {
            throw CryptoException("Chunk decryption failed", e)
        }
        cache[index] = pt
        while (cache.size > cacheChunks) {
            val eldest = cache.keys.first()
            cache.remove(eldest)?.wipe()
        }
        return pt
    }

    /** Keşi sıfırlayır və mənbəni bağlayır. Təkrar çağırmaq təhlükəsizdir. */
    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        cache.values.forEach { it.wipe() }
        cache.clear()
        runCatching { source.close() }
    }

    companion object {
        /**
         * Başlığı oxuyur və faylın açarını DEK ilə açır.
         * Uğursuz olarsa [source] bağlanmır — onu çağıran tərəf bağlamalıdır.
         *
         * @throws AuthenticationFailedException açar/ID yanlışdırsa və ya fayl kəsilibsə
         * @throws CryptoException eDrive faylı deyilsə
         */
        fun open(source: CiphertextSource, dek: ByteArray, fileId: String, cacheChunks: Int = 16): RandomAccessDecryptor {
            require(cacheChunks >= 1) { "cacheChunks must be >= 1" }
            val raw = ByteArray(StreamingCipher.HEADER_BYTES)
            try {
                source.readFully(0, raw, 0, raw.size)
            } catch (e: EOFException) {
                throw CryptoException("Not an eDrive file: header too short")
            }
            val h = StreamingCipher.readHeader(ByteArrayInputStream(raw))
            val fileKey = AesGcm.open(dek, h.wrappedFileKey, AAD_FILE_KEY + fileId)
            try {
                return RandomAccessDecryptor(source, h, SecretKeySpec(fileKey, "AES"), cacheChunks)
            } finally {
                fileKey.wipe()
            }
        }
    }
}
