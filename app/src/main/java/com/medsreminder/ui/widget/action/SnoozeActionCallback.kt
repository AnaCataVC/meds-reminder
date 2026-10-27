package com.medsreminder.ui.widget.action

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.medsreminder.core.notification.NotificationHelper
import com.medsreminder.core.widget.WidgetUpdater
import com.medsreminder.data.local.dao.MedicationGroupDao
import com.medsreminder.domain.repository.MedicationScheduleRepository
import com.medsreminder.ui.widget.WidgetConstants
import com.medsreminder.ui.widget.WidgetUtils
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Background Glance action handling 1-tap 15-minute snooze from home screen widgets.
 * Operates on the alarm's scheduled time (never bringing it forward) and forces instant widget state refresh.
 */
class SnoozeActionCallback : ActionCallback, KoinComponent {

    private val scheduleRepository: MedicationScheduleRepository by inject()
    private val notificationHelper: NotificationHelper by inject()
    private val groupDao: MedicationGroupDao by inject()
    private val widgetUpdater: WidgetUpdater by inject()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val groupId = parameters[WidgetConstants.PARAM_GROUP_ID] ?: return
        val notificationId = parameters[WidgetConstants.PARAM_NOTIFICATION_ID]

        try {
            val groupWithMeds = groupDao.getGroupById(groupId)
            val groupName = groupWithMeds?.group?.name ?: "Recordatorio"

            val today = LocalDate.now()
            val now = LocalDateTime.now()
            val baseDateTime = groupWithMeds?.let {
                val currentSnooze = it.group.snoozeUntilEpochMs?.takeIf { epoch -> epoch > System.currentTimeMillis() }
                if (currentSnooze != null) {
                    Instant.ofEpochMilli(currentSnooze).atZone(ZoneId.systemDefault()).toLocalDateTime()
                } else {
                    it.group.scheduledTime.atDate(today)
                }
            } ?: now

            val targetDateTime = baseDateTime.plusMinutes(15)
            val finalDateTime = if (targetDateTime.isBefore(now)) now.plusMinutes(15) else targetDateTime
            val snoozeEpoch = finalDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val formattedTime = finalDateTime.toLocalTime().format(DateTimeFormatter.ofPattern("hh:mm a"))

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, "⏳ Pospuesto para las $formattedTime: $groupName", Toast.LENGTH_SHORT).show()
            }

            notificationHelper.cancelAllForGroup(groupId, notificationId)
            scheduleRepository.snoozeSchedule(groupId, snoozeEpoch, notificationId = notificationId)

            // Immediately force update safely on the specific widget instance clicked
            WidgetUtils.updateWidgetSafely(context, glanceId)

            widgetUpdater.notifyWidgetsChanged()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing SnoozeActionCallback for groupId=$groupId", e)
        }
    }

    companion object {
        private const val TAG = "SnoozeAction"
    }
}
