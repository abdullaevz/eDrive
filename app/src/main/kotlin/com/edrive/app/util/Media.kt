package com.edrive.app.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class PickedFile(val uri: Uri, val name: String, val size: Long, val mimeType: String)

object Media {

    const val THUMB_PX = 320

    fun describe(resolver: ContentResolver, uri: Uri): PickedFile {
        var name = "fayl"
        var size = -1L
        if (uri.scheme == "file") {
            val f = java.io.File(uri.path!!)
            name = f.name
            size = f.length()
        } else resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        val mime = resolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
        return PickedFile(uri, name, size, mime)
    }

    class Thumb(val jpeg: ByteArray, val width: Int, val height: Int)

    /** Şəkil və videolar üçün kiçik JPEG miniatür (EXIF istiqaməti nəzərə alınır). */
    fun thumbnail(context: Context, file: PickedFile): Thumb? = runCatching {
        when {
            file.mimeType.startsWith("image/") -> imageThumb(context.contentResolver, file.uri)
            file.mimeType.startsWith("video/") -> videoThumb(context, file.uri)
            else -> null
        }
    }.getOrNull()

    private fun imageThumb(resolver: ContentResolver, uri: Uri): Thumb? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, THUMB_PX * 2) }
        val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val orientation = resolver.openInputStream(uri)?.use { exifOrientation(ExifInterface(it)) } ?: ExifInterface.ORIENTATION_NORMAL
        val rotated = applyOrientation(bmp, orientation)
        val (w, h) = if (isSwapped(orientation)) bounds.outHeight to bounds.outWidth else bounds.outWidth to bounds.outHeight
        return Thumb(compress(scaleDown(rotated, THUMB_PX)), w, h)
    }

    private fun videoThumb(context: Context, uri: Uri): Thumb? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val frame = r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: frame.width
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: frame.height
            return Thumb(compress(scaleDown(frame, THUMB_PX)), w, h)
        } finally {
            r.release()
        }
    }

    /** Deşifrə edilmiş tam şəkli ekrana uyğun ölçüdə açır (yaddaşda, diskə yazılmadan). */
    fun decodeFull(bytes: ByteArray, maxSide: Int = 4096): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide) }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val orientation = runCatching { exifOrientation(ExifInterface(ByteArrayInputStream(bytes))) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return applyOrientation(bmp, orientation)
    }

    /** Ən uzun tərəf [target]-dən böyük olmayana qədər 2-nin qüvvəsi ilə kiçildir (8160 px → hədəf 4096 üçün 2). */
    internal fun sampleSize(w: Int, h: Int, target: Int): Int {
        var s = 1
        while (max(w, h) / s > target) s *= 2
        return s
    }

    private fun scaleDown(b: Bitmap, maxSide: Int): Bitmap {
        val m = max(b.width, b.height)
        if (m <= maxSide) return b
        val f = maxSide.toFloat() / m
        return Bitmap.createScaledBitmap(b, (b.width * f).roundToInt().coerceAtLeast(1), (b.height * f).roundToInt().coerceAtLeast(1), true)
    }

    private fun compress(b: Bitmap): ByteArray =
        ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()

    private fun exifOrientation(e: ExifInterface) =
        e.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

    private fun isSwapped(o: Int) = o in setOf(
        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270,
        ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE,
    )

    private fun applyOrientation(b: Bitmap, o: Int): Bitmap {
        val m = Matrix()
        when (o) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return b
        }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }
}

fun formatBytes(n: Long): String {
    if (n < 0) return "—"
    val u = listOf("B", "KB", "MB", "GB", "TB")
    var v = n.toDouble()
    var i = 0
    while (v >= 1024 && i < u.lastIndex) { v /= 1024; i++ }
    return if (i == 0) "$n B" else if (v < 10) "%.1f %s".format(v, u[i]) else "%.0f %s".format(v, u[i])
}
