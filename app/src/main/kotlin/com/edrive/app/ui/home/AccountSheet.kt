package com.edrive.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edrive.app.drive.DriveLayout
import com.edrive.app.data.db.entity.FileEntity
import com.edrive.app.data.db.entity.FileStatus
import com.edrive.app.data.db.entity.UserEntity
import com.edrive.app.ui.components.Avatar
import com.edrive.app.ui.components.PrimaryButton
import com.edrive.app.ui.components.StatusDot
import com.edrive.app.ui.theme.EColors
import com.edrive.app.util.formatBytes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Sol üst küncdəki istifadəçi adına toxunanda açılan panel: Google Drive statusu və idarəsi. */
@Composable
fun AccountSheetContent(
    username: String,
    user: UserEntity?,
    files: List<FileEntity>,
    driveBusy: Boolean,
    syncing: Boolean,
    biometricAvailable: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSync: () -> Unit,
    onBiometric: (Boolean) -> Unit,
    onLock: () -> Unit,
    onExportLog: () -> Unit = {},
) {
    val connected = user?.isDriveReady == true
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(username, 52.dp)
            Column(Modifier.padding(start = 14.dp)) {
                Text(username, style = MaterialTheme.typography.titleLarge)
                user?.let {
                    Text(
                        "Bu cihazda yaradılıb: " + SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(it.createdAt)),
                        color = EColors.Faint, fontSize = 12.sp,
                    )
                }
            }
        }

        // ---- Google Drive kartı
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(EColors.Surface)
                .border(1.dp, if (connected) Color(0x333DDC97) else EColors.Line, RoundedCornerShape(18.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (connected) Icons.Outlined.Cloud else Icons.Outlined.CloudOff, null, tint = if (connected) EColors.Accent else EColors.Faint)
                Text("Google Drive", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp).weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(if (connected) Color(0x173DDC97) else EColors.Surface3)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(if (connected) EColors.Accent else EColors.Faint)
                    Spacer(Modifier.width(6.dp))
                    Text(if (connected) "Qoşulub" else "Qoşulmayıb", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = if (connected) EColors.Accent else EColors.Muted)
                }
            }
            if (connected) {
                InfoRow(Icons.Outlined.Cloud, user?.driveEmail.orEmpty())
                InfoRow(Icons.Outlined.Folder, "${DriveLayout.ROOT_FOLDER} / $username")
                val synced = files.count { it.status == FileStatus.SYNCED }
                val size = files.filter { it.status == FileStatus.SYNCED }.sumOf { it.size.coerceAtLeast(0) }
                Text("$synced fayl Drive-da · ${formatBytes(size)} (şifrəli)", color = EColors.Muted, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onSync, enabled = !syncing, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                        if (syncing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = EColors.Accent)
                        else Icon(Icons.Outlined.Sync, null, Modifier.size(18.dp), tint = EColors.Accent)
                        Spacer(Modifier.width(8.dp))
                        Text("Sinxron", color = EColors.Text)
                    }
                    OutlinedButton(onClick = onDisconnect, enabled = !driveBusy, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                        Text("Ayır", color = EColors.Danger)
                    }
                }
            } else {
                Text(
                    "Qoşulduqdan sonra Drive-da \"${DriveLayout.ROOT_FOLDER}\" qovluğu yaradılacaq və bütün yükləmələr ora şifrəli gedəcək.",
                    color = EColors.Muted, fontSize = 13.sp,
                )
                PrimaryButton("Google ilə qoşul", onConnect, loading = driveBusy)
            }
        }

        // ---- Təhlükəsizlik
        if (biometricAvailable) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(EColors.Surface).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Fingerprint, null, tint = EColors.Accent)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Barmaq izi ilə giriş")
                    Text("Parol yerinə biometrik təsdiq", color = EColors.Faint, fontSize = 12.sp)
                }
                Switch(
                    checked = user?.biometricEnabled == true, onCheckedChange = onBiometric,
                    colors = SwitchDefaults.colors(checkedTrackColor = EColors.Accent, checkedThumbColor = EColors.AccentInk),
                )
            }
        }

        OutlinedButton(onClick = onExportLog, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Outlined.BugReport, null, tint = EColors.Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Diaqnostika jurnalını saxla", color = EColors.Muted)
        }

        OutlinedButton(onClick = onLock, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Outlined.Lock, null, tint = EColors.Amber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Kilidlə / hesabı dəyiş", color = EColors.Amber)
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = EColors.Faint, modifier = Modifier.size(16.dp))
        Text(text, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
