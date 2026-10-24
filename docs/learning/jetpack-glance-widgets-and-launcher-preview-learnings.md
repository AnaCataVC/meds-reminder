# Jetpack Glance Widgets, Configuration Lifecycles & Launcher Previews

## Overview

This document captures architectural learnings, platform constraints, and implementation patterns discovered while introducing Android Home Screen Widgets into **Meds Reminder** using **Jetpack Glance** (Glance AppWidget `1.1.1`).

---

## 1. Provider Isolation in Shared Configuration Activities

When multiple widget types (e.g. `NextDoseWidget` and `DailyScheduleWidget`) share a common configuration activity (`WidgetConfigurationActivity`), invoking `update(context, glanceId)` across different widget classes will result in runtime exceptions.

### The Problem
Calling `NextDoseWidget().update(context, glanceId)` on a `glanceId` associated with a `DailyScheduleWidgetReceiver` throws:
```text
java.lang.IllegalArgumentException: Cannot update widget of type DailyScheduleWidget with provider NextDoseWidget
```
In a shared configuration flow, this exception aborted the lifecycle before the matching widget class could be updated, preventing configuration data from persisting.

### The Solution
Always inspect the actual provider class from `AppWidgetManager` before triggering widget updates:
```kotlin
val appWidgetInfo = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)
val providerClassName = appWidgetInfo?.provider?.className ?: ""

if (providerClassName.contains("NextDose")) {
    runCatching { NextDoseWidget().update(context, glanceId) }
} else if (providerClassName.contains("DailySchedule")) {
    runCatching { DailyScheduleWidget().update(context, glanceId) }
}
```

---

## 2. Dual-Store State Persistence (SharedPreferences Fallback)

### The Problem
Jetpack Glance persists per-widget state asynchronously through `PreferencesGlanceStateDefinition` (backed by DataStore). During the transition when the configuration activity finishes and the launcher inflates the newly placed widget, the DataStore file may not yet be flushed to disk, causing the widget to briefly render with default (unfiltered) state.

### The Solution
Implement an atomic dual-store pattern:
1. Save the configuration key synchronously in standard `SharedPreferences` keyed by platform `appWidgetId`.
2. Persist the state in Glance Preferences DataStore via `updateAppWidgetState()`.
3. In `provideGlance()`, fallback to the synchronous `SharedPreferences` entry if the Glance Preferences value has not yet propagated:
```kotlin
var selectedPersonId = prefs[WidgetConstants.PREF_SELECTED_PERSON_ID]?.takeIf { it > 0 }

if (selectedPersonId == null) {
    val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrNull()
    if (appWidgetId != null && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
        val sp = context.getSharedPreferences("meds_widgets_prefs", Context.MODE_PRIVATE)
        val spPersonId = sp.getLong("widget_person_$appWidgetId", -1L)
        if (spPersonId > 0) {
            selectedPersonId = spPersonId
        }
    }
}
```

---

## 3. Launcher Previews in Android 12+ vs Legacy Launchers

Android launchers do not execute Kotlin Compose/Glance rendering pipelines to generate preview tiles in the widget picker. Without declarative preview assets in metadata, launchers display empty or blank placeholder containers.

### Best Practices:
1. **`android:previewLayout` (Android 12+, API 31+)**: Reference an XML layout in `res/layout/` representing a realistic mock of the widget.
2. **`android:previewImage` (Legacy & Fallback)**: Reference a clean vector drawable in `res/drawable/` to ensure crisp rendering across different screen densities without raster pixelation.
3. Both attributes must be specified concurrently in `appwidget-provider` XML configuration:
```xml
<appwidget-provider
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp"
    android:minHeight="110dp"
    android:previewLayout="@layout/widget_daily_schedule_preview"
    android:previewImage="@drawable/widget_daily_schedule_preview"
    ... />
```

---

## 4. Instant Visual Feedback & Synchronous Instance Refresh

In widget architectures, user actions (such as taking a dose or snoozing an alarm) execute in a background `ActionCallback`. Updating Room database records and scheduling alarms can take several milliseconds.

To achieve responsive UX:
1. Dispatch an immediate, non-blocking visual indicator (`Toast`) on `Dispatchers.Main` directly within `ActionCallback.onAction()`.
2. Synchronously invoke `.update(context, glanceId)` on the exact clicked widget instance before dispatching a broad system-wide update broadcast. This guarantees that the clicked widget transitions immediately to its updated state without perceived lag.
