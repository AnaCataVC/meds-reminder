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
import com.medsreminder.ui.widget.DailyScheduleWidget
import com.medsreminder.ui.widget.NextDoseWidget
import com.medsreminder.ui.widget.WidgetConstants
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Background Glance action handling 1-tap intake confirmation from home screen widgets.
 * Delivers immediate visual Toast feedback and forces instant widget state refresh.
 */
class ConfirmIntakeActionCallback : ActionCallback, KoinComponent {

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
            // Retrieve group information for clear feedback
            val groupWithMeds = groupDao.getGroupById(groupId)
            val groupName = groupWithMeds?.group?.name ?: "Medicamento"

            // Instant visual Toast feedback posted safely to main looper
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, "✅ Toma confirmada: $groupName", Toast.LENGTH_SHORT).show()
            }

            // Dismiss active notifications & audio
            notificationHelper.cancelAllForGroup(groupId, notificationId)

            // Confirm intake in Room & reschedule next alarm
            scheduleRepository.confirmIntake(groupId, notificationId = notificationId)

            // Immediately force update on the specific widget instance clicked
            runCatching { NextDoseWidget().update(context, glanceId) }
            runCatching { DailyScheduleWidget().update(context, glanceId) }

            // Broadcast update to all other active widgets
            widgetUpdater.notifyWidgetsChanged()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing ConfirmIntakeActionCallback for groupId=$groupId", e)
        }
    }

    companion object {
        private const val TAG = "ConfirmIntakeAction"
    }
}
