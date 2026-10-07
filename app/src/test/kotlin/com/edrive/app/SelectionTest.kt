package com.edrive.app

import com.edrive.app.data.db.entity.FileEntity
import com.edrive.app.data.db.entity.FileStatus
import com.edrive.app.ui.components.fileInfoRows
import com.edrive.app.ui.home.BatchProgress
import com.edrive.app.ui.home.driveRemovable
import com.edrive.app.ui.home.exportable
import com.edrive.app.ui.home.needsFolder
import com.edrive.app.ui.home.toggleAll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SelectionTest {
    private fun f(id: String, mime: String = "image/jpeg", status: FileStatus = FileStatus.SYNCED, w: Int = 0, h: Int = 0) =
        FileEntity(id = id, userId = 1, name = "$id.bin", mimeType = mime, size = 1000, createdAt = 0, width = w, height = h, status = status)

    private val all = listOf(
        f("a"), f("b", status = FileStatus.PENDING), f("c", status = FileStatus.UPLOADING),
        f("d", status = FileStatus.ENCRYPTING), f("e", status = FileStatus.FAILED), f("g", "application/pdf"),
    )
    private val ids = all.map { it.id }.toSet()

    @Test fun exportableOnlySyncedOrPending() =
        assertEquals(listOf("a", "b", "g"), all.exportable(ids).map { it.id })

    @Test fun driveRemovableOnlySynced() =
        assertEquals(listOf("a", "g"), all.driveRemovable(ids).map { it.id })

    @Test fun localOnlyFilesAreExportableButNotDriveRemovable() {
        val local = listOf(f("l", status = FileStatus.LOCAL))
        assertEquals(listOf("l"), local.exportable(setOf("l")).map { it.id })
        assertTrue(local.driveRemovable(setOf("l")).isEmpty())
    }

    @Test fun onlySelectedAreConsidered() =
        assertEquals(listOf("g"), all.exportable(setOf("g", "c")).map { it.id })

    @Test fun needsFolderForNonMediaOrOldAndroid() {
        assertFalse(needsFolder(listOf(f("a"), f("v", "video/mp4")), 33))
        assertTrue(needsFolder(listOf(f("a"), f("g", "application/pdf")), 33))
        assertTrue(needsFolder(listOf(f("a")), 28))
        assertFalse(needsFolder(emptyList(), 28))
    }

    @Test fun toggleAllSelectsThenClears() {
        val every = listOf("a", "b", "c")
        assertEquals(setOf("a", "b", "c"), toggleAll(setOf("a"), every))
        assertEquals(emptySet<String>(), toggleAll(setOf("a", "b", "c"), every))
        assertEquals(emptySet<String>(), toggleAll(emptySet(), emptyList()))
    }

    @Test fun batchFraction() {
        assertEquals(0f, BatchProgress("x", 0, 4).fraction, 0.0001f)
        assertEquals(0.375f, BatchProgress("x", 1, 4, 0.5f).fraction, 0.0001f)
        assertEquals(1f, BatchProgress("x", 4, 4).fraction, 0.0001f)
        assertEquals(0f, BatchProgress("x", 0, 0).fraction, 0.0001f)
    }

    @Test fun infoRowsContainKeyFields() {
        val rows = fileInfoRows(f("abc", w = 4000, h = 3000), Locale.US).toMap()
        assertEquals("abc.bin", rows["Ad"])
        assertEquals("image/jpeg", rows["Növ"])
        assertTrue(rows["Ölçü"]!!.contains("1,000 bayt"))
        assertEquals("4000 × 3000 px · 12.0 MP", rows["Ölçülər"])
        assertEquals("Google Drive-da saxlanılır", rows["Vəziyyət"])
        assertEquals("abc", rows["Fayl ID"])
        assertTrue(rows.containsKey("Şifrəli ölçü"))
    }

    @Test fun infoRowsOmitDimensionsWhenUnknown() =
        assertFalse(fileInfoRows(f("x"), Locale.US).any { it.first == "Ölçülər" })
}
