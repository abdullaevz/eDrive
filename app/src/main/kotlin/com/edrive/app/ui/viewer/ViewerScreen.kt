package com.edrive.app.ui.viewer

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import com.edrive.app.data.Session
import com.edrive.app.data.vault.FileAccessService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.edrive.app.data.db.entity.FileEntity
import com.edrive.app.data.db.entity.FileStatus
import com.edrive.app.ui.components.PrimaryButton
import com.edrive.app.ui.home.typeIcon
import com.edrive.app.ui.theme.EColors
import com.edrive.app.util.Media
import com.edrive.app.util.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ViewerUi(
    val loading: Boolean = false,
    /** Cihaza endirmə (deşifrə edilmiş nüsxə) gedir */
    val exporting: Boolean = false,
    val progress: Float = 0f,
    val bitmap: Bitmap? = null,
    val error: String? = null,
)

@HiltViewModel
class ViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val fileAccess: FileAccessService,
    private val session: Session,
) : ViewModel() {
    val id: String = checkNotNull(savedStateHandle["id"])
    fun beginExternalUi() = session.beginExternalUi()
    fun endExternalUi() = session.endExternalUi()

    val file: StateFlow<FileEntity?> = fileAccess.observeFile(id).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val ui = MutableStateFlow(ViewerUi())
    private val _open = Channel<Uri>(Channel.BUFFERED)
    val openEvents = _open.receiveAsFlow()
    val closeEvents = Channel<Unit>(Channel.BUFFERED)
    val messages = Channel<String>(Channel.BUFFERED)

    /** Şəkil/video → qalereya (Android 10+). */
    fun saveToGallery() = export { fileAccess.exportToGallery(id) { p -> ui.value = ui.value.copy(progress = p) }.let { "Qalereyaya saxlanıldı: $it" } }

    /** İstənilən fayl → istifadəçinin seçdiyi yer. */
    fun saveTo(uri: Uri) = export { fileAccess.exportTo(id, uri) { p -> ui.value = ui.value.copy(progress = p) }; "Fayl cihaza saxlanıldı" }

    private fun export(block: suspend () -> String) {
        if (ui.value.exporting) return
        viewModelScope.launch {
            ui.value = ui.value.copy(exporting = true, progress = 0f)
            try {
                messages.send(block())
            } catch (e: Exception) {
                messages.send("Endirmə alınmadı: ${e.message}")
            } finally {
                ui.value = ui.value.copy(exporting = false)
            }
        }
    }

    fun loadImage() {
        if (ui.value.bitmap != null || ui.value.loading) return
        viewModelScope.launch {
            ui.value = ViewerUi(loading = true)
            try {
                val bytes = fileAccess.decryptToMemory(id) { p -> ui.value = ui.value.copy(progress = p) }
                val bmp = withContext(Dispatchers.Default) { Media.decodeFull(bytes) }
                bytes.fill(0)
                ui.value = if (bmp != null) ViewerUi(bitmap = bmp) else ViewerUi(error = "Şəkil formatı dəstəklənmir — kənar tətbiqdə açın")
            } catch (e: Exception) {
                ui.value = ViewerUi(error = e.message ?: "Deşifrə alınmadı")
            }
        }
    }

    fun openExternally() {
        viewModelScope.launch {
            ui.value = ui.value.copy(loading = true, error = null)
            try {
                val uri = fileAccess.openExternally(id) { p -> ui.value = ui.value.copy(progress = p) }
                _open.send(uri)
            } catch (e: Exception) {
                ui.value = ui.value.copy(error = e.message ?: "Açılmadı")
            } finally {
                ui.value = ui.value.copy(loading = false)
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            try {
                fileAccess.delete(id)
                closeEvents.send(Unit)
            } catch (e: Exception) {
                ui.value = ui.value.copy(error = "Silinmədi: ${e.message}")
            }
        }
    }
}

@Composable
fun ViewerRoute(vm: ViewerViewModel, onBack: () -> Unit) {
    val file by vm.file.collectAsState()
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDownload by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(file?.mimeType ?: "*/*")) { uri ->
        vm.endExternalUi()
        if (uri != null) vm.saveTo(uri)
    }
    val isMedia = file?.let { it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/") } == true
    LaunchedEffect(Unit) { for (m in vm.messages) snackbar.showSnackbar(m) }

    val isImage = file?.let { it.mimeType.startsWith("image/") } == true
    LaunchedEffect(file?.id, isImage) { if (isImage) vm.loadImage() }
    LaunchedEffect(Unit) {
        vm.openEvents.collect { uri ->
            vm.beginExternalUi()
            val type = file?.mimeType ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { context.startActivity(Intent.createChooser(intent, "Aç")) }
            vm.endExternalUi()
        }
    }
    LaunchedEffect(Unit) { for (u in vm.closeEvents) onBack() }

    ViewerContent(
        file, ui, isImage, onBack,
        onDelete = { confirmDelete = true },
        onOpenExternal = vm::openExternally,
        onDownload = { confirmDownload = true },
        snackbar = snackbar,
    )

    if (confirmDownload) {
        val toGallery = isMedia && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        AlertDialog(
            onDismissRequest = { confirmDownload = false },
            containerColor = EColors.Bg2,
            icon = { Icon(Icons.Outlined.Download, null, tint = EColors.Accent) },
            title = { Text("Cihaza endirilsin?") },
            text = {
                Text(
                    (if (toGallery) "\"${file?.name}\" deşifrə olunub qalereyaya (${if (file?.mimeType?.startsWith("video/") == true) "Movies" else "Pictures"}/eDrive) saxlanacaq."
                    else "\"${file?.name}\" deşifrə olunub seçdiyiniz yerə saxlanacaq.") +
                        "\n\nEndirilən nüsxə ŞİFRƏSİZ olacaq — telefona çıxışı olan hər kəs onu görə bilər. Drive-dakı şifrəli nüsxə toxunulmaz qalır.",
                    color = EColors.Muted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDownload = false
                    if (toGallery) vm.saveToGallery()
                    else {
                        vm.beginExternalUi()
                        saveAs.launch(file?.name ?: "eDrive-fayl")
                    }
                }) { Text("Endir", color = EColors.Accent) }
            },
            dismissButton = { TextButton(onClick = { confirmDownload = false }) { Text("Ləğv et", color = EColors.Muted) } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = EColors.Bg2,
            title = { Text("Fayl silinsin?") },
            text = { Text("\"${file?.name}\" həm bu cihazdan, həm də Google Drive-dan silinəcək. Bu əməliyyat geri qaytarılmır.", color = EColors.Muted) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Sil", color = EColors.Danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Ləğv et", color = EColors.Muted) } },
        )
    }
}

