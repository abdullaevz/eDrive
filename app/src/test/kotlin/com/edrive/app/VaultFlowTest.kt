package com.edrive.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.edrive.app.data.AccountException
import com.edrive.app.data.AccountRepository
import com.edrive.app.data.Session
import com.edrive.app.data.vault.FileAccessService
import com.edrive.app.data.vault.ImportService
import com.edrive.app.data.vault.LocalVaultStore
import com.edrive.app.data.vault.ThumbnailCache
import com.edrive.app.data.vault.UploadService
import com.edrive.app.data.db.AppDatabase
import com.edrive.app.data.db.entity.FileStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/** Real məntiqin (Room, şifrələmə, miniatür, sessiya) Robolectric üzərində uçdan-uca testi. Drive istisna. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VaultFlowTest {
    private lateinit var ctx: Application
    private lateinit var db: AppDatabase
    private lateinit var session: Session
    private lateinit var accounts: AccountRepository
    private lateinit var importer: ImportService
    private lateinit var uploads: UploadService
    private lateinit var fileAccess: FileAccessService

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
        shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("jpg", "image/jpeg")
        shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("txt", "text/plain")
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        session = Session()
        accounts = AccountRepository(db.users(), bcKdf, session, FakeBiometric())
        val cache = ThumbnailCache()
        val store = LocalVaultStore(ctx, db.files(), cache)
        val noDrive = FakeDrive() // bu testdə Drive qoşulmur
        importer = ImportService(ctx, session, db.files(), store) {}
        uploads = UploadService(db.users(), db.files(), store, noDrive) {}
        fileAccess = FileAccessService(ctx, session, db.users(), db.files(), store, noDrive, cache)
    }

    @After fun tearDown() = db.close()

    private fun registerAndActivate(name: String = "natiq", pw: String = "salam12345") = runBlocking {
        accounts.activate(accounts.register(name, pw.toCharArray()))
    }

    @Test fun registerLoginLock() = runBlocking {
        registerAndActivate()
        assertEquals("natiq", session.current?.username)
        val header = db.users().byUsername("natiq")!!.headerJson
        assertFalse("parol açıq saxlanmamalıdır", header.contains("salam12345"))

        session.lock()
        assertNull(session.current)
        val e = assertThrows(AccountException::class.java) { runBlocking { accounts.login("natiq", "yanlis-parol".toCharArray()) } }
        assertEquals("Parol yanlışdır", e.message)
        accounts.login("NATIQ", "salam12345".toCharArray()) // istifadəçi adı böyük/kiçik hərfə həssas deyil
        assertNotNull(session.current)
    }

    @Test fun validation() {
        assertThrows(AccountException::class.java) { runBlocking { accounts.register("ab", "salam12345".toCharArray()) } }
        assertThrows(AccountException::class.java) { runBlocking { accounts.register("natiq", "qisa".toCharArray()) } }
        registerAndActivate()
        assertThrows(AccountException::class.java) { runBlocking { accounts.register("natiq", "basqa-parol1".toCharArray()) } }
    }

    @Test fun multipleLocalUsersAreIsolated() = runBlocking {
        registerAndActivate("ali", "ali-parol-123")
        registerAndActivate("vali", "vali-parol-123")
        session.lock()
        assertThrows(AccountException::class.java) { runBlocking { accounts.login("ali", "vali-parol-123".toCharArray()) } }
        accounts.login("ali", "ali-parol-123".toCharArray())
        assertEquals("ali", session.current?.username)
    }

    @Test fun importEncryptsAndDecryptsWithThumbnail() = runBlocking {
        registerAndActivate()
        val photo = File(ctx.cacheDir, "tətil.jpg")
        Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888).also { b ->
            Canvas(b).apply { drawColor(Color.rgb(30, 120, 200)); drawCircle(600f, 400f, 250f, Paint().apply { color = Color.YELLOW }) }
            photo.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
        val doc = File(ctx.cacheDir, "qeyd.txt").apply { writeText("GİZLİ-MƏTN-MARKER ".repeat(5000)) }

        importer.import(listOf(Uri.fromFile(photo), Uri.fromFile(doc)))

        val userId = session.current!!.userId
        val files = db.files().observe(userId).first()
        assertEquals(2, files.size)
        val img = files.first { it.name == "tətil.jpg" }
        val txt = files.first { it.name == "qeyd.txt" }
        assertEquals(FileStatus.PENDING, img.status)
        assertTrue("şəkil üçün miniatür", img.hasThumb)
        assertEquals(1200, img.width)
        assertFalse(txt.hasThumb)

        // Diskdə yalnız şifrəli məlumat
        val outbox = File(ctx.filesDir, "vault/$userId/outbox")
        val enc = File(outbox, "${txt.id}.edrv").readBytes()
        assertEquals("EDRV", String(enc, 0, 4))
        assertFalse(String(enc, Charsets.ISO_8859_1).contains("MARKER"))
        assertFalse(String(File(outbox, "${txt.id}.meta").readBytes(), Charsets.ISO_8859_1).contains("qeyd"))

        // Deşifrə yaddaşda orijinalla eynidir
        assertArrayEquals(photo.readBytes(), fileAccess.decryptToMemory(img.id))
        assertArrayEquals(doc.readBytes(), fileAccess.decryptToMemory(txt.id))
        assertNotNull(fileAccess.thumbnail(userId, img.id))

        // "Endir": deşifrə olunmuş nüsxə seçilmiş yerə yazılır və orijinalla eynidir
        val exported = File(ctx.cacheDir, "export.jpg")
        fileAccess.exportTo(img.id, Uri.fromFile(exported))
        assertArrayEquals(photo.readBytes(), exported.readBytes())

        // Drive qoşulmayıbsa, növbə gözləyir (fayl itmir)
        uploads.processQueue()
        assertEquals(FileStatus.PENDING, db.files().get(img.id)!!.status)
        assertEquals("Google Drive qoşulmayıb", db.files().get(img.id)!!.error)

        // Kilidlənəndə miniatür açılmır
        session.lock()
        assertNull(fileAccess.thumbnail(userId, img.id))
    }
}
