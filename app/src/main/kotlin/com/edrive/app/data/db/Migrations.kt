package com.edrive.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Sxem miqrasiyaları. Hər biri Room tərəfindən tək tranzaksiyada icra olunur:
 * xəta olarsa, hamısı geri qaytarılır və baza köhnə vəziyyətdə qalır.
 */
object Migrations {

    /**
     * 1 → 2 (1.2.0): PIN qapısı, Drive-a qoşulanda yaranan vault, qovluqlar.
     *
     * `users`: PIN sahələri əlavə olunur; `headerJson` boş ola bilir; biometrik açarla sarılmış DEK
     * (`bio*`) köçürülmür — yeni modeldə DEK biometrikdən asılı olmayan cihaz açarı ilə sarılır və
     * köhnə hesab ilk girişdə köhnə parolla bir dəfə açılır. `driveUserFolderId` silinir (kök qovluq istifadə olunur).
     * `files`: açar (userId, id) olur, `folderId` əlavə olunur. Bütün sətirlər olduğu kimi köçürülür.
     * `folders`: yeni cədvəl.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE `users_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `username` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `lastLoginAt` INTEGER NOT NULL, `pinHash` BLOB, `pinSalt` BLOB, " +
                    "`failedAttempts` INTEGER NOT NULL, `lockedUntil` INTEGER NOT NULL, `biometricEnabled` INTEGER NOT NULL, " +
                    "`headerJson` TEXT, `deviceWrappedDek` BLOB, `deviceIv` BLOB, `driveEmail` TEXT, `driveRootFolderId` TEXT)",
            )
            db.execSQL(
                "INSERT INTO `users_new` (id, username, createdAt, lastLoginAt, pinHash, pinSalt, failedAttempts, lockedUntil, " +
                    "biometricEnabled, headerJson, deviceWrappedDek, deviceIv, driveEmail, driveRootFolderId) " +
                    "SELECT id, username, createdAt, lastLoginAt, NULL, NULL, 0, 0, 0, headerJson, NULL, NULL, driveEmail, driveRootFolderId " +
                    "FROM `users`",
            )
            db.execSQL("DROP TABLE `users`")
            db.execSQL("ALTER TABLE `users_new` RENAME TO `users`")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_username` ON `users` (`username`)")

            db.execSQL(
                "CREATE TABLE `files_new` (`id` TEXT NOT NULL, `userId` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                    "`mimeType` TEXT NOT NULL, `size` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `width` INTEGER NOT NULL, " +
                    "`height` INTEGER NOT NULL, `hasThumb` INTEGER NOT NULL, `status` TEXT NOT NULL, `progress` REAL NOT NULL, " +
                    "`error` TEXT, `driveDataId` TEXT, `driveMetaId` TEXT, `folderId` TEXT, PRIMARY KEY(`userId`, `id`))",
            )
            db.execSQL(
                "INSERT INTO `files_new` (id, userId, name, mimeType, size, createdAt, width, height, hasThumb, status, progress, " +
                    "error, driveDataId, driveMetaId, folderId) " +
                    "SELECT id, userId, name, mimeType, size, createdAt, width, height, hasThumb, status, progress, " +
                    "error, driveDataId, driveMetaId, NULL FROM `files`",
            )
            db.execSQL("DROP TABLE `files`")
            db.execSQL("ALTER TABLE `files_new` RENAME TO `files`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_files_userId_folderId` ON `files` (`userId`, `folderId`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `folders` (`id` TEXT NOT NULL, `userId` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                    "`parentId` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`userId`, `id`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_userId_parentId` ON `folders` (`userId`, `parentId`)")
        }
    }

    val ALL = arrayOf(MIGRATION_1_2)
}
