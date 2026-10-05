package com.edrive.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.edrive.app.ui.components.EField
import com.edrive.app.ui.components.PrimaryButton
import com.edrive.app.ui.components.StatusDot
import com.edrive.app.ui.findActivity
import com.edrive.app.ui.theme.EColors
import com.edrive.app.util.formatBytes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeRoute(vm: HomeViewModel, diagnostics: () -> String, onOpen: (String) -> Unit) {
    val user by vm.user.collectAsState()
    val files by vm.files.collectAsState()
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAccount by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        vm.endExternalUi()
        if (r.resultCode == android.app.Activity.RESULT_OK) vm.onConsentResult(r.data)
    }
    val pickAccount = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        vm.endExternalUi()
        if (r.resultCode == android.app.Activity.RESULT_OK) {
            vm.onAccountPicked(r.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME))
        }
    }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        vm.endExternalUi()
        vm.import(uris)
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.endExternalUi()
        vm.import(uris)
    }
    val saveLog = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        vm.endExternalUi()
        if (uri != null) scope.launch {
            val ok = runCatching {
                val text = diagnostics()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
                }
            }.isSuccess
            snackbar.showSnackbar(if (ok) "Diaqnostika jurnalı saxlanıldı" else "Saxlamaq alınmadı")
        }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is HomeEvent.Message -> scope.launch { snackbar.showSnackbar(e.text) }
                is HomeEvent.PickAccount -> pickAccount.launch(
                    com.google.android.gms.common.AccountPicker.newChooseAccountIntent(
                        com.google.android.gms.common.AccountPicker.AccountChooserOptions.Builder()
                            .setAllowableAccountsTypes(listOf("com.google"))
                            .setAlwaysShowAccountPicker(true)
                            .build(),
                    ),
                )
                is HomeEvent.LaunchConsent -> consent.launch(IntentSenderRequest.Builder(e.pendingIntent.intentSender).build())
            }
        }
    }

    HomeContent(
        username = vm.username,
        user = user,
        files = files,
        ui = ui,
        snackbar = snackbar,
        loadThumb = vm::thumbnail,
        onAccount = { showAccount = true },
        onLock = vm::lock,
        onSync = { vm.sync() },
        onConnect = vm::connectDrive,
        onUpload = {
            if (user?.driveUserFolderId == null) scope.launch { snackbar.showSnackbar("Əvvəlcə Google Drive-a qoşulun") }
            else showPicker = true
        },
        onOpen = onOpen,
        onRetry = vm::retry,
    )

    if (showPicker) {
        ModalBottomSheet(onDismissRequest = { showPicker = false }, sheetState = rememberModalBottomSheetState(), containerColor = EColors.Bg2) {
            UploadChooser(
                onMedia = {
                    showPicker = false
                    vm.beginExternalUi()
                    pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                onFiles = {
                    showPicker = false
                    vm.beginExternalUi()
                    pickFiles.launch(arrayOf("*/*"))
                },
            )
        }
    }

    if (showAccount) {
        ModalBottomSheet(onDismissRequest = { showAccount = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = EColors.Bg2) {
            AccountSheetContent(
                username = vm.username,
                user = user,
                files = files.orEmpty(),
                driveBusy = ui.driveBusy,
                syncing = ui.syncing,
                biometricAvailable = vm.biometricAvailable,
                onConnect = vm::connectDrive,
                onDisconnect = vm::disconnectDrive,
                onSync = { vm.sync() },
                onBiometric = { on -> if (on) vm.enableBiometric(activity) else vm.disableBiometric() },
                onLock = { showAccount = false; vm.lock() },
                onExportLog = {
                    vm.beginExternalUi()
                    saveLog.launch("eDrive-diaqnostika-${java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())}.txt")
                },
            )
        }
    }

    ui.remoteVault?.let {
        RemoteVaultDialog(
            busy = ui.driveBusy, error = ui.remotePasswordError,
            onSubmit = vm::submitRemotePassword, onCancel = vm::cancelRemotePassword,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    username: String,
    user: UserEntity?,
    files: List<FileEntity>?,
    ui: HomeUi,
    snackbar: SnackbarHostState,
    loadThumb: suspend (String) -> ImageBitmap?,
    onAccount: () -> Unit,
    onLock: () -> Unit,
    onSync: () -> Unit,
    onConnect: () -> Unit,
    onUpload: () -> Unit,
    onOpen: (String) -> Unit,
    onRetry: (String) -> Unit,
) {
    val connected = user?.driveUserFolderId != null
    Scaffold(
        containerColor = EColors.Bg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EColors.Bg),
                title = {},
                navigationIcon = {
                    Row(
                        Modifier.padding(start = 12.dp).clip(RoundedCornerShape(50)).clickable(onClick = onAccount)
                            .background(EColors.Surface).border(1.dp, EColors.Line, RoundedCornerShape(50))
                            .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            Avatar(username, 30.dp)
                            StatusDot(
                                if (connected) EColors.Accent else EColors.Faint,
                                Modifier.align(Alignment.BottomEnd).border(2.dp, EColors.Surface, CircleShape),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(username, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    if (connected) {
                        IconButton(onClick = onSync, enabled = !ui.syncing) {
                            if (ui.syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = EColors.Accent)
                            else Icon(Icons.Outlined.Sync, "Sinxronlaşdır", tint = EColors.Muted)
                        }
                    }
                    IconButton(onClick = onLock) { Icon(Icons.Outlined.Lock, "Kilidlə", tint = EColors.Amber) }
                },
            )
        },
        floatingActionButton = {
            if (connected) {
                ExtendedFloatingActionButton(
                    onClick = onUpload,
                    icon = { if (ui.importing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = EColors.AccentInk) else Icon(Icons.Outlined.Add, null) },
                    text = { Text(if (ui.importing) "Şifrələnir…" else "Yüklə", fontWeight = FontWeight.SemiBold) },
                    containerColor = EColors.Accent, contentColor = EColors.AccentInk,
                    shape = RoundedCornerShape(16.dp),
                )
            }
        },
    ) { pad ->
        val list = files.orEmpty()
        LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SummaryHeader(list, connected, username)
            }
            if (!connected) {
                item(span = { GridItemSpan(maxLineSpan) }) { ConnectCard(ui.driveBusy, onConnect) }
            }
            if (files != null && list.isEmpty() && connected) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyState() }
            }
            items(list, key = { it.id }) { f ->
                FileTile(f, loadThumb, onClick = {
                    if (f.status == FileStatus.FAILED) onRetry(f.id) else onOpen(f.id)
                })
            }
        }
    }
}

