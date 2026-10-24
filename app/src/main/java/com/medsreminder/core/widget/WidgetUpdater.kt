package com.medsreminder.core.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.medsreminder.ui.widget.DailyScheduleWidget
import com.medsreminder.ui.widget.NextDoseWidget

/**
 * Contract for triggering reactive updates across all active Glance AppWidgets.
 */
interface WidgetUpdater {
    suspend fun notifyWidgetsChanged()
}

/**
 * Concrete Android implementation broadcasting refresh requests to NextDoseWidget and DailyScheduleWidget.
 */
class AndroidWidgetUpdater(
    private val context: Context
) : WidgetUpdater {

    override suspend fun notifyWidgetsChanged() {
        try {
            NextDoseWidget().updateAll(context)
            DailyScheduleWidget().updateAll(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Glance widgets", e)
        }
    }

    companion object {
        private const val TAG = "WidgetUpdater"
    }
}
