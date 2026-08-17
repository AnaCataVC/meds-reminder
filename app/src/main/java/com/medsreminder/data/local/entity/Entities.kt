package com.medsreminder.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

/**
 * Person profile entity. Represents an individual for whom medication schedules are created.
 */
@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "color_hex")
    val colorHex: String,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

/**
 * Master catalog medication entity. Reusable pill/medicine definition.
 */
@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val dosage: String? = null,
    val description: String? = null,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

/**
 * Medication group entity. Represents a scheduled reminder with an exact time and optional ringtone sound.
 */
@Entity(
    tableName = "medication_groups",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["person_id"]),
        Index(value = ["is_active", "scheduled_time"])
    ]
)
data class MedicationGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "person_id")
    val personId: Long,
    val name: String,
    @ColumnInfo(name = "scheduled_time")
    val scheduledTime: LocalTime,
    @ColumnInfo(name = "ringtone_uri")
    val ringtoneUriString: String? = null,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "days_of_week_mask")
    val daysOfWeekMask: Int = 127, // Bitmask: Mon(1)..Sun(64), 127 = Everyday
    @ColumnInfo(name = "last_taken_date")
    val lastTakenDate: LocalDate? = null, // Ephemeral day tracking (no log history bloat)
    @ColumnInfo(name = "snooze_until_epoch_ms")
    val snoozeUntilEpochMs: Long? = null
)

/**
 * Many-to-many cross-reference table between MedicationGroup and Medication.
 */
@Entity(
    tableName = "medication_group_cross_ref",
    primaryKeys = ["group_id", "medication_id"],
    foreignKeys = [
        ForeignKey(
            entity = MedicationGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medication_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["group_id"]),
        Index(value = ["medication_id"])
    ]
)
data class MedicationGroupCrossRef(
    @ColumnInfo(name = "group_id")
    val groupId: Long,
    @ColumnInfo(name = "medication_id")
    val medicationId: Long
)
