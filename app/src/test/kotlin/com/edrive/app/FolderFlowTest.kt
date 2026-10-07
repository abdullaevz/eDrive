package com.edrive.app

import android.app.Application
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.test.core.app.ApplicationProvider
import com.edrive.app.data.AccountException
import com.edrive.app.data.db.entity.FileStatus
import com.edrive.app.data.vault.DriveConnectionService.ConnectOutcome
import com.edrive.app.data.vault.FolderService.DeleteMode
import com.edrive.app.drive.DriveLayout
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** Real Drive alt qovluqları: yaratma, köçürmə, başqa cihazda görünmə, ad dəyişmə, silmə seçimləri. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class FolderFlowTest {
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val key = "qovluq testi üçün açar"

    @Before fun setUp() {
        shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("txt", "text/plain")
        File(ctx.filesDir, "vault").deleteRecursively()
    }

    private fun connected(drive: FakeDrive, name: String = "natiq"): TestPhone = runBlocking {
        TestPhone(ctx, drive).apply {
            register(name)
            when (val o = connection.finishConnect("token")) {
                is ConnectOutcome.NeedsNewKey -> connection.createVault(o.drive, key.toCharArray(), key.toCharArray())
                is ConnectOutcome.NeedsKey -> connection.adoptVault(o.drive, o.remote, key.toCharArray())
                is ConnectOutcome.Connected -> Unit
            }
        }
    }

    private fun note(name: String) = Uri.fromFile(File(ctx.cacheDir, name).apply { writeText("məzmun $name") })

    @Test fun foldersLiveOnDriveAndSyncToOtherPhones() = runBlocking {
        val drive = FakeDrive()
        val a = connected(drive)
        val docs = a.folders.create("Sənədlər", null)
        val tax = a.folders.create("Vergi", docs.id)
        assertThrows("eyni adda qovluq", AccountException::class.java) { runBlocking { a.folders.create("sənədlər", null) } }
        assertThrows("boş ad", AccountException::class.java) { runBlocking { a.folders.create("  ", null) } }
        val root = drive.nodes.values.single { it.name == DriveLayout.ROOT_FOLDER }
        assertEquals(root.id, drive.nodes.getValue(docs.id).parent)
        assertEquals(docs.id, drive.nodes.getValue(tax.id).parent)

        // Birbaşa qovluğa yükləmə + kökdən köçürmə (həm yüklənmiş, həm növbədəki fayl)
        a.importer.import(listOf(note("a.txt")), tax.id)
        a.importer.import(listOf(note("b.txt"), note("c.txt")))
        assertTrue(a.uploads.processQueue())
        val b = a.files().single { it.name == "b.txt" }
        a.importer.import(listOf(note("d.txt"))) // hələ yüklənməyib
        val d = a.files().single { it.name == "d.txt" }
        assertEquals(2, a.folders.moveFiles(listOf(b.id, d.id), docs.id))
        assertEquals(docs.id, drive.nodes.getValue(b.driveDataId!!).parent)
        assertEquals(docs.id, drive.nodes.getValue(b.driveMetaId!!).parent)
        assertTrue(a.uploads.processQueue())
        val dUploaded = a.files().single { it.name == "d.txt" }
        assertEquals("növbədəki fayl yeni qovluğa yüklənir", docs.id, drive.nodes.getValue(dUploaded.driveDataId!!).parent)

        // Başqa telefon: qovluqlar və faylların yerləri sinxronlaşır
        File(ctx.filesDir, "vault").deleteRecursively()
        val other = connected(drive, "ali")
        assertEquals(setOf("Sənədlər", "Vergi"), other.db.folders().all(1).map { it.name }.toSet())
        val byName = other.files().associateBy { it.name }
        assertEquals(tax.id, byName.getValue("a.txt").folderId)
        assertEquals(docs.id, byName.getValue("b.txt").folderId)
        assertNull(byName.getValue("c.txt").folderId)

        // Ad dəyişmə başqa telefonda görünür
        a.folders.rename(docs.id, "Arxiv")
        other.sync.sync()
        assertEquals("Arxiv", other.db.folders().get(1, docs.id)!!.name)
        a.close(); other.close()
    }

    @Test fun depthLimit() = runBlocking {
        val p = connected(FakeDrive())
        var parent: String? = null
        repeat(DriveLayout.MAX_DEPTH) { parent = p.folders.create("s$it", parent).id }
        assertThrows(AccountException::class.java) { runBlocking { p.folders.create("çox dərin", parent) } }
        p.close()
    }

    @Test fun deleteModes() = runBlocking {
        val drive = FakeDrive()
        val p = connected(drive)
        val empty = p.folders.create("Boş", null)
        p.folders.delete(empty.id, DeleteMode.EMPTY_ONLY)
        assertTrue(empty.id in drive.trashed)

        // Üst qovluğa köçür: fayl və alt qovluq köçür, qovluq zibilə
        val box = p.folders.create("Qutu", null)
        val inner = p.folders.create("İç", box.id)
        p.importer.import(listOf(note("x.txt")), box.id)
        assertTrue(p.uploads.processQueue())
        assertThrows(AccountException::class.java) { runBlocking { p.folders.delete(box.id, DeleteMode.EMPTY_ONLY) } }
        p.folders.delete(box.id, DeleteMode.MOVE_UP)
        assertNull(p.files().single().folderId)
        assertNull(p.db.folders().get(1, inner.id)!!.parentId)
        p.sync.sync()
        assertEquals("sinxronlaşmadan sonra da fayl yerindədir", 1, p.files().size)

        // İçindəkilərlə birlikdə: Drive zibilinə gedir, lokal izlər silinir
        p.folders.moveFiles(listOf(p.files().single().id), inner.id)
        p.folders.delete(inner.id, DeleteMode.WITH_CONTENTS)
        assertTrue(p.files().isEmpty())
        assertTrue(drive.filesNamed(".edrv").isEmpty())
        assertTrue(drive.trashed.values.any { it.name.endsWith(".edrv") })
        p.close()
    }

    @Test fun syncRepairsHalfMovedFile() = runBlocking {
        val drive = FakeDrive()
        val p = connected(drive)
        val target = p.folders.create("Hədəf", null)
        p.importer.import(listOf(note("y.txt")))
        assertTrue(p.uploads.processQueue())
        val f = p.files().single()
        val root = drive.nodes.values.single { it.name == DriveLayout.ROOT_FOLDER }
        // Köçürmə yarımçıq: yalnız .meta köçüb (.edrv kökdə qalıb)
        drive.move(f.driveMetaId!!, root.id, target.id)
        p.sync.sync()
        assertEquals(target.id, p.files().single().folderId)
        assertEquals(".edrv .meta-nın yanına çəkilir", target.id, drive.nodes.getValue(f.driveDataId!!).parent)
        assertEquals(FileStatus.SYNCED, p.files().single().status)
        p.close()
    }
}
