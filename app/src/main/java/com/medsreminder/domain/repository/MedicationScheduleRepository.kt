package com.medsreminder.domain.repository

import java.time.LocalDate

/**
 * Domain repository contract for managing medication schedule states (confirm, snooze, skip, reactivate).
 * Centralizes database updates, notification dismissals, and alarm rescheduling.
 */
interface MedicationScheduleRepository {
    suspend fun confirmIntake(groupId: Long, date: LocalDate = LocalDate.now(), notificationId: Int? = null)
    suspend fun confirmPartialIntake(
        groupId: Long,
        date: LocalDate = LocalDate.now(),
        takenMedicationIds: List<Long>,
        skippedMedicationIds: List<Long>,
        notificationId: Int? = null
    )
    suspend fun snoozeSchedule(groupId: Long, snoozeTriggerEpochMs: Long, notificationId: Int? = null)
    suspend fun cancelSnooze(groupId: Long)
    suspend fun skipSchedule(groupId: Long, date: LocalDate = LocalDate.now(), notificationId: Int? = null)
    suspend fun reactivateSchedule(groupId: Long)
}
