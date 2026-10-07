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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
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
import com.edrive.app.data.PinPolicy
import com.edrive.app.ui.components.Chip
import com.edrive.app.ui.components.EField
import com.edrive.app.ui.components.PinField
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
        if (uri != null) vm.savePinDocument(context, uri)
    }

    val users = loadedUsers ?: return
    val selected = users.firstOrNull { it.username.equals(state.username, ignoreCase = true) }

    AuthContent(
        state = state,
        users = users,
        selected = selected,
        biometricAvailable = vm.biometricAvailable,
        actions = AuthActions(
            onUsername = vm::setUsername,
            onPin = vm::setPin,
            onPinConfirm = vm::setPinConfirm,
            onLegacyPassword = vm::setLegacyPassword,
            onMode = vm::setMode,
            onLogin = vm::login,
            onMigrateLegacy = vm::migrateLegacy,
            onRegister = vm::register,
            onBiometric = { selected?.let { vm.biometricLogin(activity, it.id) } },
            onSavePinPdf = {
                vm.beginExternalUi()
                createPdf.launch("eDrive-${state.username}-PIN.pdf")
            },
            onToggleSavePinPdf = vm::setSavePinPdf,
            onToggleBiometric = vm::setEnableBiometric,
            onFinish = { vm.finishRegistration(activity) },
        ),
    )
}

/** Giriş ekranının bütün hadisələri — ekran ViewModel-dən asılı olmasın (önizləmə və ekran testləri üçün). */
class AuthActions(
    val onUsername: (String) -> Unit = {},
    val onPin: (String) -> Unit = {},
    val onPinConfirm: (String) -> Unit = {},
    val onLegacyPassword: (String) -> Unit = {},
    val onMode: (AuthMode) -> Unit = {},
    val onLogin: () -> Unit = {},
    val onMigrateLegacy: () -> Unit = {},
    val onRegister: () -> Unit = {},
    val onBiometric: () -> Unit = {},
    val onSavePinPdf: () -> Unit = {},
    val onToggleSavePinPdf: (Boolean) -> Unit = {},
    val onToggleBiometric: (Boolean) -> Unit = {},
    val onFinish: () -> Unit = {},
)

