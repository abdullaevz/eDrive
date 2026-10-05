package com.edrive.app

import android.app.Application
import android.os.SystemClock
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.edrive.app.data.Session
import com.edrive.app.data.vault.FileAccessService
import com.edrive.app.data.vault.ImportService
import com.edrive.app.di.ApplicationScope
import com.edrive.app.util.AppLog
import com.edrive.app.util.CrashReporter
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class EDriveApp : Application(), Configuration.Provider {
    @Inject lateinit var crashReporter: CrashReporter
    @Inject lateinit var session: Session
    @Inject lateinit var workerFactory: HiltWorkerFactory
    // Lazy: Room/Drive qraf yalnız çökmə tutucu qurulandan SONRA yaradılsın
    @Inject lateinit var importer: Lazy<ImportService>
    @Inject lateinit var fileAccess: Lazy<FileAccessService>
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate() // Hilt komponenti və inject burada baş verir
        // Ən birinci: hətta qraf yaradılarkən baş verən çökmə də tutulsun
        crashReporter.install()
        AppLog.i("app", "Tətbiq başladı")
        // FileAccessService başlanğıcda yaradılır (init-də cache/open təmizlənir)
        appScope.launch {
            fileAccess.get()
            importer.get().recoverInterrupted()
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(AutoLock(session))
    }

    /** Tətbiq fona keçib [LOCK_AFTER_MS] müddətindən çox qalarsa, vault kilidlənir. */
    private class AutoLock(private val session: Session) : DefaultLifecycleObserver {
        private var stoppedAt = 0L
        override fun onStop(owner: LifecycleOwner) { stoppedAt = SystemClock.elapsedRealtime() }
        override fun onStart(owner: LifecycleOwner) {
            if (stoppedAt > 0 && !session.isInExternalUi && SystemClock.elapsedRealtime() - stoppedAt > LOCK_AFTER_MS) {
                AppLog.i("session", "Fonda qaldığı üçün avtomatik kilidləndi")
                session.lock()
            }
            stoppedAt = 0
        }
    }

    companion object {
        const val LOCK_AFTER_MS = 60_000L
    }
}
