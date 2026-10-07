package com.edrive.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.edrive.app.data.AccountException
import com.edrive.app.data.PinLockedException
import com.edrive.app.data.db.entity.UserEntity
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

/** Profil (PIN, kilid, barmaq izi, 1.x keçidi) və lokal şifrələmənin uçdan-uca testi — Robolectric üzərində, Drive-sız. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VaultFlowTest {
    private lateinit var ctx: Application
    private lateinit var phone: TestPhone
    private val db get() = phone.db
    private val session get() = phone.session
    private val accounts get() = phone.accounts

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
        shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("jpg", "image/jpeg")
        shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("txt", "text/plain")
        phone = TestPhone(ctx)
    }

    @After fun tearDown() = phone.close()

    private fun login(name: String, pin: String) = runBlocking { accounts.login(name, pin.toCharArray()) }

    @Test fun registerLoginLock() = runBlocking {
        phone.register("natiq", "4826")
        assertEquals("natiq", session.current?.username)
        assertFalse("vault Drive-a qoşulana qədər yoxdur", session.current!!.hasVault)
        val user = db.users().byUsername("natiq")!!
        assertFalse("PIN açıq saxlanmamalıdır", String(user.pinHash!!, Charsets.ISO_8859_1).contains("4826"))
        assertNull(user.headerJson)

        session.lock()
        assertNull(session.current)
        val e = assertThrows(AccountException::class.java) { login("natiq", "1397") }
        assertEquals("PIN yanlışdır", e.message)
        login("NATIQ", "4826") // istifadəçi adı böyük/kiçik hərfə həssas deyil
        assertNotNull(session.current)
        assertEquals("uğurlu girişdən sonra sayğac sıfırlanır", 0, db.users().byUsername("natiq")!!.failedAttempts)
    }

    @Test fun pinLockoutGrowsAndExpires() = runBlocking {
        phone.register("natiq", "4826")
        session.lock()
        repeat(4) { assertThrows(AccountException::class.java) { login("natiq", "1397") } }
        val locked = assertThrows(PinLockedException::class.java) { login("natiq", "1397") }
        assertEquals(phone.clock.now() + 60_000, locked.untilMillis)
        // Gözləmə bitməyib — hətta düzgün PIN də qəbul edilmir
        assertThrows(PinLockedException::class.java) { login("natiq", "4826") }
        phone.clock.time += 61_000
        repeat(4) { assertThrows(AccountException::class.java) { login("natiq", "1397") } }
        val second = assertThrows(PinLockedException::class.java) { login("natiq", "1397") }
        assertEquals("ikinci pillə: 5 dəqiqə", phone.clock.now() + 5 * 60_000, second.untilMillis)
        phone.clock.time += 5 * 60_000 + 1
        login("natiq", "4826")
        assertNotNull(session.current)
    }

    @Test fun validation() {
        assertThrows(AccountException::class.java) { phone.register("ab") }
        assertThrows("bariz PIN", AccountException::class.java) { phone.register("natiq", "1234") }
        assertThrows("bariz PIN", AccountException::class.java) { phone.register("natiq", "0000") }
        assertThrows("uzunluq", AccountException::class.java) { phone.register("natiq", "48261") }
        assertThrows("uyğunsuz təkrar", AccountException::class.java) {
            runBlocking { accounts.register("natiq", "4826".toCharArray(), "4827".toCharArray()) }
        }
        phone.register("natiq")
        assertThrows(AccountException::class.java) { phone.register("natiq", "5937") }
    }

    @Test fun multipleLocalUsersAreIsolated() = runBlocking {
        phone.register("ali", "4826")
        phone.register("vali", "5937")
        session.lock()
        assertThrows(AccountException::class.java) { login("ali", "5937") }
        login("ali", "4826")
        assertEquals("ali", session.current?.username)
    }

    @Test fun biometricOnlyOpensTheApp() = runBlocking {
        phone.register("natiq")
        val activity = org.robolectric.Robolectric.buildActivity(androidx.fragment.app.FragmentActivity::class.java).setup().get()
        accounts.setBiometric(activity, true)
        session.lock()
        phone.biometric.answer = false
        assertFalse("imtina — giriş yoxdur", accounts.loginWithBiometric(activity, 1))
        assertNull(session.current)
        phone.biometric.answer = true
        assertTrue(accounts.loginWithBiometric(activity, 1))
        assertNotNull(session.current)
    }

    @Test fun vaultLoadsFromDeviceKeyAndFallsBackToSecurityKey() = runBlocking {
        phone.register("natiq")
        val userId = session.requireUser().userId
        phone.vaults.create(userId, "uzun-təhlükəsizlik-açarı".toCharArray())
        assertTrue(session.current!!.hasVault)

        session.lock()
        login("natiq", "4826")
        assertTrue("PIN-dən sonra vault cihaz açarı ilə avtomatik açılır", session.current!!.hasVault)

        phone.deviceKeys.lose(userId) // məs. telefonun təhlükəsizlik ayarları sıfırlanıb
        session.lock()
        login("natiq", "4826")
        assertFalse("profil açılır, vault isə açar tələb edir", session.current!!.hasVault)
        assertThrows(AccountException::class.java) { runBlocking { phone.vaults.unlockWithKey(userId, "yanlış-açar-123".toCharArray()) } }
        phone.vaults.unlockWithKey(userId, "uzun-təhlükəsizlik-açarı".toCharArray())
        assertTrue(session.current!!.hasVault)
    }

    /** 1.x hesabı: PIN yoxdur, vault köhnə parolla yaradılıb. Keçiddən sonra fayllar eyni açarla açılır. */
    @Test fun legacyAccountMigratesWithOldPassword() = runBlocking {
        val created = com.edrive.crypto.VaultKeys(bcKdf).create("köhnə-parol-1".toCharArray(), "2026-01-01T00:00:00Z")
        val userId = db.users().insert(UserEntity(username = "natiq", createdAt = 1, headerJson = created.header.toJson()))
        assertThrows(AccountException::class.java) { login("natiq", "4826") }

        assertThrows(AccountException::class.java) {
            runBlocking { accounts.migrateLegacy("natiq", "səhv-parol".toCharArray(), "4826".toCharArray(), "4826".toCharArray()) }
        }
        assertFalse("yanlış paroldan sonra heç nə dəyişmir", db.users().byId(userId)!!.hasPin)

        accounts.migrateLegacy("natiq", "köhnə-parol-1".toCharArray(), "4826".toCharArray(), "4826".toCharArray())
        assertArrayEquals("eyni vault, eyni DEK", created.dek, session.requireKey())
        session.lock()
        login("natiq", "4826")
        assertArrayEquals(created.dek, session.requireKey())
    }

    @Test fun importWithoutVaultIsRefused() {
        phone.register("natiq")
        assertThrows(com.edrive.app.data.VaultLockedException::class.java) {
            runBlocking { phone.importer.import(listOf(Uri.fromFile(File(ctx.cacheDir, "x.txt").apply { writeText("x") }))) }
        }
    }

    @Test fun importEncryptsAndDecryptsWithThumbnail() = runBlocking {
        phone.register("natiq")
        phone.vaults.create(session.requireUser().userId, "uzun-təhlükəsizlik-açarı".toCharArray())
        val photo = File(ctx.cacheDir, "tətil.jpg")
        Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888).also { b ->
            Canvas(b).apply { drawColor(Color.rgb(30, 120, 200)); drawCircle(600f, 400f, 250f, Paint().apply { color = Color.YELLOW }) }
            photo.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
        val doc = File(ctx.cacheDir, "qeyd.txt").apply { writeText("GİZLİ-MƏTN-MARKER ".repeat(5000)) }

        phone.importer.import(listOf(Uri.fromFile(photo), Uri.fromFile(doc)))

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
        assertArrayEquals(photo.readBytes(), phone.fileAccess.decryptToMemory(img.id))
        assertArrayEquals(doc.readBytes(), phone.fileAccess.decryptToMemory(txt.id))
        assertNotNull(phone.fileAccess.thumbnail(userId, img.id))

        // "Endir": deşifrə olunmuş nüsxə seçilmiş yerə yazılır və orijinalla eynidir
        val exported = File(ctx.cacheDir, "export.jpg")
        phone.fileAccess.exportTo(img.id, Uri.fromFile(exported))
        assertArrayEquals(photo.readBytes(), exported.readBytes())

        // Drive qoşulmayıbsa, növbə gözləyir (fayl itmir)
        phone.uploads.processQueue()
        assertEquals(FileStatus.PENDING, db.files().get(userId, img.id)!!.status)
        assertEquals("Google Drive qoşulmayıb", db.files().get(userId, img.id)!!.error)

        // Kilidlənəndə miniatür açılmır
        session.lock()
        assertNull(phone.fileAccess.thumbnail(userId, img.id))
    }
}