@Composable
fun AuthContent(
    state: AuthState,
    users: List<KnownUser>,
    selected: KnownUser?,
    biometricAvailable: Boolean,
    actions: AuthActions,
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
                when {
                    state.mode == AuthMode.REGISTER -> "Yeni hesab yaradın"
                    state.mode == AuthMode.PIN_DOCUMENT -> "Son addım: PIN sənədi"
                    selected?.legacy == true -> "Yeni versiyaya keçid"
                    else -> "PIN ilə daxil olun"
                },
                color = EColors.Muted, modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(28.dp))

            AnimatedContent(state.mode, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "mode") { mode ->
                when (mode) {
                    AuthMode.LOGIN -> LoginForm(state, users, selected, actions)
                    AuthMode.REGISTER -> RegisterForm(state, actions, hasUsers = users.isNotEmpty())
                    AuthMode.PIN_DOCUMENT -> PinDocumentStep(state, biometricAvailable, actions)
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
private fun LoginForm(state: AuthState, users: List<KnownUser>, selected: KnownUser?, actions: AuthActions) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (users.isNotEmpty()) {
            Text("BU CİHAZDAKI HESABLAR", style = MaterialTheme.typography.labelSmall, color = EColors.Faint)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                users.forEach { u ->
                    FilterChip(
                        selected = u.username.equals(state.username, true),
                        onClick = { actions.onUsername(u.username) },
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
        EField(state.username, actions.onUsername, "İstifadəçi adı", Icons.Outlined.Person, enabled = !state.busy)
        if (selected?.legacy == true) {
            LegacyFields(state, actions)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PinField(
                    state.pin, actions.onPin, "PIN", Modifier.weight(1f),
                    imeAction = ImeAction.Done, isError = state.error != null, enabled = !state.busy,
                )
                if (selected?.biometric == true) {
                    OutlinedButton(
                        onClick = actions.onBiometric, enabled = !state.busy, shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(56.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) { Icon(Icons.Outlined.Fingerprint, "Barmaq izi ilə daxil ol", tint = EColors.Accent, modifier = Modifier.size(26.dp)) }
                }
            }
            ErrorText(state.error)
            PrimaryButton(
                "Daxil ol", actions.onLogin, loading = state.busy,
                enabled = state.username.isNotBlank() && state.pin.length == PinPolicy.LENGTH,
            )
        }
        TextButton(onClick = { actions.onMode(AuthMode.REGISTER) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Yeni hesab yarat", color = EColors.Accent)
        }
    }
}

/** 1.x hesabı: köhnə parol bir dəfə soruşulur və yeni PIN təyin edilir. Vault və fayllar dəyişmir. */
@Composable
private fun LegacyFields(state: AuthState, actions: AuthActions) {
    InfoBox(
        Icons.Outlined.Info, EColors.Accent,
        "Yeni versiyada proqram 4 rəqəmli PIN ilə açılır. Köhnə parolunuz isə artıq \"Təhlükəsizlik açarı\"dır — " +
            "o, fayllarınızı qoruyur və yalnız Drive-a yeni cihazdan qoşulanda soruşulur.",
    )
    EField(state.legacyPassword, actions.onLegacyPassword, "Köhnə parol", Icons.Outlined.Key, password = true, enabled = !state.busy)
    PinField(state.pin, actions.onPin, "Yeni PIN (4 rəqəm)", enabled = !state.busy)
    PinField(
        state.pinConfirm, actions.onPinConfirm, "PIN-i təkrarlayın", imeAction = ImeAction.Done, enabled = !state.busy,
        isError = state.pinConfirm.length == PinPolicy.LENGTH && state.pinConfirm != state.pin,
    )
    ErrorText(state.error)
    PrimaryButton(
        "Keçidi tamamla", actions.onMigrateLegacy, loading = state.busy,
        enabled = state.legacyPassword.isNotEmpty() && state.pin.length == PinPolicy.LENGTH && state.pinConfirm == state.pin,
    )
}

@Composable
private fun RegisterForm(state: AuthState, actions: AuthActions, hasUsers: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EField(state.username, actions.onUsername, "İstifadəçi adı", Icons.Outlined.Person, enabled = !state.busy)
        PinField(state.pin, actions.onPin, "PIN (4 rəqəm)", enabled = !state.busy)
        PinField(
            state.pinConfirm, actions.onPinConfirm, "PIN-i təkrarlayın", imeAction = ImeAction.Done, enabled = !state.busy,
            isError = state.pinConfirm.length == PinPolicy.LENGTH && state.pinConfirm != state.pin,
        )
        InfoBox(
            Icons.Outlined.Info, EColors.Accent,
            "PIN yalnız bu telefonda proqramı açır. Fayllarınızı qoruyan Təhlükəsizlik açarını Google Drive-a qoşulanda təyin edəcəksiniz.",
        )
        ErrorText(state.error)
        PrimaryButton(
            "Hesab yarat", actions.onRegister, loading = state.busy,
            enabled = state.username.length >= 3 && state.pin.length == PinPolicy.LENGTH && state.pinConfirm == state.pin,
        )
        if (hasUsers) {
            TextButton(onClick = { actions.onMode(AuthMode.LOGIN) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Artıq hesabım var", color = EColors.Accent)
            }
        }
    }
}

@Composable
private fun PinDocumentStep(state: AuthState, biometricAvailable: Boolean, actions: AuthActions) {
    val saved = state.pinPdfSavedAs != null
    val wantPdf = state.savePinPdf
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(EColors.Surface)
                .border(1.dp, EColors.Line, RoundedCornerShape(16.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Shield, null, tint = EColors.Accent)
                Text("PIN-i yadda saxlayın", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "PIN heç yerdə açıq saxlanılmır. İstifadəçi adı və PIN olan sənədi gizli yerdə saxlayın.",
                color = EColors.Muted, fontSize = 14.sp,
            )
            KeyValue("İstifadəçi", state.username)
            KeyValue("PIN", "•".repeat(state.pin.length))
        }
        SwitchRow(Icons.Outlined.Download, "PIN sənədini (PDF) saxla", wantPdf, actions.onToggleSavePinPdf, enabled = !state.busy)
        if (wantPdf) {
            if (saved) InfoBox(Icons.Outlined.CheckCircle, EColors.Accent, "Saxlanıldı: ${state.pinPdfSavedAs}")
            OutlinedButton(
                onClick = actions.onSavePinPdf, enabled = !state.busy, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Outlined.Download, null, tint = EColors.Accent)
                Spacer(Modifier.size(8.dp))
                Text(if (saved) "Yenidən saxla" else "PIN sənədini yüklə (PDF)", color = EColors.Text)
            }
        }
        if (biometricAvailable) {
            SwitchRow(Icons.Outlined.Fingerprint, "Barmaq izi ilə giriş", state.enableBiometricAfter, actions.onToggleBiometric)
        }
        ErrorText(state.error)
        PrimaryButton("Davam et", actions.onFinish, enabled = saved || !wantPdf, loading = state.busy)
        if (wantPdf && !saved) {
            Text("Davam etmək üçün əvvəlcə sənədi saxlayın", color = EColors.Faint, fontSize = 12.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SwitchRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(EColors.Surface).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = EColors.Accent)
        Text(text, Modifier.weight(1f).padding(start = 12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = EColors.Accent, checkedThumbColor = EColors.AccentInk),
        )
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
