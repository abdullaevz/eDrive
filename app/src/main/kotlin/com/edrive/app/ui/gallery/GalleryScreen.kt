package com.edrive.app.ui.gallery

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.edrive.app.media.DeviceItem
import com.edrive.app.media.MediaAccess
import com.edrive.app.ui.components.PrimaryButton
import com.edrive.app.ui.theme.EColors

/** Telefonun bütün albomları: icazə verildikdən sonra şəkil/videoları seçib şifrələməyə göndərir. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryRoute(onClose: () -> Unit, onImport: (List<Uri>) -> Unit, vm: GalleryViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(0) }

    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.endExternalUi()
        vm.refresh()
    }
    fun askPermission() {
        vm.beginExternalUi()
        request.launch(com.edrive.app.media.DeviceMedia.permissions())
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    LaunchedEffect(Unit) {
        // İlk açılışda icazə yoxdursa, dərhal istənilir
        if (asked == 0 && com.edrive.app.media.DeviceMedia.access(context) == MediaAccess.NONE) {
            asked = 1
            askPermission()
        }
    }

    val shown = remember(ui.items, ui.albumId) { vm.shownItems(ui) }
    val n = ui.selected.size

    Scaffold(
        containerColor = EColors.Bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EColors.Bg),
                title = { Text(if (n == 0) "Qalereya" else "$n seçildi", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Bağla", tint = EColors.Muted) } },
                actions = {
                    if (shown.isNotEmpty()) IconButton(onClick = { vm.selectAllShown() }) {
                        Icon(Icons.Outlined.SelectAll, "Hamısını seç", tint = EColors.Muted)
                    }
                },
            )
        },
        bottomBar = {
            if (n > 0) {
                Box(Modifier.fillMaxWidth().background(EColors.Bg2).navigationBarsPadding().padding(16.dp)) {
                    PrimaryButton("Şifrələ və yüklə ($n)", { onImport(vm.selectedUris()) })
                }
            }
        },
    ) { pad ->
        when {
            ui.access == MediaAccess.NONE -> PermissionNeeded(
                Modifier.padding(pad),
                onAllow = ::askPermission,
                onSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) },
            )
            ui.loading && ui.items.isEmpty() -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EColors.Accent)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 24.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (ui.access == MediaAccess.PARTIAL) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).background(EColors.Surface).padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Yalnız əvvəl seçdiyiniz fayllara icazə var.", Modifier.weight(1f), color = EColors.Muted, fontSize = 13.sp)
                            TextButton(onClick = ::askPermission) { Text("Hamısına icazə ver", color = EColors.Accent) }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AlbumChip("Hamısı · ${ui.items.size}", ui.albumId == null) { vm.setAlbum(null) }
                        ui.albums.forEach { a -> AlbumChip("${a.name} · ${a.count}", ui.albumId == a.id) { vm.setAlbum(a.id) } }
                    }
                }
                if (shown.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.PhotoLibrary, null, tint = EColors.Faint, modifier = Modifier.size(44.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("Şəkil və ya video tapılmadı", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                items(shown, key = { it.key }) { item ->
                    GalleryTile(item, selected = item.key in ui.selected, loadThumb = vm::thumbnail, onClick = { vm.toggle(item) })
                }
            }
        }
    }
}

@Composable
private fun AlbumChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF0F3527), selectedLabelColor = EColors.Accent),
    )
}

@Composable
private fun GalleryTile(item: DeviceItem, selected: Boolean, loadThumb: suspend (DeviceItem) -> Bitmap?, onClick: () -> Unit) {
    val thumb by produceState<ImageBitmap?>(null, item.key) { value = loadThumb(item)?.asImageBitmap() }
    Box(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(EColors.Surface2)
            .border(if (selected) 2.dp else 0.dp, if (selected) EColors.Accent else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        thumb?.let { Image(it, item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (item.isVideo) {
            Icon(Icons.Outlined.PlayCircle, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(20.dp))
        }
        if (selected) Box(Modifier.matchParentSize().background(Color(0x333DDC97)))
        Box(
            Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape)
                .background(if (selected) EColors.Accent else Color(0x66070A0E))
                .border(1.5.dp, if (selected) EColors.Accent else Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Icon(Icons.Outlined.Check, null, tint = EColors.AccentInk, modifier = Modifier.size(14.dp)) }
    }
}

@Composable
private fun PermissionNeeded(modifier: Modifier, onAllow: () -> Unit, onSettings: () -> Unit) {
    Column(modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.PhotoLibrary, null, tint = EColors.Accent, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Qalereyaya giriş lazımdır", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Bütün albomlardakı şəkil və videoları seçib şifrələmək üçün telefonun \"Foto və video\" icazəsi lazımdır. " +
                "Fayllar yalnız siz seçəndən sonra, telefonda şifrələnir.",
            color = EColors.Muted, fontSize = 14.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton("İcazə ver", onAllow)
        TextButton(onClick = onSettings) { Text("Tənzimləmələri aç", color = EColors.Accent) }
        Text(
            "İş profili (Work) fayllarına bu icazə şamil olmur — onlara yalnız təşkilatınızın admini icazə verə bilər.",
            color = EColors.Faint, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
        )
    }
}
