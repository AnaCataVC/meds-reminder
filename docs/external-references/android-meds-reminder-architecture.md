> **Created:** 2026-08-17
> **Last Updated:** 2026-08-17

# Android Architecture & System Constraints Research: Meds Reminder

This document synthesizes external technical benchmarks, Android OS policy requirements, and engineering constraints for developing a native Android medication reminder application (`meds-reminder`).

---

## 1. Android Alarm Policies (Android 13/14/15, API 33-35)

### 1.1 `SCHEDULE_EXACT_ALARM` vs `USE_EXACT_ALARM`

* **`SCHEDULE_EXACT_ALARM`**:
  * **Behavior in Android 14+ (API 34+)**: Denied by default for newly installed apps targeting API 33+.
  * **Flow**: Requires dynamic runtime check via `alarmManager.canScheduleExactAlarms()`. If `false`, redirects user via `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`.
  * **Revocable**: Can be revoked by the user at any time in system settings. Requires listening to broadcast `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`.

* **`USE_EXACT_ALARM`**:
  * **Behavior**: Pre-granted on install in Android 13+ (API 33+). Cannot be revoked by the user.
  * **Google Play Policy Exemption**: Strictly permitted only for core functionality apps under the categories:
    1. Alarm clock apps.
    2. Timer apps.
    3. Calendar / Medication adherence / critical reminder apps.
  * **Recommendation**: Declare `USE_EXACT_ALARM` in `AndroidManifest.xml` as primary permission for medical adherence, with `SCHEDULE_EXACT_ALARM` (`maxSdkVersion="32"`) as backwards-compatible fallback.

### 1.2 `AlarmManager.setAlarmClock()` vs `setExactAndAllowWhileIdle()`

* `setAlarmClock(AlarmManager.AlarmClockInfo, PendingIntent)`:
  * **Highest Priority**: Bypasses Doze mode and App Standby buckets completely.
  * **UI Indicator**: Renders the system alarm clock icon on the status bar and lockscreen.
  * **Accuracy**: Exact millisecond execution, not subject to batching or OEM battery throttling.

---

## 2. Notification Sound & `RingtoneManager` Constraints (Android 8.0+ / API 26+)

### 2.1 Immutability of `NotificationChannel` Sound
* In Android 8.0+ (Oreo, API 26+), sound is bound to the `NotificationChannel` and cannot be modified on an existing channel.
* Re-creating a channel with the same ID and a different sound is ignored by the OS.

### 2.2 Sound Access & Permission Boundaries
* **Standard System Tones**: URIs from `RingtoneManager.ACTION_RINGTONE_PICKER` (pointing to `content://media/internal/audio/...` or `Settings.System.DEFAULT_RINGTONE_URI`) are accessible by the System UI process.
* **Storage Access Framework (SAF) vs RingtonePicker**: `takePersistableUriPermission` throws a `SecurityException` if called on `RingtoneManager` URIs because `MediaProvider` does not grant persistable flags through the ringtone picker.
* **Architecture Solution**:
  * Generate a deterministic channel ID per custom tone: `"meds_channel_tone_${uri.hashCode()}"`.
  * Set `AudioAttributes.USAGE_ALARM` and `AudioAttributes.CONTENT_TYPE_SONIFICATION` on the channel.
  * Fallback to default notification channel if sound URI becomes inaccessible.

---

## 3. Dependency Injection & Serialization Benchmarks

| Framework | Compile Safety | Build Overhead | KMP Readiness | AndroidX Integration |
| :--- | :--- | :--- | :--- | :--- |
| **Hilt (Dagger)** | High (Static graph validation) | Moderate (KSP/Kapt) | JVM/Android only | Native (`@AndroidEntryPoint`, `@HiltViewModel`) |
| **Koin** | Runtime (Fails if module missing) | Low (Zero code-gen) | 100% Multiplatform | Good (`koin-androidx-compose`) |
| **Kotlin-Inject** | High (Compile-time via KSP) | Low (Lightweight KSP) | 100% Multiplatform | Requires manual factory bindings |

### Serialization: `kotlinx.serialization` vs `Moshi`
* `kotlinx.serialization`: Official JetBrains tool, compiler plugin based, zero reflection, native streaming for SAF (`InputStream`/`OutputStream`), and full KMP compatibility.
* `Moshi`: Strong reflection/KSP capabilities on JVM, but heavier and Android/JVM only.

---

## 4. Local-First Database Architecture (No History Tracker)

Per user requirements, the schema excludes historical tracking (`intake_logs`) to keep the footprint minimal, fast, and focused purely on:
1. `PersonEntity`: Profile catalog (ID, Name, ColorHex).
2. `MedicationEntity`: Master pill catalog (ID, Name, Dosage, Description, Stock).
3. `MedicationGroupEntity`: Schedule group (PersonId, Time, RingtoneUri, IsActive, DaysOfWeek, Status, SnoozeUntil).
4. `MedicationGroupCrossRef`: Many-to-Many join table between Group and Medication.

---

## 5. References & Sources
- [Android Developers: Exact Alarm Permission Guidelines](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)
- [Google Play Policy: Schedule Exact Alarms Use Cases](https://support.google.com/googleplay/android-developer/answer/13161491)
- [Android Developers: Create and Manage Notification Channels](https://developer.android.com/develop/ui/views/notifications/channels)
- [Kotlinx Serialization Documentation](https://github.com/Kotlin/kotlinx.serialization)
