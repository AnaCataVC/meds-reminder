package com.medsreminder.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.medsreminder.core.notification.NotificationHelper
import com.medsreminder.data.local.dao.MedicationGroupDao
import com.medsreminder.domain.scheduler.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDate

/**
 * Handles interactive actions clicked directly from standard Android notifications.
 */
class NotificationActionReceiver : BroadcastReceiver(), KoinComponent {

    private val groupDao: MedicationGroupDao by inject()
    private val alarmScheduler: AlarmScheduler by inject()
    private val notificationHelper: NotificationHelper by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val groupId = intent.getLongExtra(NotificationHelper.EXTRA_GROUP_ID, -1L)
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)

        if (groupId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val groupWithMeds = groupDao.getGroupById(groupId)
                val group = groupWithMeds?.group ?: return@launch
                val today = LocalDate.now()

                when (action) {
                    NotificationHelper.ACTION_CONFIRM -> {
                        // Mark today's dose as completed
                        groupDao.markGroupAsTaken(groupId, today)
                        notificationHelper.cancelNotification(notificationId)
                        notificationHelper.cancelNotification(groupId.toInt())
                        notificationHelper.cancelNotification(groupId.toInt() + 100000)
                        // Schedule next regular alarm cycle with updated date
                        val updatedGroup = group.copy(lastTakenDate = today, snoozeUntilEpochMs = null)
                        alarmScheduler.schedule(updatedGroup)
                    }

                    NotificationHelper.ACTION_SNOOZE_10 -> {
                        // Snooze alarm for 10 minutes
                        val snoozeTriggerEpoch = System.currentTimeMillis() + (10 * 60 * 1000L)
                        groupDao.setSnoozeTime(groupId, snoozeTriggerEpoch)
                        notificationHelper.cancelNotification(notificationId)
                        alarmScheduler.scheduleSnooze(groupId, snoozeTriggerEpoch)
                    }

                    NotificationHelper.ACTION_POSTPONE_6H -> {
                        // Postpone alarm for 6 hours
                        val postponeTriggerEpoch = System.currentTimeMillis() + (6 * 3600 * 1000L)
                        groupDao.setSnoozeTime(groupId, postponeTriggerEpoch)
                        notificationHelper.cancelNotification(notificationId)
                        alarmScheduler.scheduleSnooze(groupId, postponeTriggerEpoch)
                    }

                    NotificationHelper.ACTION_CANCEL_TODAY -> {
                        // Skip dose for today without changing overall active state
                        groupDao.markGroupSkippedToday(groupId, today)
                        notificationHelper.cancelNotification(notificationId)
                        // Also dismiss main notification if active
                        notificationHelper.cancelNotification(groupId.toInt())
                        alarmScheduler.schedule(group)
                    }

                    NotificationHelper.ACTION_DISMISS_PRE_ALARM -> {
                        // Just dismiss the advance notification; exact alarm will still ring at scheduled time
                        notificationHelper.cancelNotification(notificationId)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
