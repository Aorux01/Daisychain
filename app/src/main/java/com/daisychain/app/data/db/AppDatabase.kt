package com.daisychain.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ShortcutEntity::class, FolderEntity::class, AutomationEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shortcutDao(): ShortcutDao
    abstract fun folderDao(): FolderDao
    abstract fun automationDao(): AutomationDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "daisychain.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `automations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `triggerType` TEXT NOT NULL, `param` TEXT NOT NULL, `shortcutId` INTEGER NOT NULL, " +
                        "`askFirst` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`shortcutId`) REFERENCES `shortcuts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_automations_shortcutId` ON `automations` (`shortcutId`)")
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shortcuts ADD COLUMN actionsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }
    }
}
