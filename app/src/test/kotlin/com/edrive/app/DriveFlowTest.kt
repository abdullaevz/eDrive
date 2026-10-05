package com.edrive.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.edrive.app.data.AccountException
import com.edrive.app.data.AccountRepository
import com.edrive.app.data.Session
import com.edrive.app.data.db.AppDatabase
import com.edrive.app.data.db.entity.FileStatus
import com.edrive.app.data.vault.DriveConnectionService
import com.edrive.app.data.vault.DriveConnectionService.ConnectOutcome
import com.edrive.app.data.vault.FileAccessService
import com.edrive.app.data.vault.ImportService
import com.edrive.app.data.vault.LocalVaultStore
import com.edrive.app.data.vault.SyncService
import com.edrive.app.data.vault.ThumbnailCache
import com.edrive.app.data.vault.UploadService
import com.edrive.app.drive.DriveLayout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Bütün Google Drive axınının uçdan-uca testi — saxta Drive ilə (internet olmadan):
 * qoşulma → şifrələmə → yükləmə → yeni telefonda bərpa → silmə → sinxronizasiya.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DriveFlowTest {

    private val ctx: Application = ApplicationProvider.getApplicationContext()

    /** Bir telefonu təqlid edir: öz bazası, öz sessiyası, eyni (paylaşılan) saxta Drive. */
    private inner class Phone(drive: FakeDrive) {
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        val session = Session()
        val accounts = AccountRepository(db.users(), bcKdf, session, FakeBiometric())
        private val cache = ThumbnailCache()
        private val store = LocalVaultStore(ctx, db.files(), cache)
        var scheduled = 0
        private val scheduler = { scheduled++; Unit }
        val sync = SyncService(session, db.users(), db.files(), store, drive)
        val connection = DriveConnectionService(session, db.users(), db.files(), accounts, drive, drive, sync, scheduler)
        val importer = ImportService(ctx, session, db.files(), store, scheduler)
        val uploads = UploadService(db.users(), db.files(), store, drive, scheduler)
        val fileAccess = FileAccessService(ctx, session, db.users(), db.files(), store, drive, cache)

        fun register(name: String, pw: String) = runBlocking { accounts.activate(accounts.register(name, pw.toCharArray())) }
        fun files() = runBlocking { db.files().observe(session.requireUser().userId).first() }
    }

    /** Yeni telefona keçidi təqlid edir: lokal vault faylları və keş silinir. */
    private fun wipeLocalStorage() {
        File(ctx.filesDir, "vault").deleteRecursively()
        File(ctx.cacheDir, "blobs").deleteRecursively()
    }

    private fun samplePhoto(): File {
        val f = File(ctx.cacheDir, "deniz.jpg")
        Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).also { b ->
            Canvas(b).drawColor(Color.rgb(20, 90, 160))
            f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
        return f
    }

    @Test fun fullDriveLifecycle() = runBlocking {
        shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("jpg", "image/jpeg")
        shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("txt", "text/plain")
        val drive = FakeDrive()

        // ---------------- 1-ci telefon: qeydiyyat + Drive-a qoşulma
        val phoneA = Phone(drive)
        phoneA.register("natiq", "salam12345")
        val connected = phoneA.connection.finishConnect("token")
        assertEquals(ConnectOutcome.Connected("natiq@gmail.com", 0), connected)
        val root = drive.nodes.values.single { it.name == DriveLayout.ROOT_FOLDER && it.folder }
        val userFolder = drive.nodes.values.single { it.name == "natiq" && it.parent == root.id }
        assertNotNull("vault.json Drive-a yazılmalıdır", drive.nodes.values.singleOrNull { it.name == DriveLayout.VAULT_FILE && it.parent == userFolder.id })

        // ---------------- Şifrələ + yüklə
        val photo = samplePhoto()
        val note = File(ctx.cacheDir, "qeyd.txt").apply { writeText("GİZLİ-MARKER ".repeat(3000)) }
        phoneA.importer.import(listOf(Uri.fromFile(photo), Uri.fromFile(note)))
        assertTrue(phoneA.uploads.processQueue())
        assertTrue(phoneA.files().all { it.status == FileStatus.SYNCED && it.driveDataId != null && it.driveMetaId != null })

        // Drive-da yalnız şifrəli məlumat var: fayl adları və məzmun görünmür
        assertEquals(2, drive.filesNamed(".edrv").size)
        assertEquals(2, drive.filesNamed(".meta").size)
        drive.nodes.values.filter { !it.folder && it.name != DriveLayout.VAULT_FILE }.forEach { n ->
            val raw = String(n.bytes, Charsets.ISO_8859_1)
            assertFalse(raw.contains("MARKER"))
            assertFalse(raw.contains("qeyd.txt"))
            assertFalse(n.name.contains("qeyd") || n.name.contains("deniz"))
        }

        // ---------------- 2-ci telefon (və ya tətbiq yenidən quruldu): eyni ad + parol → Drive-dan bərpa
        wipeLocalStorage()
        val phoneB = Phone(drive)
        phoneB.register("natiq", "salam12345") // yeni lokal vault yaranır (fərqli açar)
        val outcome = phoneB.connection.finishConnect("token")
        assertTrue("Drive-da köhnə vault tapılmalıdır", outcome is ConnectOutcome.NeedsPassword)
        val remote = (outcome as ConnectOutcome.NeedsPassword).remote

        assertThrows(AccountException::class.java) {
            runBlocking { phoneB.connection.adoptRemote(remote, "yanlis-parol".toCharArray()) }
        }
        val restored = phoneB.connection.adoptRemote(remote, "salam12345".toCharArray())
        assertEquals(2, (restored as ConnectOutcome.Connected).imported)

        val filesB = phoneB.files()
        assertEquals(setOf("deniz.jpg", "qeyd.txt"), filesB.map { it.name }.toSet())
        val photoB = filesB.single { it.name == "deniz.jpg" }
        assertTrue(photoB.hasThumb)
        assertNotNull("miniatür Drive-dakı manifestdən bərpa olunmalıdır", phoneB.fileAccess.thumbnail(photoB.userId, photoB.id))
        // Tam fayl Drive-dan endirilib deşifrə olunur və orijinalla eynidir
        assertArrayEquals(photo.readBytes(), phoneB.fileAccess.decryptToMemory(photoB.id))
        assertArrayEquals(note.readBytes(), phoneB.fileAccess.decryptToMemory(filesB.single { it.name == "qeyd.txt" }.id))

        // ---------------- 2-ci telefonda silmə → 1-ci telefon sinxronizasiyada görür
        phoneB.fileAccess.delete(photoB.id)
        assertEquals(1, drive.filesNamed(".edrv").size)
        assertNull(phoneB.files().firstOrNull { it.id == photoB.id })

        phoneA.sync.sync()
        assertEquals(listOf("qeyd.txt"), phoneA.files().map { it.name })

        // ---------------- Ayırma
        phoneB.connection.disconnect()
        assertEquals(listOf("fake-token"), drive.revoked)
        assertEquals("Google-un yadda saxladığı hesab icazəsi də silinməlidir", listOf("natiq@gmail.com"), drive.forgotten)
        assertNull(phoneB.db.users().byUsername("natiq")!!.driveUserFolderId)

        phoneA.db.close(); phoneB.db.close()
    }

    @Test fun differentPasswordCannotTakeOverExistingDriveVault() = runBlocking {
        val drive = FakeDrive()
        val phoneA = Phone(drive)
        phoneA.register("natiq", "salam12345")
        phoneA.connection.finishConnect("token")

        wipeLocalStorage()
        val phoneB = Phone(drive)
        phoneB.register("natiq", "basqa-parol-99")
        val outcome = phoneB.connection.finishConnect("token") as ConnectOutcome.NeedsPassword
        assertThrows(AccountException::class.java) {
            runBlocking { phoneB.connection.adoptRemote(outcome.remote, "basqa-parol-99".toCharArray()) }
        }
        phoneA.db.close(); phoneB.db.close()
    }
}
