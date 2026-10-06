@file:OptIn(UnstableApi::class)

package com.edrive.app.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import com.edrive.crypto.CryptoException
import com.edrive.crypto.RandomAccessDecryptor
import java.io.IOException

/**
 * ExoPlayer üçün mənbə: şifrəli `.edrv` faylını yaddaşda, ixtiyari mövqedən (axtarış dəstəklənir) deşifrə edir.
 * Açıq mətn heç vaxt diskə yazılmır. Oxuyucunun ömrünü ([RandomAccessDecryptor.close]) çağıran tərəf idarə edir.
 */
class EncryptedDataSource(private val decryptor: RandomAccessDecryptor) : BaseDataSource(/* isNetwork = */ false) {
    private var uri: Uri? = null
    private var position = 0L
    private var bytesRemaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val total = decryptor.plaintextLength
        if (dataSpec.position > total) {
            throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        }
        position = dataSpec.position
        val available = total - position
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) available else minOf(dataSpec.length, available)
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val toRead = minOf(length.toLong(), bytesRemaining).toInt()
        val n = try {
            decryptor.read(position, buffer, offset, toRead)
        } catch (e: CryptoException) {
            throw IOException(e)
        } catch (e: IllegalStateException) {
            throw IOException("Vault bağlandı", e)
        }
        if (n < 0) return C.RESULT_END_OF_INPUT
        position += n
        bytesRemaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    companion object {
        /** Şərti ünvan: faktiki məlumat [RandomAccessDecryptor]-dan gəlir, şəbəkəyə çıxış yoxdur. */
        val URI: Uri = Uri.parse("edrive://encrypted-video")
    }
}
