package com.edrive.app.data.vault

import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap

/** Deşifrə olunmuş miniatürlərin yaddaşdakı (RAM) keşi. Vault kilidlənəndə tam təmizlənir. */
class ThumbnailCache(maxBytes: Int = 24 * 1024 * 1024) {
    private val cache = object : LruCache<String, ImageBitmap>(maxBytes) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun get(id: String): ImageBitmap? = cache.get(id)
    fun put(id: String, bitmap: ImageBitmap) { cache.put(id, bitmap) }
    fun remove(id: String) { cache.remove(id) }
    fun clear() = cache.evictAll()
}
