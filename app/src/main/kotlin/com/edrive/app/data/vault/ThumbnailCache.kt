package com.edrive.app.data.vault

import javax.inject.Inject
import javax.inject.Singleton
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap

/** Deşifrə olunmuş miniatürlərin yaddaşdakı (RAM) keşi. Vault kilidlənəndə tam təmizlənir. */
@Singleton
class ThumbnailCache @Inject constructor() {
    private val cache = object : LruCache<String, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun get(id: String): ImageBitmap? = cache.get(id)
    fun put(id: String, bitmap: ImageBitmap) { cache.put(id, bitmap) }
    fun remove(id: String) { cache.remove(id) }
    fun clear() = cache.evictAll()

    private companion object {
        const val MAX_BYTES = 24 * 1024 * 1024
    }
}
