package com.medsreminder.ui.widget

import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.action.ActionParameters

/**
 * Constants and typed keys for Glance AppWidgets and ActionCallbacks.
 */
object WidgetConstants {
    val PREF_SELECTED_PERSON_ID = longPreferencesKey("selected_person_id")
    val PARAM_GROUP_ID = ActionParameters.Key<Long>("group_id")
    val PARAM_NOTIFICATION_ID = ActionParameters.Key<Int>("notification_id")
}
