package com.medsreminder.data.repository

import com.medsreminder.core.notification.NotificationHelper
import com.medsreminder.core.widget.WidgetUpdater
import com.medsreminder.data.local.dao.MedicationGroupDao
import com.medsreminder.domain.repository.MedicationScheduleRepository
import com.medsreminder.domain.scheduler.AlarmScheduler
import java.time.LocalDate
import java.time.LocalTime

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
        groupDao.markAllCrossRefsTaken(groupId, date)

        val groupWithMeds = groupDao.getGroupById(groupId)
        if (groupWithMeds != null) {
            val updatedGroup = groupWithMeds.group.copy(lastTakenDate = date, lastSkippedDate = null, snoozeUntilEpochMs = null)
            alarmScheduler.schedule(updatedGroup)
        }
        widgetUpdater?.notifyWidgetsChanged()
    }

    override suspend fun confirmPartialIntake(
        groupId: Long,
        date: LocalDate,
        takenMedicationIds: List<Long>,
        skippedMedicationIds: List<Long>,
        notificationId: Int?
    ) {
        notificationHelper.cancelAllForGroup(groupId, notificationId)
        alarmScheduler.cancelRingTimeout(groupId)
        groupDao.recordPartialIntake(groupId, date, takenMedicationIds, skippedMedicationIds)

        val groupWithMeds = groupDao.getGroupById(groupId)
        if (groupWithMeds != null) {
            val updatedGroup = groupWithMeds.group.copy(lastTakenDate = date, lastSkippedDate = null, snoozeUntilEpochMs = null)
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
        groupDao.markAllCrossRefsSkipped(groupId, date)

        val groupWithMeds = groupDao.getGroupById(groupId)
        if (groupWithMeds != null) {
            val updatedGroup = groupWithMeds.group.copy(lastSkippedDate = date, lastTakenDate = null, snoozeUntilEpochMs = null)
            alarmScheduler.schedule(updatedGroup)
        }
        widgetUpdater?.notifyWidgetsChanged()
    }

    override suspend fun reactivateSchedule(groupId: Long) {
        notificationHelper.cancelAllForGroup(groupId)
        alarmScheduler.cancelRingTimeout(groupId)
        groupDao.clearGroupResolutionToday(groupId)
        groupDao.clearCrossRefResolutions(groupId)

        val groupWithMeds = groupDao.getGroupById(groupId)
        if (groupWithMeds != null) {
            val updatedGroup = groupWithMeds.group.copy(lastTakenDate = null, lastSkippedDate = null, snoozeUntilEpochMs = null)
            val now = LocalTime.now()
            if (now >= updatedGroup.scheduledTime) {
                // Scheduled time already passed today: schedule to ring in 5 seconds
                val triggerEpoch = System.currentTimeMillis() + 5_000L
                groupDao.setSnoozeTime(groupId, triggerEpoch)
                alarmScheduler.scheduleSnooze(groupId, triggerEpoch)
            } else {
                alarmScheduler.schedule(updatedGroup)
            }
        }
        widgetUpdater?.notifyWidgetsChanged()
    }
}
