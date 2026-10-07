package com.edrive.app.ui.gallery

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edrive.app.data.Session
import com.edrive.app.media.DeviceAlbum
import com.edrive.app.media.DeviceItem
import com.edrive.app.media.DeviceMedia
import com.edrive.app.media.MediaAccess
import com.edrive.app.util.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class GalleryUi(
    val access: MediaAccess = MediaAccess.NONE,
    val loading: Boolean = true,
    val items: List<DeviceItem> = emptyList(),
    val albums: List<DeviceAlbum> = emptyList(),
    /** null = bütün albomlar */
    val albumId: Long? = null,
    /** Seçilmiş elementlərin [DeviceItem.key] dəyərləri */
    val selected: Set<String> = emptySet(),
)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: Session,
) : ViewModel() {
    private val _ui = MutableStateFlow(GalleryUi(access = DeviceMedia.access(context)))
    val ui: StateFlow<GalleryUi> = _ui.asStateFlow()

    // Miniatürlər yalnız bu ekran açıq olanda RAM-da saxlanılır (16 MB limit)
    private val thumbs = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun beginExternalUi() = session.beginExternalUi()
    fun endExternalUi() = session.endExternalUi()

    /** İcazə vəziyyətini yoxlayıb siyahını yenidən oxuyur (icazə verildikdən və ya ekrana qayıdanda). */
    fun refresh() {
        val access = DeviceMedia.access(context)
        if (access == MediaAccess.NONE) {
            _ui.update { it.copy(access = access, loading = false, items = emptyList(), albums = emptyList(), selected = emptySet()) }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(access = access, loading = it.items.isEmpty()) }
            try {
                val items = withContext(Dispatchers.IO) { DeviceMedia.query(context.contentResolver) }
                _ui.update { s ->
                    val keys = items.mapTo(HashSet()) { it.key }
                    val album = s.albumId?.takeIf { id -> items.any { it.bucketId == id } }
                    s.copy(access = access, loading = false, items = items, albums = DeviceMedia.albums(items), albumId = album, selected = s.selected.filterTo(HashSet()) { it in keys })
                }
            } catch (e: Exception) {
                AppLog.e("gallery", "Qalereya oxunmadı", e)
                _ui.update { it.copy(loading = false) }
            }
        }
    }

    fun shownItems(s: GalleryUi): List<DeviceItem> = if (s.albumId == null) s.items else s.items.filter { it.bucketId == s.albumId }

    fun setAlbum(id: Long?) = _ui.update { it.copy(albumId = id) }

    fun toggle(item: DeviceItem) = _ui.update { s ->
        s.copy(selected = if (item.key in s.selected) s.selected - item.key else s.selected + item.key)
    }

    /** Göstərilən albomdakı hamısını seçir; hamısı artıq seçilibsə, seçimi ləğv edir. */
    fun selectAllShown() = _ui.update { s ->
        val keys = shownItems(s).map { it.key }
        s.copy(selected = if (keys.isNotEmpty() && s.selected.containsAll(keys)) s.selected - keys.toSet() else s.selected + keys)
    }

    fun selectedUris(): List<Uri> {
        val s = _ui.value
        return s.items.filter { it.key in s.selected }.map { DeviceMedia.importUri(context, it) }
    }

    suspend fun thumbnail(item: DeviceItem): Bitmap? {
        thumbs.get(item.key)?.let { return it }
        return withContext(Dispatchers.IO) { DeviceMedia.thumbnail(context.contentResolver, item) }?.also { thumbs.put(item.key, it) }
    }

    override fun onCleared() { thumbs.evictAll() }
}
