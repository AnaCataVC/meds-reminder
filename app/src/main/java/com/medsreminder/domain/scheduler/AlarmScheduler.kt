package com.medsreminder.domain.scheduler

import com.medsreminder.data.local.entity.MedicationGroupEntity

/**
 * Domain interface for scheduling, snoozing, and canceling alarms.
 */
interface AlarmScheduler {
    fun schedule(group: MedicationGroupEntity)
    fun scheduleSnooze(groupId: Long, triggerAtEpochMs: Long)
    fun cancel(group: MedicationGroupEntity)
    suspend fun rescheduleAllActive()
}
