package com.medsreminder.ui.widget.action

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.medsreminder.core.notification.NotificationHelper
import com.medsreminder.core.widget.WidgetUpdater
import com.medsreminder.domain.repository.MedicationScheduleRepository
import com.medsreminder.ui.widget.WidgetConstants
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Background Glance action handling 1-tap 15-minute snooze from home screen widgets.
 */
class SnoozeActionCallback : ActionCallback, KoinComponent {

    private val scheduleRepository: MedicationScheduleRepository by inject()
    private val notificationHelper: NotificationHelper by inject()
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
            notificationHelper.cancelAllForGroup(groupId, notificationId)
            scheduleRepository.snoozeSchedule(groupId, snoozeEpoch, notificationId = notificationId)
            widgetUpdater.notifyWidgetsChanged()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing SnoozeActionCallback for groupId=$groupId", e)
        }
    }

    companion object {
        private const val TAG = "SnoozeAction"
    }
}
