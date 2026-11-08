package com.medsreminder.domain.scheduler

import com.medsreminder.data.local.entity.MedicationEntity
import com.medsreminder.data.local.entity.MedicationGroupEntity
import com.medsreminder.data.local.entity.MedicationGroupWithMedications

/**
 * Domain interface for scheduling, snoozing, and canceling alarms.
 */
interface AlarmScheduler {
    fun schedule(group: MedicationGroupEntity, medications: List<MedicationEntity> = emptyList())
    fun schedule(groupWithMeds: MedicationGroupWithMedications) {
        schedule(groupWithMeds.group, groupWithMeds.medications)
    }
    fun scheduleSnooze(groupId: Long, triggerAtEpochMs: Long)
    fun cancel(group: MedicationGroupEntity)
    suspend fun rescheduleAllActive()
    fun scheduleRingTimeout(groupId: Long, triggerAtEpochMs: Long)
    fun cancelRingTimeout(groupId: Long)
}
