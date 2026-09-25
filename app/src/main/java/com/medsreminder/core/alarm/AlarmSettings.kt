package com.medsreminder.core.alarm

import android.content.Context

/**
 * App-wide alarm preferences. A single value, so plain SharedPreferences is enough.
 */
class AlarmSettings(context: Context) {

    private val prefs = context.getSharedPreferences("alarm_settings", Context.MODE_PRIVATE)

    /** Minutes an unanswered alarm keeps ringing before it goes silent; 0 = never stops. */
    var ringTimeoutMinutes: Int
        get() = prefs.getInt(KEY_RING_TIMEOUT_MINUTES, DEFAULT_RING_TIMEOUT_MINUTES)
        set(value) = prefs.edit().putInt(KEY_RING_TIMEOUT_MINUTES, value).apply()

    companion object {
        private const val KEY_RING_TIMEOUT_MINUTES = "ring_timeout_minutes"
        const val DEFAULT_RING_TIMEOUT_MINUTES = 5
        val RING_TIMEOUT_OPTIONS = listOf(1, 2, 5, 10, 0)
    }
}
