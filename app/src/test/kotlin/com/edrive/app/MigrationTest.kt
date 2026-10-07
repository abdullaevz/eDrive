package com.edrive.app

import android.app.Application
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.edrive.app.data.db.AppDatabase
import com.edrive.app.data.db.Migrations
import com.edrive.app.data.db.entity.FileStatus
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 1 → 2 miqrasiyası: köhnə (1.x) bazası `app/schemas/.../1.json`-dakı SQL ilə yaradılır, real məlumat yazılır,
 * sonra Room miqrasiyanı icra edir. Room açılışda sxemi v2 entity-ləri ilə özü yoxlayır (uyğunsuzluq → xəta),
 * test isə bütün sətirlərin və dəyərlərin itmədən köçdüyünü yoxlayır.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class MigrationTest {
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val dbFile: File = ctx.getDatabasePath("migration-test.db")

    @Before fun setUp() { dbFile.parentFile?.mkdirs(); dbFile.delete() }
    @After fun tearDown() { dbFile.delete() }

    /** v1 sxemi — Room-un ixrac etdiyi faylın özündən (əl ilə köçürülmüş SQL yox). */
    private fun createVersion1() {
        val schema = Json.parseToJsonElement(File("schemas/com.edrive.app.data.db.AppDatabase/1.json").readText()).jsonObject
        val database = schema.getValue("database").jsonObject
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            for (entity in database.getValue("entities").jsonArray.map { it.jsonObject }) {
                val table = entity.getValue("tableName").jsonPrimitive.content
                db.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity["indices"]?.jsonArray?.forEach { idx ->
                    db.execSQL(idx.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            database.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            db.version = 1

            db.insert("users", null, ContentValues().apply {
                put("id", 1L); put("username", "natiq"); put("headerJson", HEADER); put("createdAt", 1000L); put("lastLoginAt", 2000L)
                put("bioWrappedDek", byteArrayOf(1, 2, 3)); put("bioIv", byteArrayOf(4, 5, 6))
                put("driveEmail", "natiq@gmail.com"); put("driveRootFolderId", "root-1"); put("driveUserFolderId", "user-1")
            })
            db.insert("users", null, ContentValues().apply {
                put("id", 2L); put("username", "ali"); put("headerJson", HEADER); put("createdAt", 3000L); put("lastLoginAt", 0L)
            })
            fun file(id: String, userId: Long, status: String, dataId: String?) = ContentValues().apply {
                put("id", id); put("userId", userId); put("name", "$id.jpg"); put("mimeType", "image/jpeg"); put("size", 1234L)
                put("createdAt", 5000L); put("width", 800); put("height", 600); put("hasThumb", 1); put("status", status)
                put("progress", 1.0); put("error", null as String?); put("driveDataId", dataId); put("driveMetaId", dataId?.let { "$it-meta" })
            }
            db.insert("files", null, file("a1", 1, "SYNCED", "d-a1"))
            db.insert("files", null, file("a2", 1, "LOCAL", null))
            db.insert("files", null, file("b1", 2, "PENDING", null))
        }
    }

    @Test fun migrate1To2KeepsAllRows() = runBlocking {
        createVersion1()
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, dbFile.absolutePath)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val natiq = db.users().byUsername("natiq")!!
            assertEquals(1L, natiq.id)
            assertEquals(HEADER, natiq.headerJson)
            assertEquals(1000L, natiq.createdAt)
            assertEquals(2000L, natiq.lastLoginAt)
            assertEquals("natiq@gmail.com", natiq.driveEmail)
            assertEquals("root-1", natiq.driveRootFolderId)
            assertFalse("köhnə hesabın hələ PIN-i yoxdur", natiq.hasPin)
            assertNull("biometrik açarla sarılmış DEK köçürülmür", natiq.deviceWrappedDek)
            assertFalse(natiq.biometricEnabled)
            assertEquals(0, natiq.failedAttempts)
            assertTrue(natiq.hasVault)
            assertEquals("ali", db.users().byId(2)!!.username)

            val files1 = db.files().all(1).associateBy { it.id }
            assertEquals(setOf("a1", "a2"), files1.keys)
            assertEquals(FileStatus.SYNCED, files1.getValue("a1").status)
            assertEquals("d-a1", files1.getValue("a1").driveDataId)
            assertEquals("d-a1-meta", files1.getValue("a1").driveMetaId)
            assertEquals(800, files1.getValue("a1").width)
            assertTrue(files1.getValue("a1").hasThumb)
            assertEquals(FileStatus.LOCAL, files1.getValue("a2").status)
            assertNull("köhnə fayllar kökdədir", files1.getValue("a1").folderId)
            assertEquals(listOf("b1"), db.files().ids(2))
            assertTrue(db.folders().all(1).isEmpty())
        } finally {
            db.close()
        }
    }

    private companion object {
        const val HEADER = """{"version":1,"kdf":{"algorithm":"argon2id","memoryKiB":65536,"iterations":3,"parallelism":1,"salt":"AAAA"},"wrappedDek":"BBBB","createdAt":"2026-01-01T00:00:00Z","keyId":"00ff"}"""
    }
}
