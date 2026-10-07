package com.edrive.app.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.edrive.app.data.AccountRepository
import com.edrive.app.ui.components.Chip
import com.edrive.app.ui.components.EField
import com.edrive.app.ui.components.PrimaryButton
import com.edrive.app.ui.components.VaultMark
import com.edrive.app.ui.findActivity
import com.edrive.app.ui.theme.EColors

@Composable
fun AuthRoute(vm: AuthViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val loadedUsers by vm.users.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()

    LaunchedEffect(loadedUsers) {
        val users = loadedUsers ?: return@LaunchedEffect
        if (users.isNotEmpty()) vm.prefillLastUser()
        else if (state.mode == AuthMode.LOGIN) vm.setMode(AuthMode.REGISTER)
    }

    // Ekran hər dəfə tam aktiv olanda (soyuq start, kilid, fondan qayıdış) barmaq izi sorğusu
    val usersLoaded = loadedUsers != null
    LaunchedEffect(usersLoaded) {
        if (!usersLoaded) return@LaunchedEffect
        activity.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.autoBiometric(activity) }
    }

    val createPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        vm.endExternalUi()
        if (uri != null) vm.saveRecovery(context, uri)
    }

    val users = loadedUsers ?: return
    val selected = users.firstOrNull { it.username.equals(state.username, ignoreCase = true) }

    AuthContent(
        state = state,
        users = users,
        biometricForSelected = selected?.biometric == true && vm.biometricAvailable,
        biometricAvailable = vm.biometricAvailable,
        onUsername = vm::setUsername,
        onPassword = vm::setPassword,
        onConfirm = vm::setConfirm,
        onMode = vm::setMode,
        onLogin = vm::login,
        onRegister = vm::register,
        onBiometric = { selected?.let { vm.biometricLogin(activity, it.id) } },
        onSaveRecovery = {
            vm.beginExternalUi()
            createPdf.launch("eDrive-${state.username}-berpa.pdf")
        },
        onToggleBiometric = vm::setEnableBiometric,
        onToggleRecoveryPdf = vm::setSaveRecoveryPdf,
        onFinish = { vm.finishRegistration(activity) },
    )
}

@Composable
fun AuthContent(
    state: AuthState,
    users: List<KnownUser>,
    biometricForSelected: Boolean,
    biometricAvailable: Boolean,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onMode: (AuthMode) -> Unit,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onBiometric: () -> Unit,
    onSaveRecovery: () -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onFinish: () -> Unit,
    onToggleRecoveryPdf: (Boolean) -> Unit = {},
) {
    Box(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0x163DDC97), Color.Transparent), radius = 1400f))
            .systemBarsPadding().imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "github.com/abdullaevz",
            color = EColors.Faint, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 12.dp),
        )
        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VaultMark(64.dp, animated = state.busy)
            Spacer(Modifier.height(14.dp))
            Text("eDrive", style = MaterialTheme.typography.headlineMedium)
            Text(
                when (state.mode) {
                    AuthMode.LOGIN -> "Şifrəli diskinizə daxil olun"
                    AuthMode.REGISTER -> "Yeni şifrəli vault yaradın"
                    AuthMode.RECOVERY -> "Son addım: bərpa sənədi"
                },
                color = EColors.Muted, modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(28.dp))

            AnimatedContent(state.mode, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "mode") { mode ->
                when (mode) {
                    AuthMode.LOGIN -> LoginForm(state, users, biometricForSelected, onUsername, onPassword, onLogin, onBiometric, onMode)
                    AuthMode.REGISTER -> RegisterForm(state, onUsername, onPassword, onConfirm, onRegister, onMode, hasUsers = users.isNotEmpty())
                    AuthMode.RECOVERY -> RecoveryStep(state, biometricAvailable, onSaveRecovery, onToggleBiometric, onFinish, onToggleRecoveryPdf)
                }
            }

            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("AES-256-GCM"); Chip("Argon2id"); Chip("Zero-knowledge")
            }
        }
    }
}

@Composable
private fun ErrorText(error: String?) {
    if (error != null) {
        Text(error, color = EColors.Danger, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
    }
}

@Composable
private fun LoginForm(
    state: AuthState,
    users: List<KnownUser>,
    biometric: Boolean,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onLogin: () -> Unit,
    onBiometric: () -> Unit,
    onMode: (AuthMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (users.isNotEmpty()) {
            Text("BU CİHAZDAKI HESABLAR", style = MaterialTheme.typography.labelSmall, color = EColors.Faint)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                users.forEach { u ->
                    FilterChip(
                        selected = u.username.equals(state.username, true),
                        onClick = { onUsername(u.username) },
                        label = { Text(u.username) },
                        leadingIcon = if (u.biometric) { { Icon(Icons.Outlined.Fingerprint, null, Modifier.size(16.dp)) } } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0F3527), selectedLabelColor = EColors.Accent,
                            selectedLeadingIconColor = EColors.Accent,
                        ),
                    )
                }
            }
        }
        EField(state.username, onUsername, "İstifadəçi adı", Icons.Outlined.Person, enabled = !state.busy)
        EField(state.password, onPassword, "Parol", Icons.Outlined.Lock, password = true, imeAction = ImeAction.Done, isError = state.error != null, enabled = !state.busy)
        ErrorText(state.error)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(
                "Daxil ol", onLogin, Modifier.weight(1f), loading = state.busy,
                enabled = state.username.isNotBlank() && state.password.length >= AccountRepository.MIN_PASSWORD,
            )
            if (biometric) {
                OutlinedButton(
                    onClick = onBiometric, enabled = !state.busy, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(52.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) { Icon(Icons.Outlined.Fingerprint, "Barmaq izi ilə daxil ol", tint = EColors.Accent, modifier = Modifier.size(26.dp)) }
            }
        }
        TextButton(onClick = { onMode(AuthMode.REGISTER) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Yeni hesab yarat", color = EColors.Accent)
        }
    }
}

