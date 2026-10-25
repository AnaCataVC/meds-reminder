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

## 2. Dual-Store State Persistence & Synchronous Commit

### The Problem
Jetpack Glance persists per-widget state asynchronously through `PreferencesGlanceStateDefinition` (backed by DataStore). During the transition when the configuration activity finishes and the launcher inflates the newly placed widget, the DataStore file may not yet be flushed to disk. Furthermore, using `SharedPreferences.edit().apply()` introduces asynchronous disk I/O, allowing the configuration activity to call `finish()` before preferences are physically written.

### The Solution
Implement an atomic, synchronous dual-store pattern:
1. Save the configuration key synchronously using `.commit()` in standard `SharedPreferences` keyed by platform `appWidgetId`.
2. Persist the state in Glance Preferences DataStore via `updateAppWidgetState()`.
3. In `provideGlance()`, fallback to `SharedPreferences` via infallible integer `appWidgetId` extraction:
```kotlin
var selectedPersonId = prefs[WidgetConstants.PREF_SELECTED_PERSON_ID]?.takeIf { it > 0 }

if (selectedPersonId == null) {
    val appWidgetId = WidgetUtils.extractAppWidgetId(id)
    if (appWidgetId != null && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
        val sp = context.getSharedPreferences("meds_widgets_prefs", Context.MODE_PRIVATE)
        val spPersonId = sp.getLong("widget_person_$appWidgetId", -1L)
        if (spPersonId > 0) {
            selectedPersonId = spPersonId
            runCatching {
                updateAppWidgetState(context, id) { mutablePrefs ->
                    mutablePrefs[WidgetConstants.PREF_SELECTED_PERSON_ID] = spPersonId
                }
            }
        }
    }
}
```

---

## 3. Infallible AppWidget ID Extraction from GlanceId

### The Problem
`GlanceAppWidgetManager.getAppWidgetId(glanceId)` can throw `IllegalArgumentException` if called before Glance registers the session internally. Because `AppWidgetId` is an internal Kotlin data class in Glance, directly casting `id as AppWidgetId` causes compilation visibility errors.

### The Solution
Extract the platform `Int` `appWidgetId` using reflection combined with regex parsing on the string representation:
```kotlin
fun extractAppWidgetId(glanceId: GlanceId): Int? {
    val reflected = runCatching {
        val method = glanceId.javaClass.getMethod("getAppWidgetId")
        method.invoke(glanceId) as? Int
    }.getOrNull()
    if (reflected != null && reflected > 0) return reflected

    return Regex("""appWidgetId=(\d+)""").find(glanceId.toString())?.groupValues?.get(1)?.toIntOrNull()
}
```

---

## 4. Launcher Previews: Raster PNG Requirement & RemoteViews Whitelist

### The Problems
1. **Samsung One UI & OEM Raster Requirement:** While Android 12+ supports `previewLayout`, OEM launchers (notably Samsung One UI) rely heavily on `android:previewImage`. If `previewImage` points to an XML `<vector>` drawable, the system's `AppWidgetProviderInfo.loadPreviewImage()` fails to rasterize it as a bitmap, rendering a blank tile.
2. **RemoteViews Class Whitelist in `previewLayout`:** Android `RemoteViews` enforces a strict class whitelist annotated with `@RemoteView`. Using generic `<View>` tags (e.g. for color pills or spacers) throws `android.view.InflateException: Class not allowed to be inflated android.view.View`.

### The Solution
1. **Supply pre-rendered raster PNG bitmaps** in `res/drawable-nodpi/` for `android:previewImage`.
2. **Never use `<View>` in `previewLayout` XML**: Use `<ImageView>` with `android:src` for indicators or `<android.widget.Space>` for spacing.

---

## 5. Main-Thread Toasts in Background ActionCallbacks

User interactions inside Glance widgets dispatch through `ActionCallback.onAction()`, which executes in a background broadcast receiver context. Calling `Toast.makeText()` on background threads or coroutine scopes with restricted contexts can fail silently.

### The Solution
Always dispatch Toasts via the application context on the main Looper:
```kotlin
Handler(Looper.getMainLooper()).post {
    Toast.makeText(context.applicationContext, "✅ Toma confirmada: $groupName", Toast.LENGTH_SHORT).show()
}
```

---

## 6. Decoupled Global Widget Refresh

In `WidgetUpdater.notifyWidgetsChanged()`, sequentially calling `updateAll()` inside a single try-catch block creates a single point of failure: if a user only has `DailyScheduleWidget` on their screen and `NextDoseWidget().updateAll()` encounters an error, the subsequent widget update was skipped.

### The Solution
Isolate every provider's `updateAll()` call in its own `runCatching` block:
```kotlin
override suspend fun notifyWidgetsChanged() {
    runCatching { NextDoseWidget().updateAll(context) }
    runCatching { DailyScheduleWidget().updateAll(context) }
}
```
