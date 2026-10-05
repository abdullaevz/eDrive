package com.edrive.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.edrive.app.data.db.dao.FileDao
import com.edrive.app.data.db.dao.UserDao
import com.edrive.app.data.db.entity.FileEntity
import com.edrive.app.data.db.entity.UserEntity

/**
 * Lokal SQLite bazası (Room). Cədvəllər: `users`, `files`.
 * Sinflərin fayl/paket yeri dəyişsə də, cədvəl adları eyni qaldığı üçün miqrasiya tələb olunmur.
 */
@Database(entities = [UserEntity::class, FileEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun users(): UserDao
    abstract fun files(): FileDao
}
