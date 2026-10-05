package com.edrive.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edrive.app.ui.components.PrimaryButton
import com.edrive.app.ui.theme.EColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Əvvəlki açılışda tətbiq çöküb — hesabatı göstərir, kopyalamağa və fayl kimi saxlamağa imkan verir. */
@Composable
fun CrashRoute(report: String, fileName: String, onDismiss: () -> Unit, onExternalUi: (Boolean) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        onExternalUi(false)
        if (uri != null) scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { it.write(report.toByteArray()) } }
            }.isSuccess
            snackbar.showSnackbar(if (ok) "Hesabat saxlanıldı" else "Saxlamaq alınmadı")
        }
    }
    CrashContent(
        report = report,
        snackbar = snackbar,
        onCopy = {
            clipboard.setText(AnnotatedString(report))
            scope.launch { snackbar.showSnackbar("Hesabat kopyalandı") }
        },
        onSave = {
            onExternalUi(true)
            save.launch(fileName)
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun CrashContent(report: String, snackbar: SnackbarHostState, onCopy: () -> Unit, onSave: () -> Unit, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(EColors.Bg).systemBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(EColors.Danger.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.BugReport, null, tint = EColors.Danger) }
                Column(Modifier.padding(start = 14.dp)) {
                    Text("eDrive gözlənilmədən bağlandı", style = MaterialTheme.typography.titleMedium)
                    Text("Aşağıdakı hesabat səbəbi göstərir", color = EColors.Muted, fontSize = 13.sp)
                }
            }
            Text(
                "Hesabatda yalnız texniki məlumat var (xəta izi, cihaz modeli, versiya). Parol, açar və fayllarınız buraya düşmür. " +
                    "Fayllarınız təhlükəsizdir — şifrəli nüsxələr toxunulmaz qalır.",
                color = EColors.Muted, fontSize = 13.sp,
            )
            Box(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF070A0E))
                    .border(1.dp, EColors.Line, RoundedCornerShape(12.dp)),
            ) {
                SelectionContainer {
                    Text(
                        report, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp, color = Color(0xFFB8C2D3),
                        softWrap = false,
                        modifier = Modifier.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(12.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onCopy, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(48.dp)) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp), tint = EColors.Accent)
                    Spacer(Modifier.width(8.dp)); Text("Kopyala", color = EColors.Text)
                }
                OutlinedButton(onClick = onSave, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(48.dp)) {
                    Icon(Icons.Outlined.Save, null, Modifier.size(18.dp), tint = EColors.Accent)
                    Spacer(Modifier.width(8.dp)); Text("Fayl kimi saxla", color = EColors.Text)
                }
            }
            PrimaryButton("Bağla və davam et", onDismiss)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp))
    }
}
