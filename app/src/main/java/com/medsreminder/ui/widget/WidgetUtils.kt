package com.medsreminder.ui.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager

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

    /**
     * Infallibly obtains or instantiates a GlanceId for a given Android platform appWidgetId.
     * If GlanceAppWidgetManager does not know about the newly created widget yet, instantiates
     * the internal AppWidgetId(appWidgetId) wrapper directly via reflection.
     */
    suspend fun getOrCreateGlanceId(context: Context, appWidgetId: Int): GlanceId? {
        val mgr = GlanceAppWidgetManager(context)
        val fromMgr = runCatching { mgr.getGlanceIdBy(appWidgetId) }.getOrNull()
        if (fromMgr != null) return fromMgr

        return runCatching {
            val clazz = Class.forName("androidx.glance.appwidget.AppWidgetId")
            val ctor = clazz.getDeclaredConstructor(Int::class.javaPrimitiveType)
            ctor.isAccessible = true
            ctor.newInstance(appWidgetId) as GlanceId
        }.getOrNull()
    }
}
