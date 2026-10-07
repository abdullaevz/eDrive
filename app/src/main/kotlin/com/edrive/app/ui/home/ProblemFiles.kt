package com.edrive.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edrive.app.data.db.entity.FileEntity
import com.edrive.app.data.db.entity.FileStatus
import com.edrive.app.data.vault.LocalVaultStore
import com.edrive.app.ui.theme.EColors

/** Diqqət tələb edən fayllar: yükləmə xətası və ya xəta ilə təkrar cəhd gözləyənlər. */
fun List<FileEntity>.problems(): List<FileEntity> =
    filter { it.status == FileStatus.FAILED || (it.status == FileStatus.PENDING && it.error != null) }

/** Lokal şifrəli nüsxə yoxdursa (daimi xəta), nə yenidən cəhd, nə də saxlamaq mümkündür — yalnız silmək. */
private val FileEntity.hasLocalCopy: Boolean get() = error?.startsWith(LocalVaultStore.PERMANENT) != true

/** Problem faylların siyahısı və hərəkətlər (bildiriş nişanına basanda açılır). */
@Composable
fun ProblemFilesSheet(
    problems: List<FileEntity>,
    onRetry: (String) -> Unit,
    onRetryAll: () -> Unit,
    onSaveCopy: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var deleting by remember { mutableStateOf<FileEntity?>(null) }
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WarningAmber, null, tint = EColors.Amber)
            Text("Problemli fayllar (${problems.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp).weight(1f))
        }
        Text("Bu fayllar hələ Google Drive-a çatmayıb — yalnız bu telefondadır.", color = EColors.Muted, fontSize = 13.sp)
        if (problems.any { it.hasLocalCopy }) {
            OutlinedButton(onClick = onRetryAll, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Refresh, null, tint = EColors.Accent)
                Text("Hamısını yenidən cəhd et", color = EColors.Text, modifier = Modifier.padding(start = 8.dp))
            }
        }
        LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(problems, key = { it.id }) { f ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(EColors.Surface).padding(start = 14.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(f.error.orEmpty().removePrefix(LocalVaultStore.PERMANENT).trim(), color = EColors.Danger, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (f.hasLocalCopy) {
                        IconButton(onClick = { onRetry(f.id) }) { Icon(Icons.Outlined.Refresh, "Yenidən cəhd et", tint = EColors.Accent) }
                        IconButton(onClick = { onSaveCopy(f.id) }) { Icon(Icons.Outlined.Download, "Telefona şifrəsiz saxla", tint = EColors.Muted) }
                    }
                    IconButton(onClick = { deleting = f }) { Icon(Icons.Outlined.Delete, "Sil", tint = EColors.Danger) }
                }
            }
        }
    }
    deleting?.let { f ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = EColors.Bg2,
            title = { Text("\"${f.name}\" silinsin?") },
            text = { Text("Faylın bu telefondakı şifrəli nüsxəsi silinəcək. Orijinal qalereyada qalıbsa, onu yenidən yükləyə bilərsiniz.", color = EColors.Muted) },
            confirmButton = { TextButton(onClick = { deleting = null; onDelete(f.id) }) { Text("Sil", color = EColors.Danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Ləğv et", color = EColors.Muted) } },
        )
    }
}
