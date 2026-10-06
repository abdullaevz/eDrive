package com.edrive.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edrive.app.data.db.entity.FileEntity
import com.edrive.app.ui.theme.EColors

// ---------------------------------------------------------------------------------------------
// Süzgəc məntiqi (təmiz Kotlin — vahid testlərlə yoxlanılır)
// ---------------------------------------------------------------------------------------------

/** Süzgəcin əsas seçimi: hamısı / yalnız şəkillər / yalnız videolar. */
enum class MediaKind { ALL, IMAGE, VIDEO }

enum class Orientation(val label: String) { ANY("Hamısı"), LANDSCAPE("Üfüqi"), PORTRAIT("Şaquli") }

enum class FileSort(val label: String) {
    NEWEST("Ən yeni"), OLDEST("Ən köhnə"), LARGEST("Ən böyük"), SMALLEST("Ən kiçik"), NAME("Ad"),
}

data class FileFilter(
    val kind: MediaKind = MediaKind.ALL,
    /** [formatLabel] nəticəsi (məs. "JPG", "MP4"); null — bütün formatlar. */
    val format: String? = null,
    val orientation: Orientation = Orientation.ANY,
    val sort: FileSort = FileSort.NEWEST,
) {
    /** Standart vəziyyətdən fərqlənirmi (sıfırla düyməsi üçün). */
    val isDefault: Boolean get() = this == FileFilter()
}

fun FileEntity.mediaKind(): MediaKind = when {
    mimeType.startsWith("image/") -> MediaKind.IMAGE
    mimeType.startsWith("video/") -> MediaKind.VIDEO
    else -> MediaKind.ALL
}

/** MIME tipindən istifadəçiyə göstərilən format adı: image/jpeg → JPG, video/quicktime → MOV və s. */
fun formatLabel(mimeType: String): String {
    val sub = mimeType.substringAfter('/', "").substringBefore(';').trim().lowercase()
    return when (sub) {
        "" -> "?"
        "jpeg", "jpg", "pjpeg" -> "JPG"
        "quicktime" -> "MOV"
        "x-matroska" -> "MKV"
        "3gpp", "3gpp2" -> "3GP"
        "x-msvideo" -> "AVI"
        "x-ms-wmv" -> "WMV"
        "x-flv" -> "FLV"
        "svg+xml" -> "SVG"
        else -> sub.removePrefix("x-").uppercase()
    }
}

/** Verilmiş növ üçün vault-da mövcud formatlar və say (çoxdan aza). */
fun availableFormats(files: List<FileEntity>, kind: MediaKind): List<Pair<String, Int>> =
    files.filter { it.mediaKind() == kind }
        .groupingBy { formatLabel(it.mimeType) }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key to it.value }

/** Ölçüləri məlum olan şəkil/video varmı (yönəliş seçimini göstərmək üçün). */
fun hasKnownOrientation(files: List<FileEntity>, kind: MediaKind): Boolean =
    files.any { it.mediaKind() == kind && it.width > 0 && it.height > 0 }

fun applyFilter(files: List<FileEntity>, f: FileFilter): List<FileEntity> {
    if (f.isDefault) return files
    var out = if (f.kind == MediaKind.ALL) files else files.filter { it.mediaKind() == f.kind }
    f.format?.let { fmt -> out = out.filter { formatLabel(it.mimeType) == fmt } }
    out = when (f.orientation) {
        Orientation.ANY -> out
        Orientation.LANDSCAPE -> out.filter { it.width > 0 && it.height > 0 && it.width >= it.height }
        Orientation.PORTRAIT -> out.filter { it.width > 0 && it.height > it.width }
    }
    return when (f.sort) {
        FileSort.NEWEST -> out.sortedByDescending { it.createdAt }
        FileSort.OLDEST -> out.sortedBy { it.createdAt }
        FileSort.LARGEST -> out.sortedByDescending { it.size }
        FileSort.SMALLEST -> out.sortedBy { it.size }
        FileSort.NAME -> out.sortedWith { a, b -> a.name.compareTo(b.name, ignoreCase = true) }
    }
}

