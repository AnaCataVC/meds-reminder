package com.medsreminder.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.medsreminder.core.notification.NotificationHelper
import com.medsreminder.data.local.dao.MedicationGroupDao
import com.medsreminder.data.local.dao.PersonDao
import com.medsreminder.domain.scheduler.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.time.LocalDateTime
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
    private val alarmSettings: AlarmSettings by inject()

    companion object {
        private const val TAG = "AlarmReceiver"
        const val ACTION_FIRE_ALARM = "com.medsreminder.ACTION_FIRE_ALARM"
        const val ACTION_PRE_ALARM = "com.medsreminder.ACTION_PRE_ALARM"
        const val ACTION_RING_TIMEOUT = "com.medsreminder.ACTION_RING_TIMEOUT"
        const val EXTRA_GROUP_ID = "extra_group_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_FIRE_ALARM && action != ACTION_PRE_ALARM && action != ACTION_RING_TIMEOUT) return

        val groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1L)
        if (groupId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val groupWithMeds = groupDao.getGroupById(groupId)
                if (groupWithMeds != null && groupWithMeds.group.isActive) {
                    val person = personDao.getPersonById(groupWithMeds.group.personId).firstOrNull()
                    val personName = person?.name ?: "Usuario"

                    val group = groupWithMeds.group
                    val ringtoneUriString = group.ringtoneUriString ?: person?.ringtoneUriString
                    // A pre-alarm announces the upcoming dose; the other actions refer to the dose already due.
                    val doseDate = if (action == ACTION_PRE_ALARM) {
                        LocalDateTime.now().plusMinutes(group.advanceNoticeMinutes.toLong()).toLocalDate()
                    } else {
                        AndroidAlarmScheduler.currentDoseDate(group)
                    }
                    val isAlreadyTakenToday = group.lastTakenDate?.let { !it.isBefore(doseDate) } == true

                    // Check if person's alarms are currently suspended
                    val isPersonSuspended = person?.suspendedUntilEpochMs?.let { it > System.currentTimeMillis() } ?: false
                    if (isPersonSuspended || isAlreadyTakenToday) {
                        if (action == ACTION_FIRE_ALARM) {
                            // Automatically reschedule for tomorrow / next active day
                            alarmScheduler.schedule(groupWithMeds.group)
                        }
                        return@launch
                    }

                    if (action == ACTION_RING_TIMEOUT) {
                        // Answered in the meantime (or dismissed): nothing left to silence.
                        if (notificationHelper.isDoseNotificationActive(groupId)) {
                            notificationHelper.showMedicationNotification(
                                groupWithMeds, personName, ringtoneUriString, doseDate, silent = true
                            )
                        }
                    } else if (action == ACTION_FIRE_ALARM) {
                        notificationHelper.showMedicationNotification(groupWithMeds, personName, ringtoneUriString, doseDate)
                        val timeoutMinutes = alarmSettings.ringTimeoutMinutes
                        if (timeoutMinutes > 0) {
                            alarmScheduler.scheduleRingTimeout(
                                groupId, System.currentTimeMillis() + timeoutMinutes * 60_000L
                            )
                        }

                        // Fallback reschedule: if the user ignores the notification, ensure the next
                        // regular calendar occurrence is queued. Any active future snooze takes precedence
                        // and prevents clobbering.
                        val freshGroup = groupDao.getGroupById(groupId)?.group ?: groupWithMeds.group
                        val hasActiveSnooze = freshGroup.snoozeUntilEpochMs?.let { it > System.currentTimeMillis() } == true
                        if (!hasActiveSnooze) {
                            alarmScheduler.schedule(freshGroup)
                        }
                    } else if (action == ACTION_PRE_ALARM) {
                        notificationHelper.showPreAlarmNotification(
                            groupWithMeds = groupWithMeds,
                            personName = personName,
                            minutesBefore = groupWithMeds.group.advanceNoticeMinutes,
                            doseDate = doseDate
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing AlarmReceiver for groupId=$groupId, action=$action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
