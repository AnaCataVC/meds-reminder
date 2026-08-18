> **Created:** 2026-08-18
> **Last Updated:** 2026-08-18

# Android 16 (API 36) & Android 14/15 Exact Alarms & Notifications Architecture

## 1. Executive Summary & Core Platform Changes

Starting in Android 12 (API 31) and continuously tightened across Android 13 (API 33), Android 14 (API 34), Android 15 (API 35), and Android 16 (API 36):
- Exact alarms (`AlarmManager.setExact()`, `setExactAndAllowWhileIdle()`, `setAlarmClock()`) are heavily restricted by the OS to protect battery life.
- Runtime notification permissions (`android.permission.POST_NOTIFICATIONS`) are mandatory on Android 13+ to post any user-facing notifications or trigger notification channel audio.
- User management of exact alarms takes place via Android System Settings under **"Alarms & reminders" (Special App Access)**.

---

## 2. Permission Comparison: `SCHEDULE_EXACT_ALARM` vs `USE_EXACT_ALARM`

| Feature / Behavior | `SCHEDULE_EXACT_ALARM` | `USE_EXACT_ALARM` |
| :--- | :--- | :--- |
| **Permission Type** | Special App Access (User-grantable) | Normal Permission (Install-time granted) |
| **Default State (Android 14+)** | **Denied by default** for new app installs | **Granted by default** upon installation |
| **Visible in "Alarms & Reminders" Settings?** | **YES** (Listed under `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`) | **NO** (System hides apps from toggle list) |
| **Google Play Store Policy** | For apps that need exact alarms secondary to main app | **Strictly audited**; rejected if not core alarm/timer/calendar |
| **Revocation by User** | User can revoke at any time in system settings | User cannot revoke from standard Alarms toggle |
| **Manifest Declaration** | `<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />` | `<uses-permission android:name="android.permission.USE_EXACT_ALARM" />` |

### Why `android:maxSdkVersion="32"` Broke the System Settings List
When `android:maxSdkVersion="32"` was declared for `SCHEDULE_EXACT_ALARM`:
- Devices running Android 13 (API 33), Android 14 (API 34), Android 15 (API 35), and Android 16 (API 36) completely ignore the `SCHEDULE_EXACT_ALARM` permission tag.
- As a result, Android's Settings app (`Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`) does **NOT** list the application in the "Alarms & reminders" settings screen.
- Even though `USE_EXACT_ALARM` grants internal scheduling capability, the user is unable to find or manage the app in the OS Special Access settings screen.

---

## 3. Best Practices for Android 16 Medication Alarms

### A. Manifest Setup
Declare `SCHEDULE_EXACT_ALARM` without `maxSdkVersion` (and `USE_EXACT_ALARM` for pre-granting on install for core alarm applications):
```xml
<!-- Notifications for Android 13+ (API 33+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Exact Alarms for Android 12+ (API 31 - 36+) -->
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />

<!-- Wake & Boot resilience -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

### B. Launching System Settings with Direct Package URI
In Android 12+ (API 31+):
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback to global alarms settings list if package URI is unsupported by OEM
        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
    }
}
```

### C. Runtime Notification Request (Android 13+ / API 33–36)
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
        != PackageManager.PERMISSION_GRANTED) {
        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
```

### D. Audio & Channel Configuration for High-Reliability Alarms
- Sound URI: `RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)`
- Audio Attributes:
  - `USAGE_ALARM`
  - `CONTENT_TYPE_SONIFICATION`
- Channel Priority & Visibility:
  - `NotificationManager.IMPORTANCE_HIGH`
  - `NotificationCompat.PRIORITY_MAX`
  - `NotificationCompat.CATEGORY_ALARM`
  - `setBypassDnd(true)`
  - `lockscreenVisibility = Notification.VISIBILITY_PUBLIC`

---

## 4. Battery Optimization & OEM Background Restrictions (Android 6.0 - 16+)

### 4.1 The "Not Optimized" vs "All Apps" Filter Trap
When launching `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`:
- Android opens the system **"Battery optimization"** (*Optimización de batería*) screen.
- **Default System View**: The screen defaults to filtering by **"Not optimized"** (*Sin optimizar*), which only displays applications that have *already* been exempted.
- **Why Newly Installed Apps Appear Missing**: Newly installed applications are in the "Optimized" state by default. Consequently, they do **NOT** appear in the initial "Not optimized" list.
- **Resolution**:
  1. The user must tap the top dropdown menu (currently set to *"Not optimized"* / *"Sin optimizar"*).
  2. Select **"All apps"** (*Todas las aplicaciones*).
  3. Locate the application alphabetically.
  4. Select **"Don't optimize"** (*No optimizar*) or *"Unrestricted"* (*Sin restricciones*).

### 4.2 Direct App Info Route (`ACTION_APPLICATION_DETAILS_SETTINGS`)
On Android 12+ (API 31–36):
- Navigating to **App Info -> Battery** (*Información de la app -> Batería* / *Uso de batería de la app*).
- Directly presents 3 operational tiers:
  1. **Unrestricted** (*Sin restricciones*): Allows background alarms, wake locks, and broadcast receivers to trigger without OEM throttling.
  2. **Optimized** (*Optimizado*): Standard Android Doze mode.
  3. **Restricted** (*Restringido*): Prohibits background execution.

### 4.3 OEM-Specific Aggressive Battery Management
- **Xiaomi (MIUI / HyperOS)**: Requires enabling *Autostart* (*Inicio automático*) and setting Battery Saver to *No restrictions*.
- **Samsung (One UI)**: Ensure the app is excluded from *Sleeping apps* and added to *Never sleeping apps* (*Aplicaciones nunca suspendidas*).
- **Huawei (EMUI)**: Set App launch from *Automatic* to *Manage manually* (allowing auto-launch, secondary launch, and background running).

---

## 5. References & Sources
- [Android Developers: Exact Alarm Permissions (Android 14-16)](https://developer.android.com/develop/background-work/services/alarms)
- [Android Developers: Request Exact Alarm Permissions](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)
- [Android Developers: Optimize for Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Android Developers: Notification Runtime Permissions](https://developer.android.com/develop/ui/views/notifications/notification-permission)
- [DontKillMyApp: OEM Background Throttling Reference](https://dontkillmyapp.com)
