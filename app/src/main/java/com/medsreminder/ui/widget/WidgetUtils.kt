package com.medsreminder.ui.widget

import androidx.glance.GlanceId

/**
 * Utility functions for Jetpack Glance AppWidgets.
 */
object WidgetUtils {

    /**
     * Infallibly extracts the integer Android platform appWidgetId from a GlanceId instance.
     * Uses reflection on AppWidgetId.getAppWidgetId() and fallback regex on toString().
     */
    fun extractAppWidgetId(glanceId: GlanceId): Int? {
        val reflected = runCatching {
            val method = glanceId.javaClass.getMethod("getAppWidgetId")
            method.invoke(glanceId) as? Int
        }.getOrNull()
        if (reflected != null && reflected > 0) return reflected

        return Regex("""appWidgetId=(\d+)""").find(glanceId.toString())?.groupValues?.get(1)?.toIntOrNull()
    }
}
