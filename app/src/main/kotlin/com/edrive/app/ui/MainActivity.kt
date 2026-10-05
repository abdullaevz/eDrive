package com.edrive.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.edrive.app.AppContainer
import com.edrive.app.EDriveApp
import com.edrive.app.ui.auth.AuthRoute
import com.edrive.app.ui.home.HomeRoute
import com.edrive.app.ui.home.HomeViewModel
import com.edrive.app.ui.theme.EColors
import com.edrive.app.ui.theme.EDriveTheme
import com.edrive.app.ui.viewer.ViewerRoute

/** BiometricPrompt FragmentActivity tələb edir. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ekran görüntüsü və "son tətbiqlər" önizləməsi bloklanır — deşifrə olunmuş şəkillər sızmasın
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val container = (application as EDriveApp).container
        setContent { EDriveTheme { Root(container) } }
    }
}

@Composable
fun Root(container: AppContainer) {
    val session by container.session.state.collectAsState()
    val reporter = (LocalContext.current.applicationContext as EDriveApp).crashReporter
    var crash by remember { mutableStateOf(reporter.pending()) }
    Box(Modifier.fillMaxSize().background(EColors.Bg)) {
        val s = session
        val pendingCrash = crash
        if (pendingCrash != null) {
            val text = remember(pendingCrash) { runCatching { pendingCrash.readText() }.getOrDefault("Hesabat oxunmadı") }
            CrashRoute(
                report = text,
                fileName = "eDrive-${pendingCrash.name}",
                onDismiss = { reporter.dismiss(pendingCrash); crash = reporter.pending() },
                onExternalUi = { if (it) container.session.beginExternalUi() else container.session.endExternalUi() },
            )
        } else if (s == null) {
            AuthRoute(container)
        } else {
            // İstifadəçi dəyişəndə bütün ekran vəziyyəti sıfırlanır
            key(s.userId) {
                val nav = rememberNavController()
                val homeVm: HomeViewModel = viewModel(
                    key = "home-${s.userId}",
                    factory = viewModelFactory { initializer { HomeViewModel(container, s.userId, s.username) } },
                )
                NavHost(nav, startDestination = "home") {
                    composable("home") { HomeRoute(container, homeVm) { id -> nav.navigate("viewer/$id") } }
                    composable("viewer/{id}") { entry ->
                        ViewerRoute(container, entry.arguments?.getString("id").orEmpty()) { nav.popBackStack() }
                    }
                }
            }
        }
    }
}

tailrec fun Context.findActivity(): FragmentActivity = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("FragmentActivity tapılmadı")
}
