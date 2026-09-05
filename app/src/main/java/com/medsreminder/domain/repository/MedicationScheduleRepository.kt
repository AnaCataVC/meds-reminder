package com.medsreminder.domain.repository

import java.time.LocalDate

/**
 * Domain repository contract for managing medication schedule states (confirm, snooze, skip).
 * Centralizes database updates, notification dismissals, and alarm rescheduling.
 */
interface MedicationScheduleRepository {
    suspend fun confirmIntake(groupId: Long, date: LocalDate = LocalDate.now(), notificationId: Int? = null)
    suspend fun snoozeSchedule(groupId: Long, snoozeTriggerEpochMs: Long, notificationId: Int? = null)
    suspend fun skipSchedule(groupId: Long, date: LocalDate = LocalDate.now(), notificationId: Int? = null)
}
