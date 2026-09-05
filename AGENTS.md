# AGENTS.md — AI Agent Guidelines & Architecture Manual

This document serves as the operational manual, architecture reference, and workflow guide for AI coding agents operating within the **Meds Reminder** repository.

---

## 1. Project Overview & Architecture

**Meds Reminder** is a native Android application built with **Kotlin** and **Jetpack Compose**. It provides reliable, battery-aware medication tracking, schedule-based alerts, dosage management, and adherence logging.

### Core Architecture & Modules:
- **`app/src/main/java/com/medsreminder/`**:
  - **`data/`**:
    - `local/`: Room Database (`AppDatabase`), DAOs (`PersonDao`, `MedicationDao`, `MedicationGroupDao`), and entities (`PersonEntity`, `MedicationEntity`, `MedicationGroupEntity`, `Relations`). Single Source of Truth (SSOT) for all intake states.
    - `repository/`: `MedicationScheduleRepositoryImpl` implementing the SSOT coordination contract between Room, alarms, and notifications.
    - `backup/`: Storage Access Framework (SAF) JSON backup & restore manager (`BackupManager`).
  - **`domain/`**:
    - `repository/`: `MedicationScheduleRepository` (State transition contract for confirm, snooze, and skip actions).
    - `scheduler/`: `AlarmScheduler` interface.
  - **`ui/`**: 
    - Jetpack Compose navigation & screens (`HorariosScreen`, `MedicamentosScreen`, `PerfilesScreen`, `AjustesScreen`, `AddEditGroupScreen`).
    - **`alarm/`**: Full-screen interactive alarm activity (`AlarmActivity`) and dedicated `AlarmViewModel`.
    - Dialogs, Material 3 design system, and `MainViewModel`.
  - **`core/alarm/` & `core/notification/`**: 
    - Broadcast receivers (`AlarmReceiver`, `BootReceiver`, `NotificationActionReceiver`) using `BroadcastReceiver.goAsync()` for thread-safe asynchronous operations.
    - `AndroidAlarmScheduler`: Concrete scheduling engine leveraging `AlarmManager.setAlarmClock()`.
    - `NotificationHelper`: Dynamic ringtone channels (`meds_channel_tone_${hash}`), silent pre-alarm channels, and `cancelAllForGroup()` atomic dismissal.
  - **`di/`**: Koin module configuration (`AppModule.kt`).
- **`releases/`**: Production and debug APKs (`meds-reminder-vX.Y.Z-release.apk`).

---

## 2. Directory Structure

```text
meds-reminder/
├── app/
│   ├── build.gradle.kts           # App-level dependencies, SDK targets, and signing configs
│   ├── schemas/                   # Room database schemas for automated migration tests
│   └── src/
│       ├── main/                  # Android Kotlin source code, manifests, resources
│       └── test/                  # Unit tests for DAOs, repositories, and ViewModels
├── gradle/
│   ├── libs.versions.toml         # Version catalog for dependencies and plugins
│   └── wrapper/                   # Gradle wrapper binaries and properties
├── docs/                          # Architecture decisions, alarm research, and learnings
│   ├── external-references/       # Android 14/15/16 exact alarms & notification policies
│   └── learning/                  # UX, concurrency, and system constraint learnings
├── releases/                      # Compiled APK release artifacts (gitignored)
├── build.gradle.kts               # Root Gradle build configuration
├── settings.gradle.kts            # Gradle settings and project tree
└── README.md                      # Bilingual project documentation (EN/ES)
```

---

## 3. Mandatory Agent Rules & Directives

### 🌐 Language & Communication
- **Source Code**: All Kotlin source code (classes, functions, properties, comments) MUST be in **English**.
- **User Chat**: Communicate with the user in **Spanish** unless requested otherwise.
- **Git Commits**: Use **Conventional Commits** in **English** (e.g., `feat: ...`, `fix: ...`, `docs: ...`, `refactor: ...`).
- **README**: Maintain bilingual documentation (English and Spanish).

### 🔒 Security & Privacy
- **Absolute Paths**: NEVER leak local filesystem paths (e.g., `C:\Users\...`) into code, documentation, or commits. Always use relative paths (`app/src/main/...`, `docs/...`).
- **Secrets**: Do not hardcode signing keys, keystore passwords, or sensitive credentials in git. Always use environment variables or local `local.properties`.

### 💻 PowerShell Environment
- **Command Chaining**: NEVER use `&&` or `||` in terminal commands. Use `;` or run sequential commands.
- **GitHub CLI Context**: Switch to personal account `AnaCataVC` (`gh auth switch -u AnaCataVC --hostname github.com 2>$null`).

---

## 4. Development & Build Commands (PowerShell)

### Build & Run Tests
```powershell
# Run unit tests
./gradlew testDebugUnitTest

# Run lint checks
./gradlew lintDebug
```

### Assemble Debug APK
```powershell
# Build debug APK
./gradlew assembleDebug
```

### Assemble Production Release APK
```powershell
# Build signed production release APK
./gradlew assembleRelease
```

> [!IMPORTANT]
> **Build Outputs**: Production APKs generated in `app/build/outputs/apk/release/` must be copied to `releases/` (e.g. `releases/meds-reminder-vX.Y.Z-release.apk`) before creating GitHub releases.

---

## 5. Android System Considerations & Best Practices

1. **Exact Alarms & Doze Mode**: Med reminders require exact delivery. Use `AlarmManager.setAlarmClock()` and ensure permissions (`USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM`) are properly declared without restrictive `maxSdkVersion`.
2. **Deterministic Architecture (Room as SSOT)**: Never mutate in-memory entity copies and pass them directly to the scheduler. All dosage state transitions (`confirmIntake`, `snoozeSchedule`, `skipSchedule`) MUST execute through `MedicationScheduleRepository` to guarantee that Room is updated, notifications are dismissed (`cancelAllForGroup`), and alarms are rescheduled deterministically.
3. **No Artificial Delays**: NEVER introduce arbitrary `delay()` calls (e.g., `delay(500)`) in ViewModels, Receivers, or Activities to wait for database writes. Rely strictly on Room suspend functions, coroutine completion, and reactive state flows (`collectAsStateWithLifecycle`).
4. **BroadcastReceiver Lifecycle (`goAsync`)**: Because the Android OS may kill a receiver process as soon as `onReceive()` finishes, always wrap asynchronous operations in `val pendingResult = goAsync()` and execute within `CoroutineScope(Dispatchers.IO).launch`, ensuring `pendingResult.finish()` is called inside a `finally` block.
5. **Notification Permissions (`POST_NOTIFICATIONS`)**: On Android 13+ (API 33+), runtime notification permissions must be requested before scheduling alarms.
6. **Room Database Migrations**: Every schema change to Room entities must increment the database version in `AppDatabase` and provide an explicit `Migration` or auto-migration rule.
7. **Boot Resilience**: The application listens to `ACTION_BOOT_COMPLETED` via `BootReceiver` to reschedule all pending alarms whenever the device is restarted.
