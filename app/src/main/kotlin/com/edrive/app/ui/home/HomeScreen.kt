package com.edrive.app.ui.home

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.edrive.app.data.db.entity.FolderEntity
import com.edrive.app.data.db.entity.UserEntity
import com.edrive.app.ui.components.Avatar
import com.edrive.app.ui.components.EField
import com.edrive.app.ui.components.FileInfoDialog
import com.edrive.app.ui.components.PrimaryButton
import com.edrive.app.ui.components.StatusDot
import com.edrive.app.ui.drive.DisconnectDialog
import com.edrive.app.ui.drive.DriveEvent
import com.edrive.app.ui.drive.DriveViewModel
import com.edrive.app.ui.drive.KeyDocumentDialog
import com.edrive.app.ui.drive.RestoreProgressDialog
import com.edrive.app.ui.drive.SecurityKeyDialog
import com.edrive.app.ui.findActivity
import com.edrive.app.ui.folders.Breadcrumb
import com.edrive.app.ui.folders.DeleteFolderDialog
import com.edrive.app.ui.folders.FolderActionsDialog
import com.edrive.app.ui.folders.FolderDialog
import com.edrive.app.ui.folders.FolderLocation
import com.edrive.app.ui.folders.FolderNameDialog
import com.edrive.app.ui.folders.FolderTile
import com.edrive.app.ui.folders.FoldersViewModel
import com.edrive.app.ui.folders.MoveToFolderDialog
import com.edrive.app.ui.theme.EColors
import com.edrive.app.util.formatBytes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeRoute(
    vm: HomeViewModel,
    driveVm: DriveViewModel,
    foldersVm: FoldersViewModel,
    diagnostics: () -> String,
    onGallery: () -> Unit = {},
    onOpen: (String) -> Unit,
) {
    val user by vm.user.collectAsState()
    val files by vm.files.collectAsState()
    val ui by vm.ui.collectAsState()
    val drive by driveVm.ui.collectAsState()
    val location by foldersVm.location.collectAsState()
    val folderUi by foldersVm.ui.collectAsState()
    var menuFolder by remember { mutableStateOf<FolderEntity?>(null) }
    val selection by vm.selection.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAccount by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDownload by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showProblems by remember { mutableStateOf(false) }

    BackHandler(enabled = selection.active) { vm.clearSelection() }
    BackHandler(enabled = !selection.active && location.current != null) { foldersVm.up() }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        vm.endExternalUi()
        if (uri != null) vm.downloadSelected(uri)
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        driveVm.endExternalUi()
        if (r.resultCode == android.app.Activity.RESULT_OK) driveVm.onConsentResult(r.data)
    }
    val pickAccount = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        driveVm.endExternalUi()
        if (r.resultCode == android.app.Activity.RESULT_OK) {
            driveVm.onAccountPicked(r.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME))
        }
    }
    val saveKeyPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        driveVm.endExternalUi()
        if (uri != null) driveVm.saveKeyDocument(context, uri)
    }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        vm.endExternalUi()
        vm.import(uris, location.currentId)
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.endExternalUi()
        vm.import(uris, location.currentId)
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
                is HomeEvent.LaunchConsent -> consent.launch(IntentSenderRequest.Builder(e.pendingIntent.intentSender).build())
            }
        }
    }
    LaunchedEffect(Unit) {
        foldersVm.messages.collect { scope.launch { snackbar.showSnackbar(it) } }
    }
    LaunchedEffect(Unit) {
        driveVm.events.collect { e ->
            when (e) {
                is DriveEvent.Message -> scope.launch { snackbar.showSnackbar(e.text) }
                is DriveEvent.PickAccount -> pickAccount.launch(
                    com.google.android.gms.common.AccountPicker.newChooseAccountIntent(
                        com.google.android.gms.common.AccountPicker.AccountChooserOptions.Builder()
                            .setAllowableAccountsTypes(listOf("com.google"))
                            .setAlwaysShowAccountPicker(true)
                            .build(),
                    ),
                )
                is DriveEvent.LaunchConsent -> consent.launch(IntentSenderRequest.Builder(e.pendingIntent.intentSender).build())
            }
        }
    }

    HomeContent(
        username = vm.username,
        user = user,
        files = files,
        ui = ui,
        driveBusy = drive.busy,
        snackbar = snackbar,
        loadThumb = vm::thumbnail,
        onAccount = { showAccount = true },
        onLock = vm::lock,
        onSync = { vm.sync() },
        onConnect = driveVm::connect,
        onUpload = {
            if (user?.isDriveReady != true) scope.launch { snackbar.showSnackbar("Əvvəlcə Google Drive-a qoşulun") }
            else showPicker = true
        },
        onOpen = onOpen,
        onRetry = vm::retry,
        onProblems = { showProblems = true },
        folders = location,
        folderActions = FolderActions(
            onOpen = foldersVm::open,
            onMenu = { menuFolder = it },
            onCreate = foldersVm::showCreate,
        ),
        selection = selection,
        selectionActions = SelectionActions(
            onToggle = vm::toggleSelect,
            onStart = vm::startSelect,
            onSelectAll = vm::selectAll,
            onClear = vm::clearSelection,
            onDownload = { confirmDownload = true },
            onDelete = { confirmDelete = true },
            onInfo = { showInfo = true },
            onMove = { foldersVm.showMove(selection.ids) },
        ),
    )

    val chosen = files.orEmpty().filter { it.id in selection.ids }
    val infoFile = chosen.singleOrNull()
    if (showInfo && infoFile != null) FileInfoDialog(infoFile, onDismiss = { showInfo = false })

    if (confirmDelete) {
        val removable = chosen.driveRemovable(chosen.map { it.id }.toSet())
        val skipped = chosen.size - removable.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = EColors.Bg2,
            icon = { Icon(Icons.Outlined.Delete, null, tint = EColors.Danger) },
            title = { Text("${removable.size} fayl Drive-dan silinsin?") },
            text = {
                Text(
                    "Seçilmiş fayllar Google Drive-dan silinəcək. Şifrəli nüsxələri bu cihazda qalacaq " +
                        "(${formatBytes(removable.sumOf { it.size.coerceAtLeast(0) })} yer tutacaq) və Drive-a yenidən yüklənməyəcək; " +
                        "Drive-da olmayan nüsxəni başqa cihazdan görmək mümkün olmayacaq. Cihazdakı nüsxəni sonradan faylı açıb silməklə ləğv edə bilərsiniz." +
                        (if (skipped > 0) "\n\n$skipped fayl Drive-da olmadığı üçün (yüklənir, xətalı və ya artıq yalnız cihazda) keçiləcək." else ""),
                    color = EColors.Muted,
                )
            },
            confirmButton = { TextButton(enabled = removable.isNotEmpty(), onClick = { confirmDelete = false; vm.deleteSelected() }) { Text("Drive-dan sil", color = EColors.Danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Ləğv et", color = EColors.Muted) } },
        )
    }

    if (confirmDownload) {
        val ready = chosen.exportable(chosen.map { it.id }.toSet())
        val folder = needsFolder(ready, android.os.Build.VERSION.SDK_INT)
        AlertDialog(
            onDismissRequest = { confirmDownload = false },
            containerColor = EColors.Bg2,
            icon = { Icon(Icons.Outlined.Download, null, tint = EColors.Accent) },
            title = { Text("${ready.size} fayl cihaza endirilsin?") },
            text = {
                Text(
                    (if (folder) "Şəkil və videolar qalereyaya, digər fayllar növbəti addımda seçəcəyiniz qovluğa saxlanacaq."
                    else "Fayllar deşifrə olunub qalereyaya (Pictures/Movies → eDrive) saxlanacaq.") +
                        "\n\nEndirilən nüsxələr ŞİFRƏSİZ olacaq — telefona çıxışı olan hər kəs onları görə bilər. Drive-dakı şifrəli nüsxələr toxunulmaz qalır.",
                    color = EColors.Muted,
                )
            },
            confirmButton = {
                TextButton(enabled = ready.isNotEmpty(), onClick = {
                    confirmDownload = false
                    if (folder) { vm.beginExternalUi(); pickFolder.launch(null) } else vm.downloadSelected(null)
                }) { Text("Endir", color = EColors.Accent) }
            },
            dismissButton = { TextButton(onClick = { confirmDownload = false }) { Text("Ləğv et", color = EColors.Muted) } },
        )
    }

    if (showPicker) {
        ModalBottomSheet(onDismissRequest = { showPicker = false }, sheetState = rememberModalBottomSheetState(), containerColor = EColors.Bg2) {
            UploadChooser(
                onGallery = { showPicker = false; onGallery() },
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

    menuFolder?.let { f ->
        FolderActionsDialog(
            f,
            onRename = { menuFolder = null; foldersVm.showRename(f) },
            onDelete = { menuFolder = null; foldersVm.showDelete(f) },
            onDismiss = { menuFolder = null },
        )
    }
    when (val d = folderUi.dialog) {
        FolderDialog.Create -> FolderNameDialog("Yeni qovluq", "", folderUi.busy, folderUi.error, foldersVm::create, foldersVm::dismiss)
        is FolderDialog.Rename -> FolderNameDialog("Adını dəyiş", d.folder.name, folderUi.busy, folderUi.error, foldersVm::rename, foldersVm::dismiss)
        is FolderDialog.Delete -> DeleteFolderDialog(d.folder, d.contents, folderUi.busy, folderUi.error, foldersVm::delete, foldersVm::dismiss)
        is FolderDialog.Move -> MoveToFolderDialog(
            location.all, d.fileIds.size, folderUi.busy, folderUi.error,
            onMove = { target -> foldersVm.move(target, onDone = vm::clearSelection) }, onDismiss = foldersVm::dismiss,
        )
        null -> Unit
    }

    if (showProblems) {
        val problems = files.orEmpty().problems()
        if (problems.isEmpty()) showProblems = false
        ModalBottomSheet(onDismissRequest = { showProblems = false }, sheetState = rememberModalBottomSheetState(), containerColor = EColors.Bg2) {
            ProblemFilesSheet(
                problems = problems,
                onRetry = vm::retry,
                onRetryAll = vm::retryAll,
                onSaveCopy = { id -> showProblems = false; vm.selectOnly(id); confirmDownload = true },
                onDelete = vm::deleteLocal,
            )
        }
    }

    if (showAccount) {
        ModalBottomSheet(onDismissRequest = { showAccount = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = EColors.Bg2) {
            AccountSheetContent(
                username = vm.username,
                user = user,
                files = files.orEmpty(),
                driveBusy = drive.busy,
                syncing = ui.syncing,
                biometricAvailable = driveVm.biometricAvailable,
                onConnect = driveVm::connect,
                onDisconnect = { showAccount = false; driveVm.requestDisconnect() },
                onSync = { vm.sync() },
                onBiometric = { on -> driveVm.setBiometric(activity, on) },
                onChangeKey = { showAccount = false; driveVm.startKeyChange() },
                onLock = { showAccount = false; vm.lock() },
                onExportLog = {
                    vm.beginExternalUi()
                    saveLog.launch("eDrive-diaqnostika-${java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())}.txt")
                },
            )
        }
    }

    drive.prompt?.let { prompt ->
        // key(prompt) — hər yeni sorğuda sahələr sıfırlanır
        androidx.compose.runtime.key(prompt) {
            SecurityKeyDialog(prompt, drive.busy, drive.promptError, onSubmit = driveVm::submitKey, onCancel = driveVm::cancelPrompt)
        }
    }
    if (drive.keyDocumentPending) {
        KeyDocumentDialog(
            savedAs = drive.keyDocumentSavedAs,
            onSave = {
                driveVm.beginExternalUi()
                saveKeyPdf.launch("eDrive-tehlukesizlik-acari.pdf")
            },
            onClose = driveVm::closeKeyDocument,
        )
    }
    drive.restore?.let { RestoreProgressDialog(it) }
    drive.disconnectUnsynced?.let { n ->
        DisconnectDialog(n, onConfirm = driveVm::disconnect, onCancel = driveVm::cancelDisconnect)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    username: String,
    user: UserEntity?,
    files: List<FileEntity>?,
    ui: HomeUi,
    driveBusy: Boolean = false,
    snackbar: SnackbarHostState,
    loadThumb: suspend (String) -> ImageBitmap?,
    onAccount: () -> Unit,
    onLock: () -> Unit,
    onSync: () -> Unit,
    onConnect: () -> Unit,
    onUpload: () -> Unit,
    onOpen: (String) -> Unit,
    onRetry: (String) -> Unit,
    onProblems: () -> Unit = {},
    folders: FolderLocation = FolderLocation(),
    folderActions: FolderActions = FolderActions(),
    selection: SelectionState = SelectionState(),
    selectionActions: SelectionActions = SelectionActions(),
) {
    val connected = user?.isDriveReady == true
    var filter by rememberSaveable(stateSaver = FileFilterSaver) { mutableStateOf(FileFilter()) }
    val all = files.orEmpty()
    val list = remember(all, folders.currentId) { all.filter { it.folderId == folders.currentId } }
    val shown = remember(list, filter) { applyFilter(list, filter) }
    val busy = ui.batch != null
    Scaffold(
        containerColor = EColors.Bg,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { ui.batch?.let { BatchBar(it) } },
        topBar = {
            if (selection.active) {
                val n = selection.ids.size
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = EColors.Bg),
                    title = { Text(if (n == 0) "Fayl seçin" else "$n seçildi", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = selectionActions.onClear, enabled = !busy) { Icon(Icons.Outlined.Close, "Seçimi ləğv et", tint = EColors.Muted) }
                    },
                    actions = {
                        IconButton(onClick = { selectionActions.onSelectAll(shown.map { it.id }) }, enabled = !busy && shown.isNotEmpty()) {
                            Icon(Icons.Outlined.SelectAll, "Hamısını seç", tint = EColors.Muted)
                        }
                        if (connected) IconButton(onClick = selectionActions.onMove, enabled = n > 0 && !busy) {
                            Icon(Icons.AutoMirrored.Outlined.DriveFileMove, "Qovluğa köçür", tint = if (n > 0 && !busy) EColors.Muted else EColors.Faint)
                        }
                        if (n == 1) IconButton(onClick = selectionActions.onInfo, enabled = !busy) { Icon(Icons.Outlined.Info, "Məlumat", tint = EColors.Muted) }
                        IconButton(onClick = selectionActions.onDownload, enabled = n > 0 && !busy) {
                            Icon(Icons.Outlined.Download, "Cihaza endir", tint = if (n > 0 && !busy) EColors.Accent else EColors.Faint)
                        }
                        IconButton(onClick = selectionActions.onDelete, enabled = n > 0 && !busy) {
                            Icon(Icons.Outlined.Delete, "Sil", tint = if (n > 0 && !busy) EColors.Danger else EColors.Faint)
                        }
                    },
                )
            } else TopAppBar(
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
                    val problemCount = remember(list) { list.problems().size }
                    if (problemCount > 0) {
                        IconButton(onClick = onProblems) {
                            BadgedBox(badge = { androidx.compose.material3.Badge(containerColor = EColors.Danger) { Text("$problemCount") } }) {
                                Icon(Icons.Outlined.WarningAmber, "Problemli fayllar", tint = EColors.Amber)
                            }
                        }
                    }
                    if (connected) {
                        IconButton(onClick = folderActions.onCreate) { Icon(Icons.Outlined.CreateNewFolder, "Yeni qovluq", tint = EColors.Muted) }
                    }
                    if (list.isNotEmpty()) {
                        IconButton(onClick = selectionActions.onStart) { Icon(Icons.Outlined.CheckCircle, "Seç", tint = EColors.Muted) }
                    }
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
            if (connected && !selection.active) {
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
        LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (list.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FilterBar(filter, list, shown.size, onChange = { filter = it })
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SummaryHeader(all, connected)
            }
            if (connected && (folders.current != null || folders.children.isNotEmpty())) {
                item(span = { GridItemSpan(maxLineSpan) }) { Breadcrumb(folders.path, folderActions.onOpen) }
            }
            if (!selection.active) {
                items(folders.children, key = { "folder-" + it.id }) { f ->
                    FolderTile(f, onClick = { folderActions.onOpen(f.id) }, onLongClick = { folderActions.onMenu(f) })
                }
            }
            if (!connected) {
                item(span = { GridItemSpan(maxLineSpan) }) { ConnectCard(driveBusy, onConnect) }
            }
            if (files != null && list.isEmpty() && folders.children.isEmpty() && connected) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyState() }
            }
            if (list.isNotEmpty() && shown.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { NoMatches(onReset = { filter = FileFilter() }) }
            }
            items(shown, key = { it.id }) { f ->
                FileTile(
                    f, loadThumb,
                    onClick = {
                        if (selection.active) selectionActions.onToggle(f.id)
                        else if (f.status == FileStatus.FAILED) onRetry(f.id) else onOpen(f.id)
                    },
                    selectionMode = selection.active,
                    selected = f.id in selection.ids,
                    onLongClick = { if (!busy) selectionActions.onToggle(f.id) },
                )
            }
        }
    }
}

@Composable
private fun SummaryHeader(files: List<FileEntity>, connected: Boolean) {
    Column(Modifier.padding(bottom = 10.dp, top = 4.dp)) {
        Text("Şifrəli fayllar", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        val total = files.sumOf { it.size.coerceAtLeast(0) }
        val pending = files.count { it.status != FileStatus.SYNCED && it.status != FileStatus.LOCAL }
        Text(
            buildString {
                append("${files.size} fayl · ${formatBytes(total)}")
                if (connected) append(" · ${DriveLayout.ROOT_FOLDER}")
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
private fun NoMatches(onReset: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.PhotoLibrary, null, tint = EColors.Faint, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text("Bu süzgəcə uyğun fayl yoxdur", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onReset) { Text("Süzgəci sıfırla", color = EColors.Accent) }
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
fun FileTile(
    f: FileEntity,
    loadThumb: suspend (String) -> ImageBitmap?,
    onClick: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: () -> Unit = {},
) {
    val thumb by produceState<ImageBitmap?>(null, f.id, f.hasThumb) { if (f.hasThumb) value = loadThumb(f.id) }
    Box(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(EColors.Surface2)
            .border(if (selected) 2.dp else 1.dp, if (selected) EColors.Accent else EColors.Line, RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
        if (selectionMode) {
            if (selected) Box(Modifier.matchParentSize().background(Color(0x333DDC97)))
            Box(
                Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp).clip(CircleShape)
                    .background(if (selected) EColors.Accent else Color(0x99070A0E))
                    .border(1.5.dp, if (selected) EColors.Accent else Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Outlined.Check, null, tint = EColors.AccentInk, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun BatchBar(b: BatchProgress) {
    Column(Modifier.fillMaxWidth().background(EColors.Bg2).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text("${b.label}… ${minOf(b.done + 1, b.total)}/${b.total}", fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = { b.fraction }, color = EColors.Accent, trackColor = EColors.Surface3, modifier = Modifier.fillMaxWidth())
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
        FileStatus.LOCAL -> Badge(Icons.Outlined.CloudOff, EColors.Amber, Modifier.align(Alignment.BottomEnd))
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
private fun UploadChooser(onGallery: () -> Unit, onMedia: () -> Unit, onFiles: () -> Unit) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Nə yükləmək istəyirsiniz?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
        ChooserRow(Icons.Outlined.PhotoLibrary, "Qalereya (bütün albomlar)", "Tətbiqin öz qalereyası — bütün qovluqlar, icazə ilə", onGallery)
        ChooserRow(Icons.Outlined.PhotoLibrary, "Foto və video (sistem seçicisi)", "Android-in standart seçicisi, icazəsiz", onMedia)
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
