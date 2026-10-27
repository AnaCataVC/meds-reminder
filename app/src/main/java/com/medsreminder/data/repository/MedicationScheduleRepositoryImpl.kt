package com.medsreminder.data.repository

import com.medsreminder.core.notification.NotificationHelper
import com.medsreminder.core.widget.WidgetUpdater
import com.medsreminder.data.local.dao.MedicationGroupDao
import com.medsreminder.domain.repository.MedicationScheduleRepository
import com.medsreminder.domain.scheduler.AlarmScheduler
import java.time.LocalDate

/**
 * Concrete implementation of MedicationScheduleRepository.
 * Single Source of Truth for resolving doses: updates Room, dismisses system notifications,
 * recalculates exact alarms deterministically, and broadcasts reactive updates to AppWidgets.
 */
class MedicationScheduleRepositoryImpl(
    private val groupDao: MedicationGroupDao,
    private val alarmScheduler: AlarmScheduler,
    private val notificationHelper: NotificationHelper,
    private val widgetUpdater: WidgetUpdater? = null
) : MedicationScheduleRepository {

    override suspend fun confirmIntake(groupId: Long, date: LocalDate, notificationId: Int?) {
        notificationHelper.cancelAllForGroup(groupId, notificationId)
        alarmScheduler.cancelRingTimeout(groupId)
        groupDao.markGroupAsTaken(groupId, date)

        val groupWithMeds = groupDao.getGroupById(groupId)
        if (groupWithMeds != null) {
            val updatedGroup = groupWithMeds.group.copy(lastTakenDate = date, snoozeUntilEpochMs = null)
            alarmScheduler.schedule(updatedGroup)
        }
        widgetUpdater?.notifyWidgetsChanged()
    }

    override suspend fun snoozeSchedule(groupId: Long, snoozeTriggerEpochMs: Long, notificationId: Int?) {
        notificationHelper.cancelAllForGroup(groupId, notificationId)
        alarmScheduler.cancelRingTimeout(groupId)
        groupDao.setSnoozeTime(groupId, snoozeTriggerEpochMs)
        alarmScheduler.scheduleSnooze(groupId, snoozeTriggerEpochMs)
        widgetUpdater?.notifyWidgetsChanged()
    }

    override suspend fun cancelSnooze(groupId: Long) {
        notificationHelper.cancelAllForGroup(groupId)
        alarmScheduler.cancelRingTimeout(groupId)
        groupDao.setSnoozeTime(groupId, null)

        val groupWithMeds = groupDao.getGroupById(groupId)
        if (groupWithMeds != null) {
            val updatedGroup = groupWithMeds.group.copy(snoozeUntilEpochMs = null)
            alarmScheduler.schedule(updatedGroup)
        }
        widgetUpdater?.notifyWidgetsChanged()
    }

    override suspend fun skipSchedule(groupId: Long, date: LocalDate, notificationId: Int?) {
        notificationHelper.cancelAllForGroup(groupId, notificationId)
        alarmScheduler.cancelRingTimeout(groupId)
        groupDao.markGroupSkippedToday(groupId, date)

        val groupWithMeds = groupDao.getGroupById(groupId)
        if (groupWithMeds != null) {
            val updatedGroup = groupWithMeds.group.copy(lastTakenDate = date, snoozeUntilEpochMs = null)
            alarmScheduler.schedule(updatedGroup)
        }
        widgetUpdater?.notifyWidgetsChanged()
    }
}