/** Ekran çevriləndə süzgəcin saxlanması üçün. */
val FileFilterSaver = listSaver<FileFilter, Any>(
    save = { listOf(it.kind.name, it.format.orEmpty(), it.orientation.name, it.sort.name) },
    restore = {
        FileFilter(
            kind = MediaKind.valueOf(it[0] as String),
            format = (it[1] as String).ifEmpty { null },
            orientation = Orientation.valueOf(it[2] as String),
            sort = FileSort.valueOf(it[3] as String),
        )
    },
)

// ---------------------------------------------------------------------------------------------
// Görünüş
// ---------------------------------------------------------------------------------------------

/**
 * Ən başdakı süzgəc paneli: yan-yana iki düymə (Şəkil / Video). Biri seçiləndə ona uyğun seçimlər açılır:
 * format, yönəliş və sıralama. Eyni düyməyə yenidən toxunmaq süzgəci söndürür.
 */
@Composable
fun FilterBar(
    filter: FileFilter,
    files: List<FileEntity>,
    shownCount: Int,
    onChange: (FileFilter) -> Unit,
) {
    val imageCount = remember(files) { files.count { it.mediaKind() == MediaKind.IMAGE } }
    val videoCount = remember(files) { files.count { it.mediaKind() == MediaKind.VIDEO } }

    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KindButton(
                Modifier.weight(1f), Icons.Outlined.Image, "Şəkil", imageCount, filter.kind == MediaKind.IMAGE,
            ) { onChange(if (filter.kind == MediaKind.IMAGE) FileFilter() else FileFilter(kind = MediaKind.IMAGE)) }
            KindButton(
                Modifier.weight(1f), Icons.Outlined.Videocam, "Video", videoCount, filter.kind == MediaKind.VIDEO,
            ) { onChange(if (filter.kind == MediaKind.VIDEO) FileFilter() else FileFilter(kind = MediaKind.VIDEO)) }
        }

        AnimatedVisibility(visible = filter.kind != MediaKind.ALL, enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val formats = remember(files, filter.kind) { availableFormats(files, filter.kind) }
                if (formats.size > 1) {
                    OptionRow("Format") {
                        Chip("Hamısı", filter.format == null) { onChange(filter.copy(format = null)) }
                        formats.forEach { (fmt, count) ->
                            Chip("$fmt · $count", filter.format == fmt) { onChange(filter.copy(format = fmt)) }
                        }
                    }
                }
                if (hasKnownOrientation(files, filter.kind)) {
                    OptionRow("Yönəliş") {
                        Orientation.entries.forEach { o ->
                            Chip(o.label, filter.orientation == o) { onChange(filter.copy(orientation = o)) }
                        }
                    }
                }
                OptionRow("Sıralama") {
                    FileSort.entries.forEach { s ->
                        Chip(s.label, filter.sort == s) { onChange(filter.copy(sort = s)) }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("$shownCount nəticə", color = EColors.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    if (!filter.isDefault) {
                        Text(
                            "Sıfırla", color = EColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onChange(FileFilter()) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KindButton(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.clip(shape).clickable(onClick = onClick)
            .background(if (selected) Color(0x1A3DDC97) else EColors.Surface)
            .border(1.dp, if (selected) EColors.Accent else EColors.Line, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (selected) EColors.Accent else EColors.Muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            color = if (selected) EColors.Accent else Color.White,
        )
        Spacer(Modifier.width(6.dp))
        Text("$count", fontSize = 12.sp, color = EColors.Faint)
    }
}

@Composable
private fun OptionRow(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, color = EColors.Faint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) EColors.Accent else EColors.Muted,
        modifier = Modifier.clip(shape).clickable(onClick = onClick)
            .background(if (selected) Color(0x1A3DDC97) else EColors.Surface)
            .border(1.dp, if (selected) EColors.Accent else EColors.Line, shape)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}
