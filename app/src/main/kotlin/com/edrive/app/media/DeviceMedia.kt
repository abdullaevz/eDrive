package com.edrive.app.media

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.ContextCompat

/** Telefondakı bir şəkil/video (MediaStore-dan). */
data class DeviceItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mime: String,
    val size: Long,
    val dateSec: Long,
    val bucketId: Long,
    val bucket: String,
    val isVideo: Boolean,
) {
    val key: String get() = uri.toString()
}

data class DeviceAlbum(val id: Long, val name: String, val count: Int)

enum class MediaAccess { NONE, PARTIAL, FULL }

/** Telefondakı bütün şəkil və videolara (albomlar üzrə) giriş — Android icazəsi ilə. */
object DeviceMedia {

    /** İstənilməli icazələr (Android versiyasına görə). */
    fun permissions(): Array<String> = buildList {
        when {
            Build.VERSION.SDK_INT >= 34 -> {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
            Build.VERSION.SDK_INT >= 33 -> {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            else -> add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        // Şəkillərin GPS/EXIF məlumatı da olduğu kimi qalsın (yoxsa sistem onu silir və fayl orijinaldan fərqlənir)
        if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }.toTypedArray()

    private fun granted(c: Context, p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

    fun access(c: Context): MediaAccess = when {
        Build.VERSION.SDK_INT >= 33 ->
            if (granted(c, Manifest.permission.READ_MEDIA_IMAGES) && granted(c, Manifest.permission.READ_MEDIA_VIDEO)) MediaAccess.FULL
            else if (Build.VERSION.SDK_INT >= 34 && granted(c, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) MediaAccess.PARTIAL
            else MediaAccess.NONE
        else -> if (granted(c, Manifest.permission.READ_EXTERNAL_STORAGE)) MediaAccess.FULL else MediaAccess.NONE
    }

    fun hasLocationAccess(c: Context): Boolean =
        Build.VERSION.SDK_INT >= 29 && granted(c, Manifest.permission.ACCESS_MEDIA_LOCATION)

    /** Bütün şəkil və videolar, ən yenisi birinci. */
    fun query(resolver: ContentResolver): List<DeviceItem> {
        val out = ArrayList<DeviceItem>()
        val cols = arrayOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
        )
        val sel = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        resolver.query(MediaStore.Files.getContentUri("external"), cols, sel, args, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val video = c.getInt(7) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                out += DeviceItem(
                    id = id,
                    uri = ContentUris.withAppendedId(base, id),
                    name = c.getString(1) ?: "fayl",
                    mime = c.getString(2) ?: if (video) "video/*" else "image/*",
                    size = if (c.isNull(3)) -1L else c.getLong(3),
                    dateSec = c.getLong(4),
                    bucketId = if (c.isNull(5)) 0L else c.getLong(5),
                    bucket = c.getString(6) ?: "Digər",
                    isVideo = video,
                )
            }
        }
        return out
    }

    fun albums(items: List<DeviceItem>): List<DeviceAlbum> =
        items.groupBy { it.bucketId }
            .map { (id, list) -> DeviceAlbum(id, list.first().bucket, list.size) }
            .sortedWith(compareByDescending<DeviceAlbum> { it.count }.thenBy { it.name.lowercase() })

    /** Miniatür (bloklayır — IO axınında çağırın). */
    @Suppress("DEPRECATION")
    fun thumbnail(resolver: ContentResolver, item: DeviceItem, px: Int = 256): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 29) resolver.loadThumbnail(item.uri, Size(px, px), null)
        else if (item.isVideo) MediaStore.Video.Thumbnails.getThumbnail(resolver, item.id, MediaStore.Video.Thumbnails.MINI_KIND, null)
        else MediaStore.Images.Thumbnails.getThumbnail(resolver, item.id, MediaStore.Images.Thumbnails.MINI_KIND, null)
    }.getOrNull()

    /** Yükləmə üçün URI: yer icazəsi varsa, orijinal (GPS-i silinməmiş) baytlar oxunsun. */
    fun importUri(c: Context, item: DeviceItem): Uri =
        if (!item.isVideo && hasLocationAccess(c) && Build.VERSION.SDK_INT >= 29) MediaStore.setRequireOriginal(item.uri) else item.uri
}
