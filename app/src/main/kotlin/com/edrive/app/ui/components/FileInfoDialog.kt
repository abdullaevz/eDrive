package com.edrive.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edrive.app.data.db.entity.FileEntity
import com.edrive.app.data.db.entity.FileStatus
import com.edrive.app.data.vault.LocalVaultStore
import com.edrive.app.ui.theme.EColors
import com.edrive.app.util.formatBytes
import com.edrive.crypto.StreamingCipher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** (başlıq, dəyər) cütləri — pəncərə və testlər üçün ayrıca funksiya. */
fun fileInfoRows(f: FileEntity, locale: Locale = Locale.getDefault()): List<Pair<String, String>> = buildList {
    add("Ad" to f.name)
    add("Növ" to f.mimeType)
    add("Ölçü" to if (f.size >= 0) "${formatBytes(f.size)} (${"%,d".format(locale, f.size)} bayt)" else "—")
    if (f.width > 0 && f.height > 0) {
        val mp = f.width.toLong() * f.height / 1_000_000.0
        add("Ölçülər" to "${f.width} × ${f.height} px" + if (mp >= 1.0) " · ${"%.1f".format(locale, mp)} MP" else "")
    }
    add("Əlavə edilib" to SimpleDateFormat("dd.MM.yyyy HH:mm", locale).format(Date(f.createdAt)))
    add("Vəziyyət" to when (f.status) {
        FileStatus.ENCRYPTING -> "Şifrələnir"
        FileStatus.PENDING -> "Yükləmə növbəsindədir"
        FileStatus.UPLOADING -> "Drive-a yüklənir"
        FileStatus.SYNCED -> "Google Drive-da saxlanılır"
        FileStatus.LOCAL -> "Yalnız bu cihazda (Drive-dan silinib)"
        FileStatus.FAILED -> "Xəta" + (f.error?.let { ": ${it.removePrefix(LocalVaultStore.PERMANENT).trim()}" } ?: "")
    })
    add("Şifrələmə" to "AES-256-GCM · 64 KiB bloklar")
    if (f.size >= 0) add("Şifrəli ölçü" to formatBytes(StreamingCipher.ciphertextSize(f.size)))
    add("Fayl ID" to f.id)
}

@Composable
fun FileInfoDialog(file: FileEntity, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EColors.Bg2,
        icon = { Icon(Icons.Outlined.Info, null, tint = EColors.Accent) },
        title = { Text("Fayl məlumatı") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for ((k, v) in fileInfoRows(file)) {
                    Column {
                        Text(k, color = EColors.Faint, fontSize = 12.sp)
                        Text(
                            v, fontSize = 14.sp,
                            fontFamily = if (k == "Fayl ID" || k == "Növ") FontFamily.Monospace else FontFamily.Default,
                            overflow = TextOverflow.Visible,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Bağla", color = EColors.Accent) } },
    )
}
