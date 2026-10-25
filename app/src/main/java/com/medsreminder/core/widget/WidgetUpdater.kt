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
        runCatching {
            NextDoseWidget().updateAll(context)
        }.onFailure { e ->
            Log.w(TAG, "Notice: NextDoseWidget updateAll: ${e.message}")
        }

        runCatching {
            DailyScheduleWidget().updateAll(context)
        }.onFailure { e ->
            Log.w(TAG, "Notice: DailyScheduleWidget updateAll: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "WidgetUpdater"
    }
}
