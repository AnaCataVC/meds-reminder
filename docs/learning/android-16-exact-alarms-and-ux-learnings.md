> **Created:** 2026-08-18
> **Last Updated:** 2026-08-18

# Learnings: Android 16 Exact Alarms, Notification Runtime Permissions, and Form UX Validation

## 1. Context & Architectural Overview
During the testing and verification of medication alarm scheduling on Android 16 (and backward compatibility through Android 12–15), we encountered issues regarding:
- App visibility in Android OS **"Alarms & Reminders"** Special App Access settings.
- Notification audio delivery and silent suppression.
- UX friction where disabled primary actions caused false-positive debugging assumptions.

This document records the root causes, decisions, and architectural lessons learned.

---

## 2. Key Architectural Decisions & Gotchas

### A. The `android:maxSdkVersion="32"` Manifest Trap
- **Issue:** The manifest previously declared:
  ```xml
  <uses-permission
      android:name="android.permission.SCHEDULE_EXACT_ALARM"
      android:maxSdkVersion="32" />
  ```
- **Consequence:** On Android 13 (API 33), Android 14 (API 34), Android 15 (API 35), and Android 16 (API 36), the package manager completely ignores the `SCHEDULE_EXACT_ALARM` declaration. As a result, Android's system settings screen for **"Alarms & reminders"** (`Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`) filtered out Meds Reminder, making it impossible for users to toggle or inspect the permission.
- **Lesson:** `SCHEDULE_EXACT_ALARM` must be declared without `maxSdkVersion` across all target SDKs so the app remains visible in system-level Special App Access lists.

---

### B. Runtime `POST_NOTIFICATIONS` Suppression
- **Issue:** In Android 13+, posting notifications requires runtime permission (`android.permission.POST_NOTIFICATIONS`). Without runtime approval, `NotificationManager.notify()` fails silently.
- **Consequence:** Even when `AlarmManager.setAlarmClock()` fired accurately on time, the audio channel did not play because the OS blocked the notification payload.
- **Lesson:** Always pair `AlarmManager` implementations with explicit runtime notification checks during app startup (`registerForActivityResult(RequestPermission())`).

---

### C. Alarm Audio Priority and Notification Channel Caching
- **Issue:** Using `Settings.System.DEFAULT_NOTIFICATION_URI` resulted in a brief 1-second chime rather than a noticeable alarm.
- **Solution:** 
  - Set sound URI to `RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)`.
  - Set `AudioAttributes.USAGE_ALARM` with `AudioAttributes.CONTENT_TYPE_SONIFICATION`.
  - Set channel `setBypassDnd(true)` and `lockscreenVisibility = Notification.VISIBILITY_PUBLIC`.
- **Gotcha:** Android permanently caches channel sound and importance once created. When updating sound attributes programmatically, increment the channel ID (e.g., `meds_reminder_alarm_channel_v2`) to ensure existing installations adopt the new alarm sound.

---

### D. One-Shot Alarms and Autonomous Rescheduling
- **Issue:** `AlarmManager.setAlarmClock()` is inherently a one-shot trigger. If an app only reschedules inside interactive notification button receivers (e.g., "Confirm" or "Postpone"), dismissed or ignored alarms will miss all future occurrences.
- **Lesson:** `AlarmReceiver` must immediately reschedule the next recurring cycle upon receiving the broadcast, guaranteeing calendar continuity regardless of user touch interaction.

---

### E. UX Anti-Pattern: Silent Disabled Buttons
- **Issue:** The "Save and Activate Alarm" CTA was disabled when either `name.isBlank()` or `selectedPersonId == 0L`. Without visual feedback, users assumed the button was locked due to missing system permissions.
- **Solution:**
  1. Add helper text to required input fields (`supportingText`).
  2. If no person exists, display a clear, prominent callout button to *"Create First Person"*.
  3. Auto-select newly created persons immediately.
  4. Display explicit error reasons below disabled buttons (e.g., `⚠️ To save, you must: select a person and enter a reminder name`).

---

## 3. Reference Summary

| Area | Pitfall | Recommended Pattern |
| :--- | :--- | :--- |
| **Manifest** | `maxSdkVersion="32"` on exact alarms | Remove `maxSdkVersion` to support Android 13–16+ |
| **Runtime Perms** | Missing `POST_NOTIFICATIONS` check | Request on launch in `MainActivity` |
| **Alarm Channel** | Notification chime sound / cached channel | Use `TYPE_ALARM`, bump channel ID to `_v2` |
| **Receiver** | Relying on user touch to reschedule | Automatically call `alarmScheduler.schedule()` on alarm fire |
| **Form UX** | Silently disabled save button | Explicit validation warnings and auto-selection |