@Composable
private fun RegisterForm(
    state: AuthState,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onRegister: () -> Unit,
    onMode: (AuthMode) -> Unit,
    hasUsers: Boolean,
) {
    val len = state.password.length
    val ok = len >= AccountRepository.MIN_PASSWORD
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EField(state.username, onUsername, "İstifadəçi adı", Icons.Outlined.Person, enabled = !state.busy)
        EField(
            state.password, onPassword, "Master parol", Icons.Outlined.Key, password = true, enabled = !state.busy,
            supporting = if (ok) "✓ $len simvol" else "Ən azı ${AccountRepository.MIN_PASSWORD} simvol ($len/${AccountRepository.MIN_PASSWORD})",
        )
        EField(
            state.confirm, onConfirm, "Parolu təkrarlayın", Icons.Outlined.Lock, password = true, imeAction = ImeAction.Done,
            enabled = !state.busy, isError = state.confirm.isNotEmpty() && state.confirm != state.password,
        )
        InfoBox(
            Icons.Outlined.WarningAmber, EColors.Amber,
            "Bu parol fayllarınızı şifrələyən açara çevriləcək və heç yerdə saxlanılmayacaq. Növbəti addımda bərpa sənədini yükləyəcəksiniz.",
        )
        ErrorText(state.error)
        PrimaryButton(
            "Hesab yarat", onRegister, loading = state.busy,
            enabled = state.username.length >= 3 && ok && state.confirm == state.password,
        )
        if (hasUsers) {
            TextButton(onClick = { onMode(AuthMode.LOGIN) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Artıq hesabım var", color = EColors.Accent)
            }
        }
    }
}

@Composable
private fun RecoveryStep(
    state: AuthState,
    biometricAvailable: Boolean,
    onSave: () -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onFinish: () -> Unit,
    onToggleRecoveryPdf: (Boolean) -> Unit,
) {
    val saved = state.recoverySavedAs != null
    val wantPdf = state.saveRecoveryPdf
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(EColors.Surface)
                .border(1.dp, EColors.Line, RoundedCornerShape(16.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Shield, null, tint = EColors.Accent)
                Text("Parolu itirməyin", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Parol unudulsa, Google Drive-dakı şifrəli fayllar həmişəlik açılmaz olur. " +
                    "İstifadəçi adı və parol olan PDF sənədini təhlükəsiz yerə saxlayın.",
                color = EColors.Muted, fontSize = 14.sp,
            )
            KeyValue("İstifadəçi", state.username)
            KeyValue("Parol", "•".repeat(state.password.length.coerceAtMost(16)))
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(EColors.Surface).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Download, null, tint = EColors.Accent)
            Text("Bərpa sənədini (PDF) saxla", Modifier.weight(1f).padding(start = 12.dp))
            Switch(
                checked = wantPdf, onCheckedChange = onToggleRecoveryPdf, enabled = !state.busy,
                colors = SwitchDefaults.colors(checkedTrackColor = EColors.Accent, checkedThumbColor = EColors.AccentInk),
            )
        }
        if (wantPdf) {
            if (saved) {
                InfoBox(Icons.Outlined.CheckCircle, EColors.Accent, "Saxlanıldı: ${state.recoverySavedAs}")
            }
            OutlinedButton(
                onClick = onSave, enabled = !state.busy, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Outlined.Download, null, tint = EColors.Accent)
                Spacer(Modifier.size(8.dp))
                Text(if (saved) "Yenidən saxla" else "Bərpa sənədini yüklə (PDF)", color = EColors.Text)
            }
        } else {
            InfoBox(
                Icons.Outlined.WarningAmber, EColors.Amber,
                "Sənəd saxlanılmayacaq. Parolu itirsəniz, fayllarınızı heç bir yolla bərpa etmək mümkün olmayacaq.",
            )
        }
        if (biometricAvailable) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(EColors.Surface).padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Fingerprint, null, tint = EColors.Accent)
                Text("Barmaq izi ilə giriş", Modifier.weight(1f).padding(start = 12.dp))
                Switch(
                    checked = state.enableBiometricAfter, onCheckedChange = onToggleBiometric,
                    colors = SwitchDefaults.colors(checkedTrackColor = EColors.Accent, checkedThumbColor = EColors.AccentInk),
                )
            }
        }
        ErrorText(state.error)
        PrimaryButton("Davam et", onFinish, enabled = saved || !wantPdf, loading = state.busy)
        if (wantPdf && !saved) {
            Text("Davam etmək üçün əvvəlcə sənədi saxlayın", color = EColors.Faint, fontSize = 12.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun KeyValue(k: String, v: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(k, color = EColors.Faint, fontSize = 13.sp, modifier = Modifier.weight(0.4f))
        Text(v, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.6f))
    }
}

@Composable
fun InfoBox(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.07f))
            .border(1.dp, tint.copy(alpha = 0.22f), RoundedCornerShape(12.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text, fontSize = 13.sp, color = EColors.Text.copy(alpha = 0.88f))
    }
}
