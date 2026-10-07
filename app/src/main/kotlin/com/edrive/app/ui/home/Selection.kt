package com.edrive.app.ui.home

import com.edrive.app.data.db.entity.FileEntity
import com.edrive.app.data.db.entity.FileStatus

/** Çoxlu seçim rejimi: [active] = rejim açıqdır, [ids] = seçilmiş faylların id-ləri. */
data class SelectionState(val active: Boolean = false, val ids: Set<String> = emptySet())

/** Toplu əməliyyatın gedişi (ekranın altındakı panel üçün). */
data class BatchProgress(val label: String, val done: Int, val total: Int, val current: Float = 0f) {
    val fraction: Float get() = if (total <= 0) 0f else ((done + current.coerceIn(0f, 1f)) / total).coerceIn(0f, 1f)
}

/** Seçim rejiminin ekran əməliyyatları. Defolt dəyərlər boşdur ki, ekran testləri dəyişməsin. */
class SelectionActions(
    val onToggle: (String) -> Unit = {},
    val onStart: () -> Unit = {},
    val onSelectAll: (List<String>) -> Unit = {},
    val onClear: () -> Unit = {},
    val onDownload: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onInfo: () -> Unit = {},
)

/** Faylın tam şifrəli nüsxəsi əlçatandır (Drive-da, növbədə və ya yalnız cihazda) — açmaq/endirmək olar. */
fun FileEntity.isReady(): Boolean =
    status == FileStatus.SYNCED || status == FileStatus.PENDING || status == FileStatus.LOCAL

/** Cihaza endirilə bilən fayllar. */
fun List<FileEntity>.exportable(ids: Set<String>): List<FileEntity> = filter { it.id in ids && it.isReady() }

/**
 * Toplu "sil" yalnız Drive-dan silir (şifrəli nüsxə cihazda qalır). Bunun üçün fayl Drive-da olmalıdır:
 * yüklənməkdə/şifrələnməkdə, xətalı və artıq yalnız cihazda olan fayllar keçilir.
 */
fun List<FileEntity>.driveRemovable(ids: Set<String>): List<FileEntity> =
    filter { it.id in ids && it.status == FileStatus.SYNCED }

fun FileEntity.isMedia(): Boolean = mimeType.startsWith("image/") || mimeType.startsWith("video/")

/** Qalereyaya yazıla bilməyən (qovluq seçmək lazım olan) fayl varmı. */
fun needsFolder(files: List<FileEntity>, sdkInt: Int): Boolean =
    files.any { !it.isMedia() || sdkInt < 29 }

/** Hamısı seçilibsə seçimi təmizləyir, yoxsa hamısını seçir. */
fun toggleAll(current: Set<String>, all: List<String>): Set<String> =
    if (all.isNotEmpty() && current.containsAll(all)) emptySet() else all.toSet()