@Composable
fun ViewerContent(
    file: FileEntity?,
    ui: ViewerUi,
    isImage: Boolean,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onOpenExternal: () -> Unit,
    onDownload: () -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val bmp = ui.bitmap
        if (bmp != null) {
            ZoomableImage(bmp)
        } else if (file != null && !isImage) {
            NonImageBody(file, ui, onOpenExternal, onDownload)
        }
        if (ui.loading && bmp == null && isImage) {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.LockOpen, null, tint = EColors.Accent, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(14.dp))
                Text(if (ui.progress in 0.001f..0.999f) "Endirilir və deşifrə olunur…" else "Deşifrə olunur…", color = EColors.Muted)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { ui.progress.coerceIn(0f, 1f) }, color = EColors.Accent,
                    trackColor = EColors.Surface3, modifier = Modifier.width(200.dp),
                )
            }
        }
        if (ui.error != null && isImage) {
            Text(ui.error, color = EColors.Danger, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(32.dp))
        }

        // Üst panel
        Row(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                .statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Geri", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(file?.name.orEmpty(), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                file?.let {
                    Text("${formatBytes(it.size)} · AES-256-GCM", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
            if (file != null && (file.status == FileStatus.SYNCED || file.status == FileStatus.PENDING)) {
                IconButton(onClick = onDownload, enabled = !ui.exporting) {
                    if (ui.exporting) CircularProgressIndicator(Modifier.size(20.dp), color = EColors.Accent, strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Download, "Cihaza endir", tint = Color.White)
                }
            }
            if (isImage) IconButton(onClick = onOpenExternal) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Başqa tətbiqdə aç", tint = Color.White) }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Sil", tint = Color.White) }
        }

        if (ui.exporting) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xE60E1218))
                    .navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Text("Deşifrə olunur və cihaza yazılır…", color = Color.White, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { ui.progress.coerceIn(0f, 1f) }, color = EColors.Accent, trackColor = EColors.Surface3, modifier = Modifier.fillMaxWidth())
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp))
    }
}

@Composable
private fun ZoomableImage(bmp: Bitmap) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    val image = remember(bmp) { bmp.asImageBitmap() }
    Image(
        image, null, contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f }) }
            .transformable(state)
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
    )
}

@Composable
private fun NonImageBody(file: FileEntity, ui: ViewerUi, onOpen: () -> Unit, onDownload: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val (icon, tint) = typeIcon(file)
        Box(Modifier.size(88.dp).background(EColors.Surface2, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(file.name, style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center)
        Text("${file.mimeType} · ${formatBytes(file.size)}", color = EColors.Muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(24.dp))
        if (file.status != FileStatus.SYNCED && file.status != FileStatus.PENDING) {
            Text("Fayl hələ hazırlanır…", color = EColors.Muted)
        } else {
            PrimaryButton("Deşifrə et və aç", onOpen, loading = ui.loading, icon = Icons.Outlined.LockOpen)
            if (ui.loading) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { ui.progress.coerceIn(0f, 1f) }, color = EColors.Accent, trackColor = EColors.Surface3, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onDownload, enabled = !ui.exporting && !ui.loading, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Outlined.Download, null, tint = EColors.Accent)
                Spacer(Modifier.width(8.dp))
                Text("Cihaza endir", color = Color.White)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Fayl müvəqqəti deşifrə olunub seçdiyiniz tətbiqdə açılacaq. Müvəqqəti nüsxə vault kilidlənəndə silinir.",
                color = EColors.Faint, fontSize = 12.sp, textAlign = TextAlign.Center,
            )
        }
        ui.error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = EColors.Danger, textAlign = TextAlign.Center) }
    }
}
