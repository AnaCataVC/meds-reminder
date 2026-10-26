package com.medsreminder.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import com.medsreminder.R
import com.medsreminder.ui.widget.receiver.DailyScheduleWidgetReceiver
import com.medsreminder.ui.widget.receiver.NextDoseWidgetReceiver

/**
 * Helper to push dynamic RemoteViews previews to the Android 15+ (API 35+) system widget picker
 * using AppWidgetManager.setWidgetPreview via reflection for backward-compatible compilation.
 */
object WidgetPreviewHelper {

    private const val TAG = "WidgetPreviewHelper"

    fun updateGeneratedPreviews(context: Context) {
        if (Build.VERSION.SDK_INT >= 35) {
            runCatching {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val setWidgetPreviewMethod = AppWidgetManager::class.java.getMethod(
                    "setWidgetPreview",
                    ComponentName::class.java,
                    Int::class.javaPrimitiveType,
                    RemoteViews::class.java
                )

                // 1. Next Dose Widget Preview
                val nextComponent = ComponentName(context, NextDoseWidgetReceiver::class.java)
                val nextRemoteViews = RemoteViews(context.packageName, R.layout.widget_next_dose_preview_layout)
                setWidgetPreviewMethod.invoke(
                    appWidgetManager,
                    nextComponent,
                    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                    nextRemoteViews
                )

                // 2. Daily Schedule Widget Preview
                val dailyComponent = ComponentName(context, DailyScheduleWidgetReceiver::class.java)
                val dailyRemoteViews = RemoteViews(context.packageName, R.layout.widget_daily_schedule_preview_layout)
                setWidgetPreviewMethod.invoke(
                    appWidgetManager,
                    dailyComponent,
                    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                    dailyRemoteViews
                )

                Log.d(TAG, "Generated widget previews pushed successfully for Android 15+")
            }.onFailure { e ->
                Log.w(TAG, "Failed to push dynamic widget previews: ${e.message}")
            }
        }
    }
}
