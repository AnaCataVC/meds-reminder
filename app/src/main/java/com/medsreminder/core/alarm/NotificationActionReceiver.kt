package com.medsreminder.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.medsreminder.core.notification.NotificationHelper
import com.medsreminder.domain.repository.MedicationScheduleRepository
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

    private val scheduleRepository: MedicationScheduleRepository by inject()
    private val notificationHelper: NotificationHelper by inject()

    companion object {
        private const val TAG = "NotificationActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val groupId = intent.getLongExtra(NotificationHelper.EXTRA_GROUP_ID, -1L)
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)

        if (groupId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val today = LocalDate.now()

                when (action) {
                    NotificationHelper.ACTION_CONFIRM -> {
                        scheduleRepository.confirmIntake(groupId, today, notificationId)
                    }

                    NotificationHelper.ACTION_SNOOZE_10 -> {
                        val snoozeTriggerEpoch = System.currentTimeMillis() + (10 * 60 * 1000L)
                        scheduleRepository.snoozeSchedule(groupId, snoozeTriggerEpoch, notificationId)
                    }

                    NotificationHelper.ACTION_POSTPONE_6H -> {
                        val postponeTriggerEpoch = System.currentTimeMillis() + (6 * 3600 * 1000L)
                        scheduleRepository.snoozeSchedule(groupId, postponeTriggerEpoch, notificationId)
                    }

                    NotificationHelper.ACTION_CANCEL_TODAY -> {
                        scheduleRepository.skipSchedule(groupId, today, notificationId)
                    }

                    NotificationHelper.ACTION_DISMISS_PRE_ALARM -> {
                        notificationHelper.cancelNotification(notificationId)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling notification action $action for groupId=$groupId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
