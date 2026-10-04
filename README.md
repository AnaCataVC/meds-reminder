<p align="center">
  <img src="icon.png" alt="meds-reminder Logo" width="120" />
</p>

# Meds Reminder

[English](README.md) | [Español](README.es.md)

[![Version](https://img.shields.io/badge/Version-1.4.0-emerald.svg?style=flat)](releases/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-purple.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg?style=flat&logo=android)](https://developer.android.com/jetpack/compose)
[![Room](https://img.shields.io/badge/Room%20DB-2.6.1-3DDC84.svg?style=flat&logo=sqlite)](https://developer.android.com/training/data-storage/room)
[![Koin](https://img.shields.io/badge/Koin-3.5.6-orange.svg?style=flat&logo=koin)](https://insert-koin.io)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=flat)](LICENSE)

---

### 1. Project Description
**Meds Reminder** is a 100% offline-first, native Android application engineered for high-reliability medication adherence and multi-profile dosage reminders. Built for families and caregivers, it enables users to manage multiple profiles, maintain a master medicine catalog, configure flexible schedule groups with deterministic alarm precision, assign custom device ringtones, temporarily suspend reminders per person (6 hours or remainder of the day), receive silent pre-alarm notifications (15/30 min before), configure rest cycles (e.g. contraceptives: 21 days on, 7 days off), display full-screen popups directly over lock screens, and atomically back up or restore data in JSON format via Android's Storage Access Framework (SAF).

### 2. Tech Stack & Architecture
* **Language:** Kotlin (v2.0)
* **UI Toolkit:** Jetpack Compose with Material Design 3
* **Architecture:** Clean Architecture + MVI/MVVM with reactive `StateFlow`
* **Single Source of Truth (SSOT):** Room Database with Kotlin Symbol Processing (KSP) & Auto-Migrations
* **Domain Repository:** `MedicationScheduleRepository` orchestrating state transitions across Room, `AlarmManager`, and `NotificationManager`
* **Lifecycle-Aware UI:** `AlarmViewModel` driving `AlarmActivity` via `collectAsStateWithLifecycle`
* **Dependency Injection:** Koin (Zero-codegen, lightweight Kotlin DSL)
* **Serialization:** `kotlinx.serialization` for schema-versioned JSON export/import
* **System Services & Background Execution:**
  * `AlarmManager.setAlarmClock()` (exact wake-ups under medical exemption)
  * `BroadcastReceiver.goAsync()` for thread-safe asynchronous receiver operations
  * Dynamic `NotificationChannel` generation per ringtone URI hash (`meds_channel_tone_${hash}`)
  * Full-Screen Intents (`USE_FULL_SCREEN_INTENT`) with `KeyguardManager` and `setTurnScreenOn`

### 3. Deterministic Alarm Architecture
In version 1.2.0, the alarm engine was comprehensively remediated into a robust, deterministic system that completely eliminates race conditions:
* **Room as Single Source of Truth (SSOT):** All intake logs, postponements, and schedule alterations mutate Room entities first (`markGroupAsTaken`, `setSnoozeTime`, `markGroupSkippedToday`). UI layers, broadcast receivers, and system schedulers query Room state directly, eliminating in-memory stale references and dual-dispatch bugs.
* **`MedicationScheduleRepository` Contract:** Centralized domain coordinator that guarantees atomic state transitions. When a medication intake is confirmed, snoozed, or skipped, the repository atomically:
  1. Updates the underlying Room database row.
  2. Invalidates all associated notification IDs simultaneously (main alarm, silent pre-alarm, and interactive banner notifications) via `NotificationHelper.cancelAllForGroup()`.
  3. Re-arms or cancels system `AlarmManager` triggers deterministically.
* **`AlarmViewModel` & UI Decoupling:** `AlarmActivity` delegates all asynchronous database and scheduling operations to a dedicated `AlarmViewModel`. UI state is exposed via `StateFlow<AlarmUiState>` and observed with `collectAsStateWithLifecycle()`. Activity dismissal is triggered reactively by observing `uiState.isFinished`, removing context leaks and view-bound coroutine leaks.
* **Zero Artificial Delays (`delay()` Removal):** All heuristic sleep routines and arbitrary coroutine delays (`delay(500)`) were eliminated from alarm triggering, confirmation, and dismissal pipelines. Operations execute deterministically upon asynchronous database transaction completion.

### 4. Key Engineering Learnings & System Constraints
* **AlarmManager Concurrency & Snooze Precedence:** When `AlarmReceiver` handles `ACTION_FIRE_ALARM`, it provides a fallback reschedule to ensure the next calendar occurrence is queued even if the user completely ignores the alert. However, if an active future snooze (`snoozeUntilEpochMs > now`) is present in Room, the scheduler yields precedence to prevent clobbering the snooze `PendingIntent`.
* **Deep Doze Mode Resilience:** Leveraging `AlarmManager.setAlarmClock()` provides an OS-level wake guarantee that pierces Android Doze mode and App Standby buckets under the medical exception (`USE_EXACT_ALARM`), displaying the clock icon on the lockscreen and guaranteeing millisecond-level execution.
* **`BroadcastReceiver.goAsync()` Execution Lifecycle:** Android terminates `BroadcastReceiver` processes immediately after `onReceive()` finishes on the main thread. By acquiring `val pendingResult = goAsync()` and executing within `CoroutineScope(Dispatchers.IO).launch` with `pendingResult.finish()` in a `finally` block, background database queries and notification channel operations complete safely without risking process death.
* **Dual-Notification Cancellation:** Advance pre-alarms (`groupId + 100000`) and main alarms (`groupId`) operate on separate channels. Marking a dose as taken early or dismissing an alert purges both identifiers concurrently, preventing ghost notifications from ringing later in the day.

### 5. Local Setup Instructions
1. Clone the repository:
   ```bash
   git clone https://github.com/AnaCataVC/meds-reminder.git
   ```
2. Open the project in **Android Studio Jellyfish | 2024.1+** (or newer).
3. Ensure JDK 17+ is configured in `Gradle Settings`.
4. Build and execute unit tests:
   ```bash
   ./gradlew testDebugUnitTest
   ```
5. Run the app on an Android device or emulator running Android 8.0+ (API 26+).

### 6. Battery Optimization & Background Execution
To ensure medication alarms ring reliably on Android:
* **Battery Optimization List Filter**: In system *Battery Optimization* settings, switch the top filter from *"Not optimized"* to *"All apps"*, find **Meds Reminder**, and set it to *"Don't optimize"*.
* **App Info Settings**: Alternatively, navigate to *App Info -> Battery* and select **"Unrestricted"**.
* **OEM Customizations**: On Xiaomi (MIUI/HyperOS) enable *Autostart*, and on Samsung add the app to *Never sleeping apps*.

---

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

