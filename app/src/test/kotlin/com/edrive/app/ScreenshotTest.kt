package com.edrive.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.edrive.app.data.db.entity.FileEntity
import com.edrive.app.data.db.entity.FileStatus
import com.edrive.app.data.db.entity.UserEntity
import com.edrive.app.ui.auth.AuthActions
import com.edrive.app.ui.auth.AuthContent
import com.edrive.app.ui.auth.AuthMode
import com.edrive.app.ui.auth.AuthState
import com.edrive.app.ui.auth.KnownUser
import com.edrive.app.ui.home.AccountSheetContent
import com.edrive.app.ui.home.ProblemFilesSheet
import com.edrive.app.ui.home.HomeContent
import com.edrive.app.ui.home.HomeUi
import com.edrive.app.ui.theme.EColors
import com.edrive.app.ui.theme.EDriveTheme
import com.edrive.app.ui.viewer.ViewerContent
import com.edrive.app.ui.viewer.ViewerUi
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

/** Ekranların vizual yoxlanışı (Robolectric + Roborazzi) — emulator olmadan PNG yaradır. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { EDriveTheme { Box(Modifier.fillMaxSize().background(EColors.Bg)) { content() } } }
        compose.onRoot().captureRoboImage("build/screens/$name.png")
    }

    private val noop: () -> Unit = {}
    private val users = listOf(KnownUser(1, "natiq", biometric = true, legacy = false), KnownUser(2, "köhnə", biometric = false, legacy = true))

    @Composable
    private fun auth(state: AuthState, users: List<KnownUser> = this.users) = AuthContent(
        state, users, selected = users.firstOrNull { it.username == state.username }, biometricAvailable = true, actions = AuthActions(),
    )

    @Test fun login() = shot("1-login") { auth(AuthState(username = "natiq", pin = "48")) }

    @Test fun register() = shot("2-register") {
        auth(AuthState(mode = AuthMode.REGISTER, username = "natiq", pin = "4826", pinConfirm = ""), emptyList())
    }

    @Test fun pinDocument() = shot("3-pin-document") {
        auth(AuthState(mode = AuthMode.PIN_DOCUMENT, username = "natiq", pin = "4826", pinPdfSavedAs = "eDrive-natiq-PIN.pdf", enableBiometricAfter = true))
    }

    @Test fun legacyMigration() = shot("3b-legacy") { auth(AuthState(username = "köhnə", legacyPassword = "salam12345")) }

    private val user = UserEntity(1, "natiq", createdAt = 1759600000000, headerJson = "{}", driveEmail = "natiq@gmail.com", driveRootFolderId = "r", biometricEnabled = true)

    private fun thumb(seed: Int): ImageBitmap {
        val b = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
        val palettes = listOf(0xFF1E5AA8 to 0xFF7DCFFF, 0xFF8A3B12 to 0xFFF2B64A, 0xFF1B4D3E to 0xFF3DDC97, 0xFF4A1E6B to 0xFFF7768E, 0xFF223344 to 0xFF9ECE6A)
        val (a, c) = palettes[seed % palettes.size]
        Canvas(b).apply {
            drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 240f, 240f, a.toInt(), c.toInt(), Shader.TileMode.CLAMP) })
            drawCircle(80f + seed * 23 % 120, 90f, 40f, Paint().apply { color = 0x55FFFFFF })
        }
        return b.asImageBitmap()
    }

    private val files = listOf(
        FileEntity("a1", 1, "IMG_2041.jpg", "image/jpeg", 3_400_000, 9, hasThumb = true, status = FileStatus.UPLOADING, progress = 0.62f),
        FileEntity("a2", 1, "IMG_2040.jpg", "image/jpeg", 2_900_000, 8, hasThumb = true, status = FileStatus.ENCRYPTING, progress = 0.3f),
        FileEntity("a3", 1, "tetil.mp4", "video/mp4", 48_000_000, 7, hasThumb = true, status = FileStatus.SYNCED),
        FileEntity("a4", 1, "IMG_1999.jpg", "image/jpeg", 2_100_000, 6, hasThumb = true, status = FileStatus.SYNCED),
        FileEntity("a5", 1, "Müqavilə_2026.pdf", "application/pdf", 840_000, 5, status = FileStatus.SYNCED),
        FileEntity("a6", 1, "IMG_1980.jpg", "image/jpeg", 1_800_000, 4, hasThumb = true, status = FileStatus.SYNCED),
        FileEntity("a7", 1, "backup.zip", "application/zip", 12_000_000, 3, status = FileStatus.FAILED, error = "x"),
        FileEntity("a8", 1, "IMG_1975.jpg", "image/jpeg", 1_700_000, 2, hasThumb = true, status = FileStatus.SYNCED),
        FileEntity("a9", 1, "IMG_1970.jpg", "image/jpeg", 2_200_000, 1, hasThumb = true, status = FileStatus.PENDING),
    )

    @Composable
    private fun home(user: UserEntity?, files: List<FileEntity>) { HomeContent(
        username = "natiq", user = user, files = files, ui = HomeUi(), snackbar = SnackbarHostState(),
        loadThumb = { id -> thumb(id.last().digitToInt()) },
        onAccount = noop, onLock = noop, onSync = noop, onConnect = noop, onUpload = noop, onOpen = {}, onRetry = {},
    ) }

    @Test fun homeGallery() = shot("4-home") { home(user, files) }

    @Test fun homeNotConnected() = shot("5-home-not-connected") { home(user.copy(driveRootFolderId = null, driveEmail = null), emptyList()) }

    @Test fun problemFiles() = shot("11-problems") {
        ProblemFilesSheet(
            listOf(
                files[0].copy(status = FileStatus.FAILED, error = "Drive xətası 500"),
                files[1].copy(status = FileStatus.PENDING, error = "Şəbəkə xətası — yenidən cəhd ediləcək"),
                files[2].copy(status = FileStatus.FAILED, error = "⛔ Lokal şifrəli nüsxə tapılmadı"),
            ),
            onRetry = {}, onRetryAll = noop, onSaveCopy = {}, onDelete = {},
        )
    }

    @Test fun accountSheet() = shot("6-account") {
        AccountSheetContent("natiq", user, files, driveBusy = false, syncing = false, biometricAvailable = true,
            onConnect = noop, onDisconnect = noop, onSync = noop, onBiometric = {}, onLock = noop, onExportLog = noop)
    }

    @Test fun viewerDocument() = shot("7-viewer-pdf") {
        ViewerContent(files[4], ViewerUi(), isImage = false, onBack = noop, onDelete = noop, onOpenExternal = noop)
    }

    @Test fun viewerExporting() = shot("9-viewer-export") {
        ViewerContent(files[2], ViewerUi(exporting = true, progress = 0.58f), isImage = false, onBack = noop, onDelete = noop, onOpenExternal = noop)
    }

    @Test fun crashScreen() = shot("10-crash") {
        com.edrive.app.ui.CrashContent(
            report = "eDrive — ÇÖKMƏ HESABATI\nVaxt: 05.10.2026 14:22:03\nTətbiq: 0.1.0 (1)\nCihaz: samsung SM-S918B\nAndroid: 16 (API 36)\n\n" +
                "──────── Xəta izi (stack trace) ────────\njava.lang.IllegalStateException: Sınaq çökməsi\n" +
                "\tat com.edrive.app.data.VaultRepository.import(VaultRepository.kt:212)\n\tat com.edrive.app.ui.home.HomeViewModel\$import\$1.invokeSuspend(HomeViewModel.kt:131)\n" +
                "\nI/app: Tətbiq başladı\nI/drive: Drive qoşuldu, bərpa olunan fayl: 0",
            snackbar = SnackbarHostState(), onCopy = noop, onSave = noop, onDismiss = noop,
        )
    }

    @Test fun viewerImageLoading() = shot("8-viewer-loading") {
        ViewerContent(files[3], ViewerUi(loading = true, progress = 0.45f), isImage = true, onBack = noop, onDelete = noop, onOpenExternal = noop)
    }
}
