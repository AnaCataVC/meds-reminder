package com.medsreminder.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.medsreminder.data.local.dao.MedicationGroupDao
import com.medsreminder.data.local.entity.MedicationGroupEntity
import com.medsreminder.domain.scheduler.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Android implementation of AlarmScheduler utilizing AlarmManager.setAlarmClock() for highest reliability.
 */
class AndroidAlarmScheduler(
    private val context: Context,
    private val groupDao: MedicationGroupDao
) : AlarmScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(group: MedicationGroupEntity) {
        if (!group.isActive) {
            cancel(group)
            return
        }
        val triggerEpochMs = calculateNextTriggerTime(group)
        setExactAlarmClock(group.id, triggerEpochMs)

        // Schedule silent pre-alarm if enabled and not currently snoozed
        val isSnoozed = group.snoozeUntilEpochMs != null && group.snoozeUntilEpochMs > System.currentTimeMillis()
        if (!isSnoozed && group.advanceNoticeMinutes > 0) {
            val preTriggerEpochMs = triggerEpochMs - (group.advanceNoticeMinutes * 60 * 1000L)
            if (preTriggerEpochMs > System.currentTimeMillis()) {
                setPreAlarm(group.id, preTriggerEpochMs)
            }
        } else {
            cancelPreAlarm(group.id)
        }
    }

    override fun scheduleSnooze(groupId: Long, triggerAtEpochMs: Long) {
        setExactAlarmClock(groupId, triggerAtEpochMs)
    }

    override fun cancel(group: MedicationGroupEntity) {
        val pendingIntent = createPendingIntent(group.id)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        cancelPreAlarm(group.id)
    }

    fun cancelPreAlarm(groupId: Long) {
        val prePendingIntent = createPreAlarmPendingIntent(groupId)
        alarmManager.cancel(prePendingIntent)
        prePendingIntent.cancel()
    }

    override suspend fun rescheduleAllActive() = withContext(Dispatchers.IO) {
        val activeGroups = groupDao.getAllActiveGroupsSync()
        for (item in activeGroups) {
            // One failing group must not leave the remaining groups unscheduled.
            runCatching { schedule(item.group) }
                .onFailure { Log.e(TAG, "Failed to schedule groupId=${item.group.id}", it) }
        }
    }

    override fun scheduleRingTimeout(groupId: Long, triggerAtEpochMs: Long) {
        setExactOrInexact(triggerAtEpochMs, createRingTimeoutPendingIntent(groupId))
    }

    override fun cancelRingTimeout(groupId: Long) {
        val pendingIntent = createRingTimeoutPendingIntent(groupId)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun setExactOrInexact(triggerEpochMs: Long, pendingIntent: PendingIntent) {
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerEpochMs, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerEpochMs, pendingIntent)
        }
    }

    private fun setExactAlarmClock(groupId: Long, triggerEpochMs: Long) {
        val pendingIntent = createPendingIntent(groupId)

        if (!canScheduleExact()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerEpochMs, pendingIntent)
            return
        }

        val showIntent = PendingIntent.getActivity(
            context,
            groupId.toInt(),
            context.packageManager.getLaunchIntentForPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerEpochMs, showIntent)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
    }

    private fun setPreAlarm(groupId: Long, preTriggerEpochMs: Long) {
        setExactOrInexact(preTriggerEpochMs, createPreAlarmPendingIntent(groupId))
    }

    private fun createRingTimeoutPendingIntent(groupId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_RING_TIMEOUT
            putExtra(AlarmReceiver.EXTRA_GROUP_ID, groupId)
        }
        return PendingIntent.getBroadcast(
            context,
            (groupId + RING_TIMEOUT_REQUEST_OFFSET).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createPendingIntent(groupId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE_ALARM
            putExtra(AlarmReceiver.EXTRA_GROUP_ID, groupId)
        }
        return PendingIntent.getBroadcast(
            context,
            groupId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createPreAlarmPendingIntent(groupId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_PRE_ALARM
            putExtra(AlarmReceiver.EXTRA_GROUP_ID, groupId)
        }
        return PendingIntent.getBroadcast(
            context,
            (groupId + 100000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Calculates the next epoch millisecond timestamp for a given group schedule based on time and days mask.
     * If an active future snooze is present in the entity, that snooze timestamp takes immediate precedence.
     */
    fun calculateNextTriggerTime(
        group: MedicationGroupEntity,
        referenceNow: LocalDateTime = LocalDateTime.now()
    ): Long {
        val referenceNowEpochMs = referenceNow.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val activeSnooze = group.snoozeUntilEpochMs
        if (activeSnooze != null && activeSnooze > referenceNowEpochMs) {
            return activeSnooze
        }

        var targetDateTime = referenceNow.toLocalDate().atTime(group.scheduledTime)

        // If medication was already marked as taken today (or in the future for this cycle),
        // or if scheduled time already passed today, evaluate starting from tomorrow
        val alreadyTakenToday = group.lastTakenDate != null && group.lastTakenDate >= targetDateTime.toLocalDate()
        if (alreadyTakenToday || targetDateTime.isBefore(referenceNow) || targetDateTime.isEqual(referenceNow)) {
            targetDateTime = targetDateTime.plusDays(1)
        }

        // Loop forward until we find an enabled day of the week
        while (!isDayEnabled(targetDateTime.dayOfWeek, group.daysOfWeekMask)) {
            targetDateTime = targetDateTime.plusDays(1)
        }

        return targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    companion object {
        private const val TAG = "AndroidAlarmScheduler"
        private const val RING_TIMEOUT_REQUEST_OFFSET = 200_000

        /**
         * Date of the dose that is due at [referenceNow]: the latest enabled occurrence of the
         * group's time at or before [referenceNow]. A dose rung late (snoozed or postponed past
         * midnight) still belongs to the day it was scheduled for, not to the day it was answered.
         */
        fun currentDoseDate(
            group: MedicationGroupEntity,
            referenceNow: LocalDateTime = LocalDateTime.now()
        ): LocalDate {
            var candidate = referenceNow.toLocalDate()
            if (candidate.atTime(group.scheduledTime).isAfter(referenceNow)) {
                candidate = candidate.minusDays(1)
            }
            while (!isDayEnabled(candidate.dayOfWeek, group.daysOfWeekMask)) {
                candidate = candidate.minusDays(1)
            }
            return candidate
        }

        // An empty mask would never match any day; treat it as "every day" instead of looping forever.
        private fun isDayEnabled(dayOfWeek: DayOfWeek, mask: Int): Boolean {
            val effectiveMask = if (mask and 127 == 0) 127 else mask
            val bit = 1 shl (dayOfWeek.value - 1)
            return (effectiveMask and bit) != 0
        }
    }
}
