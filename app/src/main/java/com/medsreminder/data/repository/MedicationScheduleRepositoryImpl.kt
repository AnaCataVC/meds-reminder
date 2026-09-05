package com.medsreminder.data.repository

import com.medsreminder.core.notification.NotificationHelper
import com.medsreminder.data.local.dao.MedicationGroupDao
import com.medsreminder.domain.repository.MedicationScheduleRepository
import com.medsreminder.domain.scheduler.AlarmScheduler
import java.time.LocalDate

/**
 * Concrete implementation of MedicationScheduleRepository.
 * Single Source of Truth for resolving doses: updates Room, dismisses system notifications,
 * and recalculates exact alarms deterministically.
 */
class MedicationScheduleRepositoryImpl(
    private val groupDao: MedicationGroupDao,
    private val alarmScheduler: AlarmScheduler,
    private val notificationHelper: NotificationHelper
) : MedicationScheduleRepository {

    override suspend fun confirmIntake(groupId: Long, date: LocalDate, notificationId: Int?) {
        groupDao.markGroupAsTaken(groupId, date)
        notificationHelper.cancelAllForGroup(groupId, notificationId)

        val groupWithMeds = groupDao.getGroupById(groupId)
        if (groupWithMeds != null) {
            val updatedGroup = groupWithMeds.group.copy(lastTakenDate = date, snoozeUntilEpochMs = null)
            alarmScheduler.schedule(updatedGroup)
        }
    }

    override suspend fun snoozeSchedule(groupId: Long, snoozeTriggerEpochMs: Long, notificationId: Int?) {
        groupDao.setSnoozeTime(groupId, snoozeTriggerEpochMs)
        notificationHelper.cancelAllForGroup(groupId, notificationId)
        alarmScheduler.scheduleSnooze(groupId, snoozeTriggerEpochMs)
    }

    override suspend fun skipSchedule(groupId: Long, date: LocalDate, notificationId: Int?) {
        groupDao.markGroupSkippedToday(groupId, date)
        notificationHelper.cancelAllForGroup(groupId, notificationId)

        val groupWithMeds = groupDao.getGroupById(groupId)
        if (groupWithMeds != null) {
            val updatedGroup = groupWithMeds.group.copy(lastTakenDate = date, snoozeUntilEpochMs = null)
            alarmScheduler.schedule(updatedGroup)
        }
    }
}
