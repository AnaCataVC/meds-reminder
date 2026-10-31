package com.medsreminder.core.widget

import com.medsreminder.core.alarm.AndroidAlarmScheduler
import com.medsreminder.data.local.entity.MedicationGroupWithMedications
import com.medsreminder.data.local.entity.PersonEntity
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Resolved representation of a single scheduled medication dose for AppWidgets.
 */
data class WidgetScheduleItem(
    val groupId: Long,
    val personId: Long,
    val personName: String,
    val personColorHex: String,
    val groupName: String,
    val scheduledTime: LocalTime,
    val medications: List<String>,
    val isTakenToday: Boolean,
    val isSkippedToday: Boolean = false,
    val isPartialToday: Boolean = false,
    val takenCount: Int = 0,
    val totalCount: Int = 0,
    val isSnoozed: Boolean,
    val snoozeUntilEpochMs: Long?,
    val isSuspended: Boolean
)

/**
 * Pure calculation engine resolving daily schedules and immediate next doses for Glance AppWidgets.
 */
object WidgetScheduleResolver {

    /**
     * Resolves the daily schedule items for today based on active status, weekday mask, cycle active phase,
     * and optional per-widget person filtering.
     */
    fun resolveDailySchedule(
        groupsWithMeds: List<MedicationGroupWithMedications>,
        persons: List<PersonEntity>,
        selectedPersonId: Long? = null,
        referenceNow: LocalDateTime = LocalDateTime.now()
    ): List<WidgetScheduleItem> {
        val today = referenceNow.toLocalDate()
        val referenceNowEpochMs = referenceNow.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val personMap = persons.associateBy { it.id }

        val targetPersonId = if (selectedPersonId != null && selectedPersonId > 0) selectedPersonId else null

        return groupsWithMeds
            .filter { item ->
                val group = item.group
                group.isActive &&
                        (targetPersonId == null || group.personId == targetPersonId) &&
                        AndroidAlarmScheduler.isDayEnabled(group, today)
            }
            .map { item ->
                val group = item.group
                val person = personMap[group.personId]
                val isTakenToday = group.lastTakenDate?.let { !it.isBefore(today) } == true
                val isSkippedToday = group.lastSkippedDate?.let { !it.isBefore(today) } == true
                val isSnoozed = group.snoozeUntilEpochMs != null && group.snoozeUntilEpochMs > referenceNowEpochMs
                val isSuspended = person?.suspendedUntilEpochMs?.let { it > referenceNowEpochMs } ?: false

                val crossRefMap = item.crossRefs.associateBy { it.medicationId }
                val totalCount = item.medications.size
                val takenCount = item.medications.count { med -> crossRefMap[med.id]?.lastTakenDate?.let { !it.isBefore(today) } == true }
                val isPartialToday = isTakenToday && totalCount >= 2 && takenCount in 1 until totalCount

                WidgetScheduleItem(
                    groupId = group.id,
                    personId = group.personId,
                    personName = person?.name ?: "Sin asignar",
                    personColorHex = person?.colorHex ?: "#0061A4",
                    groupName = group.name,
                    scheduledTime = group.scheduledTime,
                    medications = item.medications.map { it.name },
                    isTakenToday = isTakenToday,
                    isSkippedToday = isSkippedToday,
                    isPartialToday = isPartialToday,
                    takenCount = takenCount,
                    totalCount = totalCount,
                    isSnoozed = isSnoozed,
                    snoozeUntilEpochMs = group.snoozeUntilEpochMs,
                    isSuspended = isSuspended
                )
            }
            .sortedBy { it.scheduledTime }
    }

    /**
     * Identifies the immediate next relevant dose for the compact widget.
     * Prioritizes active snoozed doses, then the earliest untaken dose of today (overdue or upcoming).
     * Returns null if all doses today are completed or suspended.
     */
    fun resolveNextDose(
        groupsWithMeds: List<MedicationGroupWithMedications>,
        persons: List<PersonEntity>,
        selectedPersonId: Long? = null,
        referenceNow: LocalDateTime = LocalDateTime.now()
    ): WidgetScheduleItem? {
        val dailyItems = resolveDailySchedule(groupsWithMeds, persons, selectedPersonId, referenceNow)

        val pendingItems = dailyItems.filter { !it.isTakenToday && !it.isSkippedToday && !it.isSuspended }
        if (pendingItems.isEmpty()) return null

        // 1. If an active snooze is ringing or pending, give it highest priority
        val snoozedItem = pendingItems.firstOrNull { it.isSnoozed }
        if (snoozedItem != null) return snoozedItem

        // 2. Return the earliest untaken dose of today (chronological order guarantees pending overdue doses are never skipped)
        return pendingItems.firstOrNull()
    }
}
