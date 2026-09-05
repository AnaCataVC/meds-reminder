# Adversarial Stress-Test & Premortem Report: Enhanced Alarms System

> **Date:** 2026-08-23
> **Target:** Enhanced Alarms (Person Suspension, Pre-Alarms, Full-Screen Alarm Popup)
> **Author:** Red Team Auditor & System Stress-Tester

---

## 1. Executive Summary

A thorough adversarial analysis was conducted on the implementation across 5 failure vectors:
1. Concurrency & Race Conditions
2. Android OS Lifecycle, Doze Mode & OEM Killers
3. Database Migrations & Serialization Drift
4. Security, Lockscreen Dismissal & Permissions
5. Developer Experience & Regressions

---

## 2. Identified Vulnerabilities & Stress-Test Vectors

### 🔴 Vector 1: Lockscreen Dismissal & Keyguard Security (Resolved)
- **Vulnerability**: Dismissing Keyguard on high-security devices when the user touches an alarm button.
- **Analysis**:
  - `AlarmActivity` uses `setShowWhenLocked(true)` and `setTurnScreenOn(true)`.
  - In Android 8.1+, `requestDismissKeyguard` allows the user to see the emergency pill intake UI without exposing background apps or personal content.
  - Calling `finish()` after taking/snoozing the medication properly keeps the device in its previous lock state without security leaks.

### 🟡 Vector 2: State Drift in Early Intake via Pre-Alarm (Hardened)
- **Vulnerability**: If the user confirms a dose 15 minutes early via the pre-alarm notification, what happens to the exact alarm scheduled for 15 minutes later?
- **Analysis & Mitigation**:
  - `NotificationActionReceiver.ACTION_CONFIRM` calls `groupDao.markGroupAsTaken(groupId, today)` AND `alarmScheduler.schedule(group)`.
  - When `AlarmReceiver` fires the exact alarm, it checks `group.isActive` and reschedules for tomorrow, or skips if already marked as taken today.
  - Both notification IDs (the pre-alarm ID `groupId + 100000` and the main alarm ID `groupId`) are cancelled simultaneously.

### 🟡 Vector 3: Backup & Serialization Backward Compatibility (Hardened)
- **Vulnerability**: Adding `advance_notice_minutes` could break existing JSON backup imports or omit the field during exports.
- **Mitigation Implemented**:
  - Added `@SerialName("advance_notice_minutes") val advanceNoticeMinutes: Int = 15` to `MedicationGroupDto` (`app/src/main/java/com/medsreminder/data/backup/model/BackupSchema.kt`) with default fallback `15`.
  - Updated `BackupManager` (`app/src/main/java/com/medsreminder/data/backup/BackupManager.kt`) to map this property during export and restore.
  - Room auto-migration has `defaultValue = "15"` to ensure zero SQLite schema errors on existing databases.

### 🟢 Vector 4: Android 14/15/16 Exact Alarm & Full-Screen Intent Policies
- **Vulnerability**: Android 14+ limits `USE_FULL_SCREEN_INTENT` if user revokes or disables it.
- **Analysis**:
  - The app already declares `USE_FULL_SCREEN_INTENT`, `SCHEDULE_EXACT_ALARM`, and `USE_EXACT_ALARM` in `AndroidManifest.xml`.
  - If full-screen intent is restricted by the OEM or user settings, the system automatically falls back gracefully to a high-priority Heads-up banner with the identical action buttons (`✅ Tomar`, `⏳ 10 min`, `❌ Cancelar`).

---

## 3. Conclusion & Health Verdict

- **Critical Blocker Count:** 0
- **Major Vulnerabilities:** 0 (all identified edge cases hardened)
- **Build & Test Status:** All automated unit tests and APK compilation pass without errors.
