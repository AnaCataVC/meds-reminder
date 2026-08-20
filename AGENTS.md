# AGENTS.md — AI Agent Guidelines & Architecture Manual

This document serves as the operational manual, architecture reference, and workflow guide for AI coding agents operating within the **Meds Reminder** repository.

---

## 1. Project Overview & Architecture

**Meds Reminder** is a native Android application built with **Kotlin** and **Jetpack Compose**. It provides reliable, battery-aware medication tracking, schedule-based alerts, dosage management, and adherence logging.

### Core Architecture & Modules:
- **`app/src/main/java/com/medsreminder/`**:
  - **`data/`**:
    - `local/`: Room Database (`AppDatabase`), DAOs, and entities (`MedicationEntity`, `ReminderLogEntity`).
    - `repository/`: Repository implementations mediating between Room and ViewModels.
  - **`domain/`**: Use cases for medication intake, rescheduling, and adherence analytics.
  - **`ui/`**: Jetpack Compose UI screens (Dashboard, Medication Editor, History/Analytics, Settings), Material 3 design system, and ViewModel state holders.
  - **`receiver/` & `service/`**: Broadcast receivers (`AlarmReceiver`, `BootReceiver`) for reliable notification delivery even after device reboots (`BOOT_COMPLETED`).
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
│   └── learning/                  # UX and system constraint learnings
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
- **Absolute Paths**: NEVER leak local filesystem paths (e.g., `C:\Users\...`) into code, documentation, or commits. Always use relative paths (`app/src/main/...`).
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

1. **Exact Alarms & Doze Mode**: Med reminders require exact delivery. Use `AlarmManager.setExactAndAllowWhileIdle()` and handle permission checks for `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` gracefully on Android 12+.
2. **Notification Permissions (`POST_NOTIFICATIONS`)**: On Android 13+ (API 33+), runtime notification permissions must be requested before scheduling alarms.
3. **Room Database Migrations**: Every schema change to Room entities must increment the database version in `AppDatabase` and provide an explicit `Migration` or auto-migration rule.
4. **Boot Resilience**: The application listens to `ACTION_BOOT_COMPLETED` via `BootReceiver` to reschedule all pending alarms whenever the device is restarted.
