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
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.edrive.app.data.Session
import com.edrive.app.util.CrashReporter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.edrive.app.ui.auth.AuthRoute
import com.edrive.app.ui.gallery.GalleryRoute
import com.edrive.app.ui.drive.DriveViewModel
import com.edrive.app.ui.home.HomeRoute
import com.edrive.app.ui.home.HomeViewModel
import com.edrive.app.ui.theme.EColors
import com.edrive.app.ui.theme.EDriveTheme
import com.edrive.app.ui.viewer.ViewerRoute

/** BiometricPrompt FragmentActivity tələb edir. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var session: Session
    @Inject lateinit var crashReporter: CrashReporter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ekran görüntüsü və "son tətbiqlər" önizləməsi bloklanır — deşifrə olunmuş şəkillər sızmasın
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent { EDriveTheme { Root(session, crashReporter) } }
    }
}

@Composable
fun Root(sessionHolder: Session, reporter: CrashReporter) {
    val session by sessionHolder.state.collectAsState()
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
                onExternalUi = { if (it) sessionHolder.beginExternalUi() else sessionHolder.endExternalUi() },
            )
        } else if (s == null) {
            AuthRoute()
        } else {
            // İstifadəçi dəyişəndə bütün ekran vəziyyəti sıfırlanır
            key(s.userId) {
                val nav = rememberNavController()
                val homeVm: HomeViewModel = hiltViewModel(key = "home-${s.userId}")
                val driveVm: DriveViewModel = hiltViewModel(key = "drive-${s.userId}")
                NavHost(nav, startDestination = "home") {
                    composable("home") { HomeRoute(homeVm, driveVm, reporter::diagnostics, onGallery = { nav.navigate("gallery") }) { id -> nav.navigate("viewer/$id") } }
                    composable("gallery") {
                        GalleryRoute(onClose = { nav.popBackStack() }, onImport = { uris -> homeVm.import(uris); nav.popBackStack() })
                    }
                    composable("viewer/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                        ViewerRoute(hiltViewModel()) { nav.popBackStack() }
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
