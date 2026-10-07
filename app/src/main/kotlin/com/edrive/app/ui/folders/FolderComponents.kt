package com.edrive.app.ui.folders

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edrive.app.data.db.entity.FolderEntity
import com.edrive.app.data.vault.FolderService
import com.edrive.app.data.vault.FolderService.DeleteMode
import com.edrive.app.drive.DriveLayout
import com.edrive.app.ui.components.EField
import com.edrive.app.ui.theme.EColors

/** Fayl şəbəkəsində qovluq xanası: toxunuş — açır, uzun basma — ad dəyişmə / silmə. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderTile(folder: FolderEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(EColors.Surface)
            .border(1.dp, EColors.Line, RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Folder, null, tint = EColors.Amber, modifier = Modifier.size(40.dp))
        Text(
            folder.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Yol: eDrive Storage › A › B. Hər hissəyə toxunmaq həmin qovluğa qaytarır. */
@Composable
fun Breadcrumb(path: List<FolderEntity>, onOpen: (String?) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        Crumb(DriveLayout.ROOT_FOLDER, active = path.isEmpty()) { onOpen(null) }
        path.forEachIndexed { i, f ->
            Icon(Icons.Outlined.ChevronRight, null, tint = EColors.Faint, modifier = Modifier.size(16.dp))
            Crumb(f.name, active = i == path.lastIndex) { onOpen(f.id) }
        }
    }
}

@Composable
private fun Crumb(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text, fontSize = 13.sp, maxLines = 1,
        color = if (active) EColors.Text else EColors.Muted, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

/** Uzun basma menyusu. */
@Composable
fun FolderActionsDialog(folder: FolderEntity, onRename: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EColors.Bg2,
        icon = { Icon(Icons.Outlined.Folder, null, tint = EColors.Amber) },
        title = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                MenuRow(Icons.Outlined.DriveFileRenameOutline, "Adını dəyiş", EColors.Text, onRename)
                MenuRow(Icons.Outlined.Delete, "Sil", EColors.Danger, onDelete)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Bağla", color = EColors.Muted) } },
    )
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint)
        Text(text, color = tint, modifier = Modifier.padding(start = 14.dp))
    }
}

/** Yeni qovluq və ya ad dəyişmə. Ad Drive-da açıq mətn olduğu üçün xəbərdarlıq göstərilir. */
@Composable
fun FolderNameDialog(title: String, initial: String, busy: Boolean, error: String?, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = EColors.Bg2,
        icon = { Icon(Icons.Outlined.CreateNewFolder, null, tint = EColors.Accent) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EField(name, { name = it.take(FolderService.MAX_NAME) }, "Qovluq adı", Icons.Outlined.Folder, enabled = !busy, isError = error != null)
                Text("Qovluq adı Google Drive-da şifrəsiz görünür — həssas ad yazmayın. Fayl adları və məzmun şifrəli qalır.", color = EColors.Faint, fontSize = 12.sp)
                if (error != null) Text(error, color = EColors.Danger, fontSize = 13.sp)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = EColors.Accent)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = !busy && name.isNotBlank()) { Text("Saxla", color = EColors.Accent) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Ləğv et", color = EColors.Muted) } },
    )
}

/** Silmə: boş qovluq birbaşa, dolu qovluq üçün iki seçim — içindəkiləri üst qovluğa köçürmək və ya birlikdə silmək. */
@Composable
fun DeleteFolderDialog(
    folder: FolderEntity,
    contents: FolderService.Contents,
    busy: Boolean,
    error: String?,
    onDelete: (DeleteMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(if (contents.isEmpty) DeleteMode.EMPTY_ONLY else DeleteMode.MOVE_UP) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = EColors.Bg2,
        icon = { Icon(Icons.Outlined.Delete, null, tint = EColors.Danger) },
        title = { Text("\"${folder.name}\" silinsin?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (contents.isEmpty) {
                    Text("Qovluq boşdur.", color = EColors.Muted)
                } else {
                    Text("İçində ${contents.files} fayl və ${contents.folders} qovluq var.", color = EColors.Muted)
                    Choice("İçindəkiləri üst qovluğa köçür, qovluğu sil", mode == DeleteMode.MOVE_UP) { mode = DeleteMode.MOVE_UP }
                    Choice("İçindəkilərlə birlikdə sil", mode == DeleteMode.WITH_CONTENTS) { mode = DeleteMode.WITH_CONTENTS }
                    if (mode == DeleteMode.WITH_CONTENTS) {
                        Text(
                            "Fayllar Drive-ın zibil qutusuna gedir (30 gün ərzində Drive-da bərpa oluna bilər)." +
                                if (contents.unsynced > 0) " ${contents.unsynced} fayl hələ Drive-a yüklənməyib — o fayllar itəcək." else "",
                            color = EColors.Danger, fontSize = 13.sp,
                        )
                    }
                }
                if (error != null) Text(error, color = EColors.Danger, fontSize = 13.sp)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = EColors.Accent)
            }
        },
        confirmButton = { TextButton(onClick = { onDelete(mode) }, enabled = !busy) { Text("Sil", color = EColors.Danger) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Ləğv et", color = EColors.Muted) } },
    )
}

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).selectable(selected, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick, colors = RadioButtonDefaults.colors(selectedColor = EColors.Accent))
        Text(text, fontSize = 14.sp)
    }
}

/** Faylları köçürmək üçün hədəf qovluq seçimi (bütün ağac, girintili). */
@Composable
fun MoveToFolderDialog(all: List<FolderEntity>, count: Int, busy: Boolean, error: String?, onMove: (String?) -> Unit, onDismiss: () -> Unit) {
    val rows = remember(all) { flatten(all) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = EColors.Bg2,
        icon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null, tint = EColors.Accent) },
        title = { Text("$count fayl hara köçürülsün?") },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item { Target(Icons.Outlined.Home, DriveLayout.ROOT_FOLDER, 0, enabled = !busy) { onMove(null) } }
                    items(rows, key = { it.first.id }) { (f, depth) ->
                        Target(Icons.Outlined.Folder, f.name, depth, enabled = !busy) { onMove(f.id) }
                    }
                }
                if (error != null) Text(error, color = EColors.Danger, fontSize = 13.sp)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = EColors.Accent)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Ləğv et", color = EColors.Muted) } },
    )
}

@Composable
private fun Target(icon: androidx.compose.ui.graphics.vector.ImageVector, name: String, depth: Int, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = onClick)
            .padding(start = (8 + depth * 18).dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (depth == 0 && icon == Icons.Outlined.Home) EColors.Accent else EColors.Amber, modifier = Modifier.size(20.dp))
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
    }
}

/** Ağacı dərinliklə birlikdə düz siyahıya çevirir (valideyn → uşaqları, adla sıralı). */
internal fun flatten(all: List<FolderEntity>): List<Pair<FolderEntity, Int>> {
    val children = all.groupBy { it.parentId }.mapValues { (_, v) -> v.sortedBy { it.name.lowercase() } }
    val out = mutableListOf<Pair<FolderEntity, Int>>()
    fun walk(parent: String?, depth: Int) {
        for (f in children[parent].orEmpty()) {
            out += f to depth + 1
            walk(f.id, depth + 1)
        }
    }
    walk(null, 0)
    return out
}
