> **Created:** 2026-10-24
> **Last Updated:** 2026-10-24

# Jetpack Glance Widgets Architecture & Android Modern AppWidgets

## 1. Overview & Ecosystem

[Jetpack Glance](https://developer.android.com/jetpack/androidx/releases/glance) is Google's modern framework for developing Android AppWidgets using declarative Jetpack Compose-style syntax. Glance translates composable functions into `RemoteViews` layouts optimized for the Android launcher process without requiring manual XML layout scaffolding.

### Core Dependencies (Version Catalog)
```toml
[versions]
glance = "1.1.1" # Stable production release

[libraries]
androidx-glance = { group = "androidx.glance", name = "glance", version.ref = "glance" }
androidx-glance-appwidget = { group = "androidx.glance", name = "glance-appwidget", version.ref = "glance" }
androidx-glance-material3 = { group = "androidx.glance", name = "glance-material3", version.ref = "glance" }
```

---

## 2. Glance Lifecycle & Session Management

Unlike traditional Compose UI on screen, a Glance AppWidget does not run in a continuous composition loop. It operates as a session-driven rendering engine executed under WorkManager or within the widget process:

1. **`GlanceAppWidget.provideGlance(context, glanceId)`**:
   The entry point for defining the widget tree. It provides a coroutine scope where asynchronous database calls or preferences queries can be performed safely before calling `provideContent { ... }`.
2. **`GlanceAppWidgetReceiver`**:
   The standard `BroadcastReceiver` that intercepts Android AppWidget system lifecycle broadcasts (`ACTION_APPWIDGET_UPDATE`, `ACTION_APPWIDGET_DELETED`, etc.) and delegates them to the `GlanceAppWidget` instance.
3. **`PreferencesGlanceStateDefinition`**:
   Default state definition providing persistent, per-widget-instance DataStore preferences accessible via `currentState<Preferences>()`.

---

## 3. Interactive Actions & Domain Execution (`ActionCallback`)

Widgets can execute background actions without launching the full application UI:

```kotlin
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

// Strongly-typed action parameters
val GroupIdActionKey = ActionParameters.Key<Long>("group_id")

class ConfirmIntakeWidgetAction : ActionCallback, KoinComponent {
    private val scheduleRepository: MedicationScheduleRepository by inject()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val groupId = parameters[GroupIdActionKey] ?: return
        
        // 1. Execute domain state transition (Room + AlarmManager + Notifications)
        scheduleRepository.confirmIntake(groupId)
        
        // 2. Broadcast update to all active widget instances
        NextDoseWidget().update(context, glanceId)
        DailyScheduleWidget().update(context, glanceId)
    }
}
```

---

## 4. Per-Widget Configuration Activity (Profile Selection)

Android AppWidgets support instance-level configuration (e.g., selecting a specific person's profile or "All profiles"):

### Manifest Declaration
```xml
<activity
    android:name=".ui.widget.WidgetConfigurationActivity"
    android:exported="true"
    android:theme="@style/Theme.MedsReminder">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_CONFIGURE" />
    </intent-filter>
</activity>
```

### Provider Info XML (`appwidget-provider`)
```xml
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp"
    android:minHeight="110dp"
    android:targetCellWidth="3"
    android:targetCellHeight="2"
    android:updatePeriodMillis="0"
    android:configure="com.medsreminder.ui.widget.WidgetConfigurationActivity"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:description="@string/widget_next_dose_description">
</appwidget-provider>
```

### Configuration Flow
1. Extract incoming `appWidgetId` from `Intent.EXTRA_APPWIDGET_ID`.
2. Render profile selection UI using Compose (`ComponentActivity`).
3. On profile select:
   ```kotlin
   val glanceManager = GlanceAppWidgetManager(context)
   val glanceId = glanceManager.getGlanceIdBy(appWidgetId)

   updateAppWidgetState(context, glanceId) { prefs ->
       prefs[longPreferencesKey("selected_person_id")] = selectedPersonId ?: -1L
   }
   NextDoseWidget().update(context, glanceId)

   val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
   setResult(Activity.RESULT_OK, resultValue)
   finish()
   ```

---

## 5. Event-Driven Updates & Battery Invariants

- **Zero Aggressive Polling**: Set `updatePeriodMillis="0"` or `86400000` (24h fallback).
- **Trigger Points**:
  - `MedicationScheduleRepositoryImpl` state transitions (`confirmIntake`, `snoozeSchedule`, `skipSchedule`).
  - Alarm triggers and ring events (`AlarmReceiver`).
  - Schedule creation or edits (`MainViewModel.SaveGroup`, `DeleteGroup`).
  - Profile suspension or resumption (`MainViewModel.SuspendPerson`, `ResumePerson`).
- **Global Broadcast Refresh**:
  ```kotlin
  suspend fun notifyWidgetsChanged(context: Context) {
      NextDoseWidget().updateAll(context)
      DailyScheduleWidget().updateAll(context)
  }
  ```

---

## 6. Official References

- [Android Developers: Glance Overview](https://developer.android.com/develop/ui/views/appwidgets/glance)
- [Android Developers: Glance Actions & Callbacks](https://developer.android.com/develop/ui/views/appwidgets/glance/interactivity)
- [Android Developers: AppWidget Configuration](https://developer.android.com/develop/ui/views/appwidgets/configuration)
- [Jetpack Glance Release Notes](https://developer.android.com/jetpack/androidx/releases/glance)
