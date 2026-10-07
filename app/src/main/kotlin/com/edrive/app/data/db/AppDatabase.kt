package com.edrive.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.edrive.app.data.db.dao.FileDao
import com.edrive.app.data.db.dao.FolderDao
import com.edrive.app.data.db.dao.UserDao
import com.edrive.app.data.db.entity.FileEntity
import com.edrive.app.data.db.entity.FolderEntity
import com.edrive.app.data.db.entity.UserEntity

/**
 * Lokal SQLite bazası (Room). Cədvəllər: `users`, `files`, `folders`.
 *
 * DİQQƏT: hər sxem dəyişikliyi üçün `version` artırılmalı və [Migrations]-a miqrasiya əlavə olunmalıdır.
 * Sxemlər `app/schemas` qovluğunda saxlanılır və repoya commit edilir (miqrasiya testləri onlardan istifadə edir).
 */
@Database(entities = [UserEntity::class, FileEntity::class, FolderEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun users(): UserDao
    abstract fun files(): FileDao
    abstract fun folders(): FolderDao

    companion object {
        const val NAME = "edrive.db"
    }
}
