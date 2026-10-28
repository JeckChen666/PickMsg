package com.pickmsg.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [NotificationRecord::class, RuleInfo::class, Scene::class, PlanTask::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(RuleConverters::class)
abstract class PickMsgDatabase : RoomDatabase() {

    abstract fun notificationRecordDao(): NotificationRecordDao

    abstract fun ruleDao(): RuleDao

    abstract fun sceneDao(): SceneDao

    abstract fun planTaskDao(): PlanTaskDao

    companion object {

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `rule_info` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`enable` INTEGER NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "`matchAppMode` TEXT NOT NULL, " +
                        "`matchApps` TEXT NOT NULL, " +
                        "`matchTitleMode` TEXT NOT NULL, " +
                        "`matchTitleKeywords` TEXT NOT NULL, " +
                        "`matchTitleKeywordsA` TEXT NOT NULL, " +
                        "`matchTitleKeywordsB` TEXT NOT NULL, " +
                        "`matchTextMode` TEXT NOT NULL, " +
                        "`matchTextKeywords` TEXT NOT NULL, " +
                        "`matchTextKeywordsA` TEXT NOT NULL, " +
                        "`matchTextKeywordsB` TEXT NOT NULL, " +
                        "`titleOrText` INTEGER NOT NULL, " +
                        "`formatOps` TEXT NOT NULL, " +
                        "`actions` TEXT NOT NULL)",
                )
                db.execSQL("ALTER TABLE `notification_record` ADD COLUMN `ruleId` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `notification_record` ADD COLUMN `ruleName` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `notification_record` ADD COLUMN `actionResults` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_notification_record_ruleId` " +
                        "ON `notification_record` (`ruleId`)",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `rule_info` ADD COLUMN `scenesIds` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE `rule_info` ADD COLUMN `sceneMatchMode` TEXT NOT NULL DEFAULT 'ANY'")
                db.execSQL("ALTER TABLE `notification_record` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_notification_record_isPinned_postTime` " +
                        "ON `notification_record` (`isPinned`, `postTime`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scene` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`enable` INTEGER NOT NULL, " +
                        "`conditions` TEXT NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plan_task` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`time` TEXT NOT NULL, " +
                        "`weekdays` INTEGER NOT NULL, " +
                        "`notifyTitle` TEXT NOT NULL, " +
                        "`notifyContent` TEXT NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "`lastTriggerAt` INTEGER NOT NULL)",
                )
            }
        }

        fun create(context: Context): PickMsgDatabase =
            Room.databaseBuilder(context.applicationContext, PickMsgDatabase::class.java, "pickmsg.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
    }
}
