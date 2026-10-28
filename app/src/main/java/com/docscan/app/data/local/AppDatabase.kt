package com.docscan.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FolderEntity::class, DocumentEntity::class, PageEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun folderDao(): FolderDao
}

/** v2: folders, plus per-page crop, brightness/contrast and signature. Existing scans are kept. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `folders` " +
                "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "ALTER TABLE `documents` ADD COLUMN `folderId` INTEGER " +
                "REFERENCES `folders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_folderId` ON `documents` (`folderId`)")
        db.execSQL("ALTER TABLE `pages` ADD COLUMN `crop` TEXT")
        db.execSQL("ALTER TABLE `pages` ADD COLUMN `brightness` REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `pages` ADD COLUMN `contrast` REAL NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `pages` ADD COLUMN `signaturePath` TEXT")
        db.execSQL("ALTER TABLE `pages` ADD COLUMN `signatureX` REAL")
        db.execSQL("ALTER TABLE `pages` ADD COLUMN `signatureY` REAL")
        db.execSQL("ALTER TABLE `pages` ADD COLUMN `signatureWidth` REAL")
    }
}
