package com.edrive.app

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.room.Room
import com.edrive.app.data.AccountRepository
import com.edrive.app.data.Session
import com.edrive.app.data.db.AppDatabase
import com.edrive.app.data.vault.DriveConnectionService
import com.edrive.app.data.vault.FileAccessService
import com.edrive.app.data.vault.ImportService
import com.edrive.app.data.vault.LocalVaultStore
import com.edrive.app.data.vault.SyncService
import com.edrive.app.data.vault.ThumbnailCache
import com.edrive.app.data.vault.UploadScheduler
import com.edrive.app.data.vault.UploadService
import com.edrive.app.data.vault.WorkManagerUploadScheduler
import com.edrive.app.drive.DriveAuth
import com.edrive.app.drive.DriveClientProvider
import com.edrive.app.drive.GoogleDriveClientProvider
import com.edrive.app.security.Argon2Android
import com.edrive.app.security.BiometricKeyStore
import com.edrive.app.security.BiometricVault
import com.edrive.app.util.AppLog
import com.edrive.app.util.CrashReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class EDriveApp : Application() {
    lateinit var container: AppContainer
        private set
    lateinit var crashReporter: CrashReporter
        private set

    override fun onCreate() {
        super.onCreate()
        // Ən birinci: hətta konteyner yaradılarkən baş verən çökmə də tutulsun
        crashReporter = CrashReporter(this).also { it.install() }
        AppLog.i("app", "Tətbiq başladı")
        container = AppContainer(this)
        container.appScope.launch { container.importer.recoverInterrupted() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(AutoLock(container.session))
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

/**
 * Sadə əl ilə DI — bütün asılılıqlar burada bir dəfə qurulur (kiçik layihə üçün Hilt-dən daha şəffafdır).
 * Hər xidmət yalnız ehtiyac duyduğu asılılıqları alır; Drive-a çıxış [DriveClientProvider] interfeysi ilədir.
 */
class AppContainer(app: EDriveApp) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val session = Session()

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS) // böyük yükləmələr kəsilməsin
        .build()

    // --- Lokal məlumat və təhlükəsizlik
    val db: AppDatabase = Room.databaseBuilder(app, AppDatabase::class.java, "edrive.db").build()
    val biometric: BiometricKeyStore = BiometricVault(app)
    val accounts = AccountRepository(db.users(), Argon2Android(), session, biometric)

    // --- Google Drive
    val driveAuth = DriveAuth(app, http)
    private val drives: DriveClientProvider = GoogleDriveClientProvider(http, driveAuth)

    // --- Vault xidmətləri
    private val thumbCache = ThumbnailCache()
    private val store = LocalVaultStore(app, db.files(), thumbCache)
    val uploadScheduler: UploadScheduler = WorkManagerUploadScheduler(app)
    val sync = SyncService(session, db.users(), db.files(), store, drives)
    val connection = DriveConnectionService(session, db.users(), db.files(), accounts, driveAuth, drives, sync, uploadScheduler)
    val importer = ImportService(app, session, db.files(), store, uploadScheduler)
    val uploads = UploadService(db.users(), db.files(), store, drives, uploadScheduler)
    val fileAccess = FileAccessService(app, session, db.users(), db.files(), store, drives, thumbCache)
}