@Composable
private fun SummaryHeader(files: List<FileEntity>, connected: Boolean, username: String) {
    Column(Modifier.padding(bottom = 10.dp, top = 4.dp)) {
        Text("Şifrəli fayllar", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        val total = files.sumOf { it.size.coerceAtLeast(0) }
        val pending = files.count { it.status != FileStatus.SYNCED }
        Text(
            buildString {
                append("${files.size} fayl · ${formatBytes(total)}")
                if (connected) append(" · ${DriveLayout.ROOT_FOLDER}/$username")
                if (pending > 0) append(" · $pending növbədə")
            },
            color = EColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ConnectCard(busy: Boolean, onConnect: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF13261F), EColors.Surface)))
            .border(1.dp, Color(0x333DDC97), RoundedCornerShape(20.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x1A3DDC97)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.CloudOff, null, tint = EColors.Accent)
        }
        Text("Google Drive-a qoşulun", style = MaterialTheme.typography.titleMedium)
        Text(
            "Fayllarınız telefonda AES-256-GCM ilə şifrələnib Drive-dakı \"${DriveLayout.ROOT_FOLDER}\" qovluğuna yüklənəcək. " +
                "Google faylların məzmununu və adlarını görə bilməz.",
            color = EColors.Muted, fontSize = 14.sp,
        )
        Spacer(Modifier.height(4.dp))
        PrimaryButton("Google ilə qoşul", onConnect, loading = busy)
    }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxWidth().padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.PhotoLibrary, null, tint = EColors.Faint, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("Vault boşdur", style = MaterialTheme.typography.titleMedium)
        Text("\"Yüklə\" düyməsi ilə ilk faylınızı şifrələyin", color = EColors.Muted, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileTile(f: FileEntity, loadThumb: suspend (String) -> ImageBitmap?, onClick: () -> Unit) {
    val thumb by produceState<ImageBitmap?>(null, f.id, f.hasThumb) { if (f.hasThumb) value = loadThumb(f.id) }
    Box(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(EColors.Surface2)
            .border(1.dp, EColors.Line, RoundedCornerShape(14.dp)).combinedClickable(onClick = onClick),
    ) {
        val t = thumb
        if (t != null) {
            Image(t, f.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (f.mimeType.startsWith("video/")) {
                Icon(Icons.Outlined.PlayCircle, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(34.dp))
            }
        } else {
            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                val (icon, tint) = typeIcon(f)
                Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
                Column {
                    Text(f.name, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
                    Text(formatBytes(f.size), fontSize = 10.sp, color = EColors.Faint, fontFamily = FontFamily.Monospace)
                }
            }
        }
        StatusOverlay(f)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.StatusOverlay(f: FileEntity) {
    when (f.status) {
        FileStatus.ENCRYPTING, FileStatus.UPLOADING -> {
            Box(Modifier.matchParentSize().background(Color(0x99070A0E)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        progress = { f.progress.coerceIn(0.02f, 1f) }, modifier = Modifier.size(34.dp),
                        color = if (f.status == FileStatus.ENCRYPTING) EColors.Amber else EColors.Accent,
                        trackColor = Color(0x33FFFFFF), strokeWidth = 3.dp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (f.status == FileStatus.ENCRYPTING) "şifrələnir" else "${(f.progress * 100).toInt()}%",
                        fontSize = 10.sp, color = Color.White, fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
        FileStatus.PENDING -> Badge(Icons.Outlined.CloudQueue, EColors.Muted, Modifier.align(Alignment.BottomEnd))
        FileStatus.FAILED -> {
            Box(Modifier.matchParentSize().background(Color(0x66200808)))
            Badge(Icons.Outlined.ErrorOutline, EColors.Danger, Modifier.align(Alignment.BottomEnd))
        }
        FileStatus.SYNCED -> Badge(Icons.Outlined.CloudDone, EColors.Accent, Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun Badge(icon: ImageVector, tint: Color, modifier: Modifier) {
    Box(
        modifier.padding(6.dp).size(22.dp).clip(CircleShape).background(Color(0xCC0A0D12)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp)) }
}

fun typeIcon(f: FileEntity): Pair<ImageVector, Color> {
    val ext = f.name.substringAfterLast('.', "").lowercase()
    return when {
        f.mimeType == "application/pdf" || ext == "pdf" -> Icons.Outlined.PictureAsPdf to Color(0xFFF7768E)
        f.mimeType.startsWith("audio/") -> Icons.Outlined.AudioFile to Color(0xFF9ECE6A)
        f.mimeType.startsWith("video/") -> Icons.Outlined.PlayCircle to Color(0xFFBB9AF7)
        ext in setOf("zip", "rar", "7z", "tar", "gz") -> Icons.Outlined.FolderZip to Color(0xFFE0AF68)
        ext in setOf("doc", "docx", "txt", "md", "odt", "rtf", "xls", "xlsx", "csv", "ppt", "pptx") -> Icons.Outlined.Description to Color(0xFF7AA2F7)
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile to EColors.Muted
    }
}

@Composable
private fun UploadChooser(onMedia: () -> Unit, onFiles: () -> Unit) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Nə yükləmək istəyirsiniz?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
        ChooserRow(Icons.Outlined.PhotoLibrary, "Foto və video", "Qalereyadan seçin — miniatür ilə göstəriləcək", onMedia)
        ChooserRow(Icons.Outlined.UploadFile, "Fayllar", "Sənəd, arxiv, PDF və istənilən digər fayl", onFiles)
    }
}

@Composable
private fun ChooserRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(EColors.Surface)
            .border(1.dp, EColors.Line, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x1A3DDC97)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = EColors.Accent)
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = EColors.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun RemoteVaultDialog(busy: Boolean, error: String?, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        containerColor = EColors.Bg2,
        title = { Text("Drive-da mövcud vault tapıldı") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Bu Google hesabında bu istifadəçi adı ilə əvvəllər yaradılmış şifrəli fayllar var. " +
                        "Onları açmaq üçün həmin vault-un parolunu daxil edin.",
                    color = EColors.Muted, fontSize = 14.sp,
                )
                EField(pw, { pw = it }, "Vault parolu", Icons.Outlined.Lock, password = true, isError = error != null)
                if (error != null) Text(error, color = EColors.Danger, fontSize = 13.sp)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = EColors.Accent)
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(pw) }, enabled = !busy && pw.length >= 8) { Text("Aç", color = EColors.Accent) } },
        dismissButton = { TextButton(onClick = onCancel, enabled = !busy) { Text("Ləğv et", color = EColors.Muted) } },
    )
}
