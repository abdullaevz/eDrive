package com.edrive.app.ui.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edrive.app.data.SecurityKeyPolicy
import com.edrive.app.drive.DriveLayout
import com.edrive.app.ui.auth.InfoBox
import com.edrive.app.ui.components.EField
import com.edrive.app.ui.theme.EColors

/** Təhlükəsizlik açarı dialoqu — yaratma, daxil etmə, lokal açma və dəyişmə üçün eyni forma. */
@Composable
fun SecurityKeyDialog(
    prompt: VaultPrompt,
    busy: Boolean,
    error: String?,
    onSubmit: (key: String, confirm: String, old: String) -> Unit,
    onCancel: () -> Unit,
) {
    var old by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val newKey = prompt is VaultPrompt.CreateKey || prompt is VaultPrompt.ChangeKey
    val ready = when {
        prompt is VaultPrompt.ChangeKey -> old.isNotEmpty() && key.length >= SecurityKeyPolicy.MIN_LENGTH && confirm == key
        newKey -> key.length >= SecurityKeyPolicy.MIN_LENGTH && confirm == key
        else -> key.isNotEmpty()
    }
    val (title, text) = when (prompt) {
        is VaultPrompt.CreateKey -> "Təhlükəsizlik açarı yaradın" to
            "Bu açar \"${DriveLayout.ROOT_FOLDER}\" qovluğundakı bütün faylları şifrələyir və heç yerdə saxlanılmır. " +
            "Yeni cihazdan qoşulanda soruşulacaq. Unutsanız, fayllar bərpa olunmur."
        is VaultPrompt.EnterKey -> "Drive-da vault tapıldı" to
            "Bu Google Drive-da əvvəllər yaradılmış şifrəli fayllar var. Onları açmaq üçün Təhlükəsizlik açarını daxil edin."
        VaultPrompt.UnlockLocal -> "Vault-u açın" to
            "Bu cihazda saxlanılan açar əlçatan deyil (məs. telefonun təhlükəsizlik ayarları dəyişib). Təhlükəsizlik açarını bir dəfə daxil edin."
        VaultPrompt.ChangeKey -> "Təhlükəsizlik açarını dəyiş" to
            "Fayllar yenidən şifrələnmir — yalnız açar dəyişir. Digər cihazlar işləməyə davam edəcək; yeni cihaz yeni açarı soruşacaq. " +
            "Köhnə açar sənədini məhv edin."
    }
    AlertDialog(
        onDismissRequest = {},
        containerColor = EColors.Bg2,
        icon = { Icon(Icons.Outlined.Key, null, tint = EColors.Accent) },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text, color = EColors.Muted, fontSize = 14.sp)
                if (prompt is VaultPrompt.ChangeKey) {
                    EField(old, { old = it }, "Köhnə açar", Icons.Outlined.Lock, password = true, enabled = !busy)
                }
                EField(
                    key, { key = it }, if (newKey) "Yeni Təhlükəsizlik açarı" else "Təhlükəsizlik açarı", Icons.Outlined.Key,
                    password = true, enabled = !busy, isError = error != null && !newKey,
                    imeAction = if (newKey) ImeAction.Next else ImeAction.Done,
                    supporting = if (newKey) "Ən azı ${SecurityKeyPolicy.MIN_LENGTH} simvol (${key.length}/${SecurityKeyPolicy.MIN_LENGTH})" else null,
                )
                if (newKey) {
                    StrengthBar(SecurityKeyPolicy.strength(key.toCharArray()), visible = key.isNotEmpty())
                    EField(
                        confirm, { confirm = it }, "Açarı təkrarlayın", Icons.Outlined.Lock, password = true, enabled = !busy,
                        imeAction = ImeAction.Done, isError = confirm.isNotEmpty() && confirm != key,
                    )
                    InfoBox(
                        Icons.Outlined.WarningAmber, EColors.Amber,
                        "Bir neçə sözdən ibarət uzun ifadə (məs. 4–5 təsadüfi söz) qısa mürəkkəb paroldan daha güclü və yadda qalandır.",
                    )
                }
                if (error != null) Text(error, color = EColors.Danger, fontSize = 13.sp)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = EColors.Accent)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(key, confirm, old) }, enabled = !busy && ready) {
                Text(if (newKey) "Təsdiqlə" else "Aç", color = EColors.Accent)
            }
        },
        dismissButton = {
            if (prompt != VaultPrompt.UnlockLocal) TextButton(onClick = onCancel, enabled = !busy) { Text("Ləğv et", color = EColors.Muted) }
        },
    )
}

@Composable
private fun StrengthBar(strength: SecurityKeyPolicy.Strength, visible: Boolean) {
    if (!visible) return
    val (fraction, color) = when (strength) {
        SecurityKeyPolicy.Strength.WEAK -> 0.33f to EColors.Danger
        SecurityKeyPolicy.Strength.FAIR -> 0.66f to EColors.Amber
        SecurityKeyPolicy.Strength.STRONG -> 1f to EColors.Accent
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(EColors.Surface3)) {
            Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
        }
        Text("Güc: ${strength.label}", color = color, fontSize = 12.sp)
    }
}

/** Yeni açar sənədi: açar + vault.json nüsxəsi. Bağlananda açar yaddaşdan silinir. */
@Composable
fun KeyDocumentDialog(savedAs: String?, onSave: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = EColors.Bg2,
        icon = { Icon(Icons.Outlined.Download, null, tint = EColors.Accent) },
        title = { Text("Açar sənədini saxlayın") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Sənəddə Təhlükəsizlik açarı və vault.json-un nüsxəsi olacaq — o, Drive-dakı faylları tək başına aça bilər. " +
                        "Çap edib təhlükəsiz yerdə saxlayın; Google Şəkillərə və ya Drive-a sinxronlaşan qovluqda saxlamayın.",
                    color = EColors.Muted, fontSize = 14.sp,
                )
                if (savedAs != null) InfoBox(Icons.Outlined.CheckCircle, EColors.Accent, "Saxlanıldı: $savedAs")
                else InfoBox(Icons.Outlined.WarningAmber, EColors.Amber, "Sənədsiz açarı unutsanız, fayllarınız bərpa olunmayacaq.")
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text(if (savedAs == null) "PDF saxla" else "Yenidən saxla", color = EColors.Accent) } },
        dismissButton = { TextButton(onClick = onClose) { Text(if (savedAs == null) "Sonra" else "Bağla", color = EColors.Muted) } },
    )
}

/** Ayrılma təsdiqi. [unsynced] > 0 olarsa, itəcək fayllar barədə xəbərdarlıq. */
@Composable
fun DisconnectDialog(unsynced: Int, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = EColors.Bg2,
        icon = { Icon(Icons.Outlined.CloudOff, null, tint = EColors.Danger) },
        title = { Text("Google Drive-dan ayrılsın?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Bu telefondakı şifrəli nüsxələr, miniatürlər və vault silinəcək. Drive-dakı fayllara və telefonun qalereyasındakı " +
                        "orijinallara toxunulmur — yenidən qoşulanda Təhlükəsizlik açarı ilə hamısı qayıdır.",
                    color = EColors.Muted, fontSize = 14.sp,
                )
                if (unsynced > 0) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x1FFF5A5A)).padding(12.dp)) {
                        Text("$unsynced fayl hələ Drive-a yüklənməyib — ayrılsanız itəcək.", color = EColors.Danger, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Ayrıl", color = EColors.Danger) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Ləğv et", color = EColors.Muted) } },
    )
}
