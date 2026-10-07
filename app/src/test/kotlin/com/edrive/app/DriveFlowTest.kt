package com.edrive.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.test.core.app.ApplicationProvider
import com.edrive.app.data.AccountException
import com.edrive.app.data.db.entity.FileStatus
import com.edrive.app.data.db.entity.UserEntity
import com.edrive.app.data.vault.DriveConnectionService.ConnectOutcome
import com.edrive.app.data.vault.LegacyLayoutException
import com.edrive.app.data.vault.RemoteVaultMonitor
import com.edrive.app.data.vault.UploadService
import com.edrive.app.drive.DriveLayout
import com.edrive.crypto.VaultHeader
import com.edrive.crypto.VaultKeys
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Google Drive axınının uçdan-uca testi — saxta Drive ilə (internet olmadan): vault-un Drive-a qoşulanda yaranması,
 * şifrələmə → yükləmə → yeni telefonda açarla bərpa → silmə → açar dəyişmə → ayrılma, 1.x quruluşundan keçid.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DriveFlowTest {

    private val ctx: Application = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("jpg", "image/jpeg")
        shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("txt", "text/plain")
        wipeLocalStorage()
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

    private fun connectNew(phone: TestPhone, key: String): ConnectOutcome = runBlocking {
        val outcome = phone.connection.finishConnect("token")
        assertTrue("Drive-da vault yoxdur — yeni açar soruşulmalıdır", outcome is ConnectOutcome.NeedsNewKey)
        phone.connection.createVault((outcome as ConnectOutcome.NeedsNewKey).drive, key.toCharArray(), key.toCharArray())
        ConnectOutcome.Connected("natiq@gmail.com", 0)
    }

    private fun connectExisting(phone: TestPhone, key: String): ConnectOutcome = runBlocking {
        val outcome = phone.connection.finishConnect("token") as ConnectOutcome.NeedsKey
        phone.connection.adoptVault(outcome.drive, outcome.remote, key.toCharArray())
    }

    private fun remoteHeader(drive: FakeDrive): VaultHeader {
        val root = drive.nodes.values.single { it.name == DriveLayout.ROOT_FOLDER && it.folder }
        return VaultHeader.fromJson(drive.nodes.values.single { it.name == DriveLayout.VAULT_FILE && it.parent == root.id }.bytes.decodeToString())
    }

    @Test fun fullDriveLifecycle() = runBlocking {
        val drive = FakeDrive()
        val key = "dəniz kənarında dörd ağac"

        // ---------------- 1-ci telefon: qeydiyyat (vault yoxdur) → Drive-a qoşulma → açar təyin edilir
        val phoneA = TestPhone(ctx, drive)
        phoneA.register("natiq")
        val first = phoneA.connection.finishConnect("token") as ConnectOutcome.NeedsNewKey
        assertNull("açar təyin olunana qədər profilə heç nə yazılmır", phoneA.db.users().byUsername("natiq")!!.driveEmail)
        assertThrows("qısa açar", AccountException::class.java) {
            runBlocking { phoneA.connection.createVault(first.drive, "qisa".toCharArray(), "qisa".toCharArray()) }
        }
        phoneA.connection.createVault(first.drive, key.toCharArray(), key.toCharArray())
        assertTrue(phoneA.session.current!!.hasVault)
        assertEquals(remoteHeader(drive).keyId, phoneA.vaults.header(phoneA.session.requireUser().userId)!!.keyId)
        assertTrue(phoneA.db.users().byUsername("natiq")!!.isDriveReady)

        // ---------------- Şifrələ + yüklə (kökə)
        val photo = samplePhoto()
        val note = File(ctx.cacheDir, "qeyd.txt").apply { writeText("GİZLİ-MARKER ".repeat(3000)) }
        phoneA.importer.import(listOf(Uri.fromFile(photo), Uri.fromFile(note)))
        assertTrue(phoneA.uploads.processQueue())
        assertTrue(phoneA.files().all { it.status == FileStatus.SYNCED && it.driveDataId != null && it.driveMetaId != null })

        // Drive-da yalnız şifrəli məlumat var: fayl adları və məzmun görünmür
        assertEquals(2, drive.filesNamed(".edrv").size)
        drive.nodes.values.filter { !it.folder && it.name != DriveLayout.VAULT_FILE }.forEach { n ->
            val raw = String(n.bytes, Charsets.ISO_8859_1)
            assertFalse(raw.contains("MARKER"))
            assertFalse(raw.contains("qeyd.txt"))
            assertFalse(n.name.contains("qeyd") || n.name.contains("deniz"))
        }
        assertFalse("açar Drive-a yazılmır", String(drive.nodes.values.single { it.name == DriveLayout.VAULT_FILE }.bytes).contains("dəniz"))

        // ---------------- 2-ci telefon: istənilən adla hesab → Drive-a qoşulma → açar → bərpa
        wipeLocalStorage()
        val phoneB = TestPhone(ctx, drive)
        phoneB.register("ali", "5937")
        val outcome = phoneB.connection.finishConnect("token") as ConnectOutcome.NeedsKey
        assertThrows(AccountException::class.java) {
            runBlocking { phoneB.connection.adoptVault(outcome.drive, outcome.remote, "yanlış açar 12345".toCharArray()) }
        }
        assertNull(phoneB.db.users().byUsername("ali")!!.driveEmail)
        val restored = phoneB.connection.adoptVault(outcome.drive, outcome.remote, key.toCharArray())
        assertEquals(2, (restored as ConnectOutcome.Connected).imported)

        val filesB = phoneB.files()
        val photoB = filesB.single { it.name == "deniz.jpg" }
        assertNotNull("miniatür Drive-dakı manifestdən bərpa olunmalıdır", phoneB.fileAccess.thumbnail(photoB.userId, photoB.id))
        assertArrayEquals(photo.readBytes(), phoneB.fileAccess.decryptToMemory(photoB.id))
        assertArrayEquals(note.readBytes(), phoneB.fileAccess.decryptToMemory(filesB.single { it.name == "qeyd.txt" }.id))

        // PIN ilə yenidən giriş: açar soruşulmur, vault cihaz açarı ilə açılır
        phoneB.session.lock()
        phoneB.accounts.login("ali", "5937".toCharArray())
        assertArrayEquals(photo.readBytes(), phoneB.fileAccess.decryptToMemory(photoB.id))

        // ---------------- 2-ci telefonda silmə → 1-ci telefon sinxronizasiyada görür
        phoneB.fileAccess.delete(photoB.id)
        assertEquals(1, drive.filesNamed(".edrv").size)
        phoneA.sync.sync()
        assertEquals(listOf("qeyd.txt"), phoneA.files().map { it.name })

        // ---------------- 1-ci telefonda açar dəyişir → 2-ci telefon açılışda görür, işləməyə davam edir
        val newKey = "yeni uzun ifadə ilə açar"
        assertThrows(AccountException::class.java) {
            runBlocking { phoneA.connection.changeKey("səhv köhnə açar".toCharArray(), newKey.toCharArray(), newKey.toCharArray()) }
        }
        val before = remoteHeader(drive)
        phoneA.connection.changeKey(key.toCharArray(), newKey.toCharArray(), newKey.toCharArray())
        val after = remoteHeader(drive)
        assertEquals("DEK eyni qalır", before.keyId, after.keyId)
        assertNotEquals(before.wrappedDek, after.wrappedDek)
        assertEquals(after, phoneA.vaults.header(phoneA.session.requireUser().userId))

        val userB = phoneB.db.users().byUsername("ali")!!
        assertEquals(RemoteVaultMonitor.Status.KEY_CHANGED, phoneB.monitor.check(userB))
        assertEquals(after, phoneB.vaults.header(userB.id))
        val noteB = phoneB.files().single()
        assertArrayEquals(note.readBytes(), phoneB.fileAccess.decryptToMemory(noteB.id))

        // ---------------- Ayrılma: lokal keş və vault silinir, Drive toxunulmaz qalır
        phoneB.importer.import(listOf(Uri.fromFile(samplePhoto())))
        assertEquals("yüklənməmiş fayl xəbərdarlığı", 1, phoneB.connection.unsyncedCount())
        phoneB.connection.disconnect()
        assertEquals(listOf("fake-token"), drive.revoked)
        assertEquals(listOf("natiq@gmail.com"), drive.forgotten)
        val gone = phoneB.db.users().byUsername("ali")!!
        assertNull(gone.driveRootFolderId)
        assertNull(gone.headerJson)
        assertTrue(phoneB.files().isEmpty())
        assertFalse(phoneB.session.current!!.hasVault)
        assertEquals("Drive-dakı fayl qalır", 1, drive.filesNamed(".edrv").size)

        // Yenidən qoşulma — yeni açarla
        val again = connectExisting(phoneB, newKey) as ConnectOutcome.Connected
        assertEquals(1, again.imported)
        phoneA.close(); phoneB.close()
    }

    @Test fun twoLocalProfilesShareOneDrive() = runBlocking {
        val drive = FakeDrive()
        val phone = TestPhone(ctx, drive)
        phone.register("natiq")
        connectNew(phone, "ortaq vault açarı 2026")
        phone.importer.import(listOf(Uri.fromFile(samplePhoto())))
        assertTrue(phone.uploads.processQueue())
        val id = phone.files().single().id

        phone.register("ali", "5937")
        assertEquals(1, (connectExisting(phone, "ortaq vault açarı 2026") as ConnectOutcome.Connected).imported)
        assertEquals("eyni fayl ID-si iki profildə ayrı sətir kimi", id, phone.files().single().id)
        assertEquals(1, phone.db.files().ids(1).size)
        phone.close()
    }

    @Test fun uploadStopsWhenDriveVaultWasReplaced() = runBlocking {
        val drive = FakeDrive()
        val phone = TestPhone(ctx, drive)
        phone.register("natiq")
        connectNew(phone, "birinci vault açarı")
        // Başqa cihaz Drive-dakı vault.json-u tamam başqa vault ilə əvəz edib
        val foreign = VaultKeys(bcKdf).create("başqa vault açarı".toCharArray(), "now").header
        drive.nodes.values.single { it.name == DriveLayout.VAULT_FILE }.bytes = foreign.toJson().toByteArray()

        phone.importer.import(listOf(Uri.fromFile(samplePhoto())))
        phone.uploads.processQueue()
        val f = phone.files().single()
        assertEquals(FileStatus.FAILED, f.status)
        assertEquals(UploadService.VAULT_MISMATCH, f.error)
        assertEquals("heç nə yüklənmədi", 0, drive.filesNamed(".edrv").size)
        phone.close()
    }

    @Test fun missingVaultJsonIsRestoredFromLocalCopy() = runBlocking {
        val drive = FakeDrive()
        val phone = TestPhone(ctx, drive)
        phone.register("natiq")
        connectNew(phone, "bərpa yoxlaması açarı")
        val original = remoteHeader(drive)
        drive.nodes.values.removeAll { it.name == DriveLayout.VAULT_FILE }
        assertEquals(RemoteVaultMonitor.Status.RESTORED, phone.monitor.check(phone.db.users().byUsername("natiq")!!))
        assertEquals(original, remoteHeader(drive))
        phone.close()
    }

    /** 1.x quruluşu: `eDrive Storage/natiq/vault.json` + fayllar. Keçiddən sonra vault.json kökə, fayllar qovluqda qalır. */
    @Test fun legacyLayoutMovesOnlyVaultJson() = runBlocking {
        val drive = FakeDrive()
        val password = "köhnə-parol-1"
        // ---- 1.x telefonunun Drive-a yazdıqlarını təqlid edirik
        val old = TestPhone(ctx, drive)
        old.register("natiq")
        connectNew(old, password)
        old.importer.import(listOf(Uri.fromFile(samplePhoto())))
        assertTrue(old.uploads.processQueue())
        val root = drive.nodes.values.single { it.name == DriveLayout.ROOT_FOLDER }
        val legacy = drive.folder("natiq", root.id)
        drive.nodes.values.filter { !it.folder && it.parent == root.id }.forEach { it.parent = legacy.id }
        val header = old.vaults.header(1)!!.toJson()
        old.close()

        // ---- Yeni versiyada: köhnə hesab (PIN yoxdur), keçid, qoşulma
        wipeLocalStorage()
        val phone = TestPhone(ctx, drive)
        phone.db.users().insert(UserEntity(username = "natiq", createdAt = 1, headerJson = header))
        phone.accounts.migrateLegacy("natiq", password.toCharArray(), "4826".toCharArray(), "4826".toCharArray())
        val r = phone.connection.finishConnect("token") as ConnectOutcome.Connected
        assertEquals(1, r.imported)
        assertNotNull("vault.json kökə köçürülüb", drive.nodes.values.singleOrNull { it.name == DriveLayout.VAULT_FILE && it.parent == root.id })
        assertEquals("fayl köhnə qovluqda qalır", legacy.id, phone.files().single().folderId)
        assertEquals(listOf("natiq"), phone.db.folders().all(1).map { it.name })

        // ---- Başqa vault-a aid köhnə quruluş: yeni vault yaradılmır, aydın xəta
        val other = TestPhone(ctx, FakeDrive().also { d ->
            val r2 = d.folder(DriveLayout.ROOT_FOLDER, null)
            val f = d.folder("vali", r2.id)
            runBlocking { d.uploadSmall(DriveLayout.VAULT_FILE, f.id, header.toByteArray(), "application/json") }
        })
        other.register("ali", "5937")
        assertThrows(LegacyLayoutException::class.java) { runBlocking { other.connection.finishConnect("token") } }
        phone.close(); other.close()
    }
}
