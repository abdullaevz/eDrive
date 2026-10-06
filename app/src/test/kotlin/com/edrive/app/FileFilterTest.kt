package com.edrive.app

import androidx.compose.runtime.saveable.SaverScope
import com.edrive.app.data.db.entity.FileEntity
import com.edrive.app.data.db.entity.FileStatus
import com.edrive.app.ui.home.FileFilter
import com.edrive.app.ui.home.FileFilterSaver
import com.edrive.app.ui.home.FileSort
import com.edrive.app.ui.home.MediaKind
import com.edrive.app.ui.home.Orientation
import com.edrive.app.ui.home.applyFilter
import com.edrive.app.ui.home.availableFormats
import com.edrive.app.ui.home.formatLabel
import com.edrive.app.ui.home.hasKnownOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FileFilterTest {
    private fun file(id: String, name: String, mime: String, size: Long, created: Long, w: Int = 0, h: Int = 0) =
        FileEntity(id, 1, name, mime, size, created, width = w, height = h, status = FileStatus.SYNCED)

    private val files = listOf(
        file("1", "b.jpg", "image/jpeg", 300, 10, 4000, 3000),
        file("2", "A.png", "image/png", 100, 40, 1080, 1920),
        file("3", "c.jpg", "image/jpeg", 200, 30, 3000, 3000),
        file("4", "clip.mp4", "video/mp4", 5_000, 20, 1920, 1080),
        file("5", "reel.mov", "video/quicktime", 9_000, 50, 1080, 1920),
        file("6", "film.mkv", "video/x-matroska", 7_000, 5),
        file("7", "doc.pdf", "application/pdf", 50, 60),
    )

    private fun ids(list: List<FileEntity>) = list.map { it.id }

    @Test fun defaultFilterReturnsSameListUntouched() {
        assertSame(files, applyFilter(files, FileFilter()))
    }

    @Test fun kindImageKeepsOnlyImages() {
        assertEquals(setOf("1", "2", "3"), ids(applyFilter(files, FileFilter(kind = MediaKind.IMAGE))).toSet())
    }

    @Test fun kindVideoKeepsOnlyVideos() {
        assertEquals(setOf("4", "5", "6"), ids(applyFilter(files, FileFilter(kind = MediaKind.VIDEO))).toSet())
    }

    @Test fun documentsNeverMatchImageOrVideo() {
        assertFalse("7" in ids(applyFilter(files, FileFilter(kind = MediaKind.IMAGE))))
        assertFalse("7" in ids(applyFilter(files, FileFilter(kind = MediaKind.VIDEO))))
    }

    @Test fun defaultSortIsNewestFirst() {
        assertEquals(listOf("2", "3", "1"), ids(applyFilter(files, FileFilter(kind = MediaKind.IMAGE))))
    }

    @Test fun sortModes() {
        val img = FileFilter(kind = MediaKind.IMAGE)
        assertEquals(listOf("1", "3", "2"), ids(applyFilter(files, img.copy(sort = FileSort.OLDEST))))
        assertEquals(listOf("1", "3", "2"), ids(applyFilter(files, img.copy(sort = FileSort.LARGEST))))
        assertEquals(listOf("2", "3", "1"), ids(applyFilter(files, img.copy(sort = FileSort.SMALLEST))))
        assertEquals(listOf("2", "1", "3"), ids(applyFilter(files, img.copy(sort = FileSort.NAME)))) // "A.png", "b.jpg", "c.jpg"
    }

    @Test fun formatFilter() {
        val r = applyFilter(files, FileFilter(kind = MediaKind.IMAGE, format = "JPG"))
        assertEquals(setOf("1", "3"), ids(r).toSet())
        assertEquals(listOf("5"), ids(applyFilter(files, FileFilter(kind = MediaKind.VIDEO, format = "MOV"))))
    }

    @Test fun orientationFilterIgnoresUnknownDimensions() {
        val vid = FileFilter(kind = MediaKind.VIDEO)
        assertEquals(listOf("4"), ids(applyFilter(files, vid.copy(orientation = Orientation.LANDSCAPE))))
        assertEquals(listOf("5"), ids(applyFilter(files, vid.copy(orientation = Orientation.PORTRAIT))))
        // "6" (ölçüsü məlum deyil) heç bir yönəlişə düşmür, amma "Hamısı"nda qalır
        assertTrue("6" in ids(applyFilter(files, vid)))
    }

    @Test fun squareCountsAsLandscapeNotPortrait() {
        val img = FileFilter(kind = MediaKind.IMAGE)
        assertTrue("3" in ids(applyFilter(files, img.copy(orientation = Orientation.LANDSCAPE))))
        assertFalse("3" in ids(applyFilter(files, img.copy(orientation = Orientation.PORTRAIT))))
    }

    @Test fun combinedFilters() {
        val r = applyFilter(files, FileFilter(MediaKind.IMAGE, "JPG", Orientation.LANDSCAPE, FileSort.LARGEST))
        assertEquals(listOf("1", "3"), ids(r))
    }

    @Test fun emptyResultWhenNothingMatches() {
        assertTrue(applyFilter(files, FileFilter(kind = MediaKind.IMAGE, format = "GIF")).isEmpty())
    }

    @Test fun availableFormatsAreCountedAndOrdered() {
        assertEquals(listOf("JPG" to 2, "PNG" to 1), availableFormats(files, MediaKind.IMAGE))
        assertEquals(listOf("MKV" to 1, "MOV" to 1, "MP4" to 1), availableFormats(files, MediaKind.VIDEO))
    }

    @Test fun orientationAvailability() {
        assertTrue(hasKnownOrientation(files, MediaKind.IMAGE))
        assertFalse(hasKnownOrientation(listOf(file("x", "a.mp4", "video/mp4", 1, 1)), MediaKind.VIDEO))
    }

    @Test fun formatLabels() {
        assertEquals("JPG", formatLabel("image/jpeg"))
        assertEquals("PNG", formatLabel("image/png"))
        assertEquals("WEBP", formatLabel("image/webp"))
        assertEquals("HEIC", formatLabel("image/heic"))
        assertEquals("MP4", formatLabel("video/mp4"))
        assertEquals("MOV", formatLabel("video/quicktime"))
        assertEquals("MKV", formatLabel("video/x-matroska"))
        assertEquals("3GP", formatLabel("video/3gpp"))
        assertEquals("WEBM", formatLabel("video/webm"))
        assertEquals("SVG", formatLabel("image/svg+xml"))
        assertEquals("?", formatLabel("broken"))
    }

    @Test fun isDefault() {
        assertTrue(FileFilter().isDefault)
        assertFalse(FileFilter(kind = MediaKind.IMAGE).isDefault)
        assertFalse(FileFilter(sort = FileSort.NAME).isDefault)
    }

    @Test fun saverRoundTrip() {
        for (f in listOf(FileFilter(), FileFilter(MediaKind.VIDEO, "MP4", Orientation.PORTRAIT, FileSort.LARGEST))) {
            val saved = with(FileFilterSaver) { SaverScope { true }.save(f) }!!
            assertEquals(f, FileFilterSaver.restore(saved))
        }
    }
}
