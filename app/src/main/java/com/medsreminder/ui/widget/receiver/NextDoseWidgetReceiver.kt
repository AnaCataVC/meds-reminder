package com.medsreminder.ui.widget.receiver

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.medsreminder.ui.widget.NextDoseWidget

/**
 * Android system broadcast receiver managing NextDoseWidget lifecycle.
 */
class NextDoseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextDoseWidget()
}
