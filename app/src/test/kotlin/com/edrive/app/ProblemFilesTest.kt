package com.edrive.app

import com.edrive.app.data.db.entity.FileEntity
import com.edrive.app.data.db.entity.FileStatus
import com.edrive.app.ui.home.problems
import org.junit.Assert.assertEquals
import org.junit.Test

class ProblemFilesTest {
    private fun f(id: String, status: FileStatus, error: String? = null) =
        FileEntity(id, 1, "$id.jpg", "image/jpeg", 1, 1, status = status, error = error)

    @Test fun onlyFailedOrPendingWithError() {
        val list = listOf(
            f("a", FileStatus.FAILED, "x"), f("b", FileStatus.PENDING), f("c", FileStatus.PENDING, "Şəbəkə xətası"),
            f("d", FileStatus.SYNCED), f("e", FileStatus.LOCAL), f("g", FileStatus.UPLOADING),
        )
        assertEquals(listOf("a", "c"), list.problems().map { it.id })
    }
}
