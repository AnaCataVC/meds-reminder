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
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "suspended_until_epoch_ms")
    val suspendedUntilEpochMs: Long? = null,
    @ColumnInfo(name = "ringtone_uri")
    val ringtoneUriString: String? = null // Default alarm sound; a group's own ringtone overrides it
)

/**
 * Master catalog medication entity. Reusable pill/medicine definition with frequency and rest cycles.
 */
@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val dosage: String? = null,
    val description: String? = null,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "days_of_week_mask", defaultValue = "127")
    val daysOfWeekMask: Int = 127, // Bitmask: Mon(1)..Sun(64), 127 = Everyday
    // Rest cycle (e.g. contraceptives: 21 on / 7 off). 0 active days = no cycle.
    @ColumnInfo(name = "cycle_active_days", defaultValue = "0")
    val cycleActiveDays: Int = 0,
    @ColumnInfo(name = "cycle_rest_days", defaultValue = "0")
    val cycleRestDays: Int = 0,
    @ColumnInfo(name = "cycle_start_date")
    val cycleStartDate: LocalDate? = null
) {
    /**
     * Determines whether this medication is scheduled to be taken on [date].
     * Evaluates both the weekday bitmask and the active phase of any rest cycle.
     */
    fun isActiveOnDate(date: LocalDate): Boolean {
        // Weekday check
        val effectiveMask = if (daysOfWeekMask and 127 == 0) 127 else daysOfWeekMask
        val bit = 1 shl (date.dayOfWeek.value - 1)
        if ((effectiveMask and bit) == 0) return false

        // Cycle check
        val start = cycleStartDate ?: return true
        if (cycleActiveDays <= 0 || cycleRestDays <= 0) return true
        val cycleLength = cycleActiveDays + cycleRestDays
        val dayInCycle = Math.floorMod(java.time.temporal.ChronoUnit.DAYS.between(start, date), cycleLength.toLong())
        return dayInCycle < cycleActiveDays
    }
}

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
    @ColumnInfo(name = "last_taken_date")
    val lastTakenDate: LocalDate? = null, // Ephemeral day tracking (no log history bloat)
    @ColumnInfo(name = "last_skipped_date", defaultValue = "NULL")
    val lastSkippedDate: LocalDate? = null,
    @ColumnInfo(name = "snooze_until_epoch_ms")
    val snoozeUntilEpochMs: Long? = null,
    @ColumnInfo(name = "advance_notice_minutes", defaultValue = "15")
    val advanceNoticeMinutes: Int = 15 // 0 = disabled, 15 = 15m before, 30 = 30m before
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
    val medicationId: Long,
    @ColumnInfo(name = "last_taken_date", defaultValue = "NULL")
    val lastTakenDate: LocalDate? = null,
    @ColumnInfo(name = "last_skipped_date", defaultValue = "NULL")
    val lastSkippedDate: LocalDate? = null
)
