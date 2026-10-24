package com.medsreminder.ui.widget.receiver

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.medsreminder.ui.widget.DailyScheduleWidget

/**
 * Android system broadcast receiver managing DailyScheduleWidget lifecycle.
 */
class DailyScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DailyScheduleWidget()
}
