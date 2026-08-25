package com.medsreminder.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.medsreminder.core.notification.NotificationHelper
import com.medsreminder.data.local.dao.MedicationGroupDao
import com.medsreminder.data.local.dao.PersonDao
import com.medsreminder.domain.scheduler.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Triggered by AlarmManager when a scheduled medication time is reached.
 */
class AlarmReceiver : BroadcastReceiver(), KoinComponent {

    private val groupDao: MedicationGroupDao by inject()
    private val personDao: PersonDao by inject()
    private val alarmScheduler: AlarmScheduler by inject()
    private val notificationHelper: NotificationHelper by inject()

    companion object {
        const val ACTION_FIRE_ALARM = "com.medsreminder.ACTION_FIRE_ALARM"
        const val ACTION_PRE_ALARM = "com.medsreminder.ACTION_PRE_ALARM"
        const val EXTRA_GROUP_ID = "extra_group_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_FIRE_ALARM && action != ACTION_PRE_ALARM) return

        val groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1L)
        if (groupId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val groupWithMeds = groupDao.getGroupById(groupId)
                if (groupWithMeds != null && groupWithMeds.group.isActive) {
                    val person = personDao.getPersonById(groupWithMeds.group.personId).firstOrNull()
                    val personName = person?.name ?: "Usuario"

                    val today = java.time.LocalDate.now()
                    val isAlreadyTakenToday = groupWithMeds.group.lastTakenDate?.isEqual(today) == true

                    // Check if person's alarms are currently suspended
                    val isPersonSuspended = person?.suspendedUntilEpochMs?.let { it > System.currentTimeMillis() } ?: false
                    if (isPersonSuspended || isAlreadyTakenToday) {
                        if (action == ACTION_FIRE_ALARM) {
                            // Automatically reschedule for tomorrow / next active day
                            alarmScheduler.schedule(groupWithMeds.group)
                        }
                        return@launch
                    }

                    if (action == ACTION_FIRE_ALARM) {
                        notificationHelper.showMedicationNotification(groupWithMeds, personName)
                        // Automatically schedule next occurrence for tomorrow / next active day
                        alarmScheduler.schedule(groupWithMeds.group)
                    } else if (action == ACTION_PRE_ALARM) {
                        notificationHelper.showPreAlarmNotification(
                            groupWithMeds = groupWithMeds,
                            personName = personName,
                            minutesBefore = groupWithMeds.group.advanceNoticeMinutes
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
