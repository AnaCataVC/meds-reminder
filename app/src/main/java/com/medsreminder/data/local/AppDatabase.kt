package com.medsreminder.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    version = 2,
    exportSchema = true,
    autoMigrations = [
        androidx.room.AutoMigration(from = 1, to = 2)
    ]
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun personDao(): PersonDao
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationGroupDao(): MedicationGroupDao

    companion object {
        const val DATABASE_NAME = "meds_reminder.db"
    }
}
