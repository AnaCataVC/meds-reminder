package com.medsreminder.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.medsreminder.data.local.converter.RoomConverters
import com.medsreminder.data.local.dao.MedicationDao
import com.medsreminder.data.local.dao.MedicationGroupDao
import com.medsreminder.data.local.dao.PersonDao
import com.medsreminder.data.local.entity.MedicationEntity
import com.medsreminder.data.local.entity.MedicationGroupCrossRef
import com.medsreminder.data.local.entity.MedicationGroupEntity
import com.medsreminder.data.local.entity.PersonEntity

/**
 * Main Room Database configuration for local persistence.
 */
@Database(
    entities = [
        PersonEntity::class,
        MedicationEntity::class,
        MedicationGroupEntity::class,
        MedicationGroupCrossRef::class
    ],
    version = 7,
    exportSchema = true,
    autoMigrations = [
        androidx.room.AutoMigration(from = 1, to = 2),
        androidx.room.AutoMigration(from = 2, to = 3),
        androidx.room.AutoMigration(from = 3, to = 4),
        androidx.room.AutoMigration(from = 4, to = 5),
        androidx.room.AutoMigration(from = 5, to = 6)
    ]
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun personDao(): PersonDao
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationGroupDao(): MedicationGroupDao

    companion object {
        const val DATABASE_NAME = "meds_reminder.db"

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add frequency/cycle columns to medications
                db.execSQL("ALTER TABLE `medications` ADD COLUMN `days_of_week_mask` INTEGER NOT NULL DEFAULT 127")
                db.execSQL("ALTER TABLE `medications` ADD COLUMN `cycle_active_days` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `medications` ADD COLUMN `cycle_rest_days` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `medications` ADD COLUMN `cycle_start_date` TEXT DEFAULT NULL")

                // 2. Transfer existing schedule/cycle settings from medication_groups to their associated medications
                db.execSQL("""
                    UPDATE `medications`
                    SET `days_of_week_mask` = (
                        SELECT mg.`days_of_week_mask`
                        FROM `medication_group_cross_ref` ref
                        JOIN `medication_groups` mg ON mg.`id` = ref.`group_id`
                        WHERE ref.`medication_id` = `medications`.`id`
                        LIMIT 1
                    )
                    WHERE EXISTS (
                        SELECT 1 FROM `medication_group_cross_ref` ref
                        JOIN `medication_groups` mg ON mg.`id` = ref.`group_id`
                        WHERE ref.`medication_id` = `medications`.`id`
                    )
                """.trimIndent())

                db.execSQL("""
                    UPDATE `medications`
                    SET `cycle_active_days` = (
                        SELECT mg.`cycle_active_days`
                        FROM `medication_group_cross_ref` ref
                        JOIN `medication_groups` mg ON mg.`id` = ref.`group_id`
                        WHERE ref.`medication_id` = `medications`.`id` AND mg.`cycle_active_days` > 0
                        LIMIT 1
                    ),
                    `cycle_rest_days` = (
                        SELECT mg.`cycle_rest_days`
                        FROM `medication_group_cross_ref` ref
                        JOIN `medication_groups` mg ON mg.`id` = ref.`group_id`
                        WHERE ref.`medication_id` = `medications`.`id` AND mg.`cycle_active_days` > 0
                        LIMIT 1
                    ),
                    `cycle_start_date` = (
                        SELECT mg.`cycle_start_date`
                        FROM `medication_group_cross_ref` ref
                        JOIN `medication_groups` mg ON mg.`id` = ref.`group_id`
                        WHERE ref.`medication_id` = `medications`.`id` AND mg.`cycle_active_days` > 0
                        LIMIT 1
                    )
                    WHERE EXISTS (
                        SELECT 1 FROM `medication_group_cross_ref` ref
                        JOIN `medication_groups` mg ON mg.`id` = ref.`group_id`
                        WHERE ref.`medication_id` = `medications`.`id` AND mg.`cycle_active_days` > 0
                    )
                """.trimIndent())

                // 3. Recreate medication_groups table without days_of_week_mask, cycle_active_days, cycle_rest_days, cycle_start_date
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `medication_groups_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `person_id` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `scheduled_time` TEXT NOT NULL,
                        `ringtone_uri` TEXT,
                        `is_active` INTEGER NOT NULL,
                        `last_taken_date` TEXT,
                        `last_skipped_date` TEXT DEFAULT NULL,
                        `snooze_until_epoch_ms` INTEGER,
                        `advance_notice_minutes` INTEGER NOT NULL DEFAULT 15,
                        FOREIGN KEY(`person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO `medication_groups_new` (
                        `id`, `person_id`, `name`, `scheduled_time`, `ringtone_uri`, `is_active`,
                        `last_taken_date`, `last_skipped_date`, `snooze_until_epoch_ms`, `advance_notice_minutes`
                    )
                    SELECT `id`, `person_id`, `name`, `scheduled_time`, `ringtone_uri`, `is_active`,
                           `last_taken_date`, `last_skipped_date`, `snooze_until_epoch_ms`, `advance_notice_minutes`
                    FROM `medication_groups`
                """.trimIndent())

                db.execSQL("DROP TABLE `medication_groups`")
                db.execSQL("ALTER TABLE `medication_groups_new` RENAME TO `medication_groups`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_medication_groups_person_id` ON `medication_groups` (`person_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_medication_groups_is_active_scheduled_time` ON `medication_groups` (`is_active`, `scheduled_time`)")
            }
        }
    }
}
