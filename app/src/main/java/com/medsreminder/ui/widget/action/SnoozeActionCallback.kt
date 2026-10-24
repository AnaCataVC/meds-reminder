package com.medsreminder.ui.widget.action

import android.content.Context
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Background Glance action handling 1-tap 15-minute snooze from home screen widgets.
 * Delivers immediate visual Toast feedback and forces instant widget state refresh.
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
        val snoozeEpoch = System.currentTimeMillis() + (15 * 60 * 1000L) // 15 minutes

        try {
            val groupWithMeds = groupDao.getGroupById(groupId)
            val groupName = groupWithMeds?.group?.name ?: "Recordatorio"

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "⏳ Pospuesto 15 min: $groupName", Toast.LENGTH_SHORT).show()
            }

            notificationHelper.cancelAllForGroup(groupId, notificationId)
            scheduleRepository.snoozeSchedule(groupId, snoozeEpoch, notificationId = notificationId)

            runCatching { NextDoseWidget().update(context, glanceId) }
            runCatching { DailyScheduleWidget().update(context, glanceId) }

            widgetUpdater.notifyWidgetsChanged()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing SnoozeActionCallback for groupId=$groupId", e)
        }
    }

    companion object {
        private const val TAG = "SnoozeAction"
    }
}
