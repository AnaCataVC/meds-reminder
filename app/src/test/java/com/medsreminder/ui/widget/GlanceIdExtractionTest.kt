package com.medsreminder.ui.widget

import androidx.glance.GlanceId
import org.junit.Assert.*
import org.junit.Test

class GlanceIdExtractionTest {

    @Test
    fun testGlanceIdInspection() {
        val glanceIdClass = Class.forName("androidx.glance.appwidget.AppWidgetId")
        val constructor = glanceIdClass.getDeclaredConstructor(Int::class.javaPrimitiveType)
        constructor.isAccessible = true
        val instance = constructor.newInstance(42) as GlanceId

        println("instance toString: $instance")
        assertEquals("AppWidgetId(appWidgetId=42)", instance.toString())

        val parsedId = Regex("""appWidgetId=(\d+)""").find(instance.toString())?.groupValues?.get(1)?.toIntOrNull()
        assertEquals(42, parsedId)

        val method = glanceIdClass.getMethod("getAppWidgetId")
        val reflectedId = method.invoke(instance) as Int
        assertEquals(42, reflectedId)
    }
}
