> **Created:** 2026-08-25
> **Last Updated:** 2026-08-25

# Learnings: Early Intake Alarm Rollover, In-Memory State Drift, and Multi-Layer Fail-Safes

## 1. Context & Architectural Overview
In scheduled medication management apps, users frequently take their prescribed doses before the scheduled alarm time (e.g., taking an afternoon dose at 10:00 AM instead of 2:00 PM and clicking **"Marcar tomado"**).

We resolved an insidious bug where, despite marking a medication group as taken for the day:
1. The exact scheduled alarm still rang at 2:00 PM.
2. The silent pre-alarm (advance notice notification) still popped up 15–30 minutes prior.

---

## 2. Root Cause Analysis & Pitfalls

### A. Pure Time-Comparison Flaw in Rollover Calculation
- **Gotcha:** `AndroidAlarmScheduler.calculateNextTriggerTime()` only compared `targetDateTime` against `referenceNow` (`targetDateTime.isBefore(referenceNow)`).
- **Failure Mode:** When evaluated at 10:00 AM for a 2:00 PM schedule, 2:00 PM was strictly in the future. The scheduler re-armed the system `AlarmManager` for today at 2:00 PM instead of rolling over to tomorrow or the next active day.

### B. In-Memory Entity Stale Reference Drift
- **Gotcha:** Room DAO update methods (such as `groupDao.markGroupAsTaken(groupId, today)`) update the underlying SQLite row asynchronously, but do not mutate already-loaded Kotlin data class instances (`groupWithMeds.group`).
- **Failure Mode:** Calling `alarmScheduler.schedule(groupWithMeds.group)` right after `markGroupAsTaken` passed an entity whose `lastTakenDate` was still `null` or yesterday's date, bypassing any `lastTakenDate` checks.

### C. Missing Dispatch-Time Fail-Safe in `AlarmReceiver`
- **Gotcha:** Relying solely on `PendingIntent` cancellations is fragile if an alarm was already scheduled or queued in the OS alarm queue prior to database updates.
- **Failure Mode:** When `AlarmReceiver` woke up on `ACTION_FIRE_ALARM` or `ACTION_PRE_ALARM`, it immediately showed full-screen alerts and notifications without checking if the dose had already been recorded today.

---

## 3. Key Architectural Patterns & Hardening Solutions

### 1. Rollover Invariant in Scheduling Logic
In `AndroidAlarmScheduler.calculateNextTriggerTime`:
```kotlin
val alreadyTakenToday = group.lastTakenDate != null && group.lastTakenDate >= targetDateTime.toLocalDate()
if (alreadyTakenToday || targetDateTime.isBefore(referenceNow) || targetDateTime.isEqual(referenceNow)) {
    targetDateTime = targetDateTime.plusDays(1)
}
```
If the medication was marked as taken today (or on or after the target schedule date), the scheduler unconditionally advances to the next valid weekday according to `daysOfWeekMask`.

### 2. Multi-Layer Dispatch Fail-Safe in `AlarmReceiver`
In `AlarmReceiver.onReceive`:
```kotlin
val today = LocalDate.now()
val isAlreadyTakenToday = groupWithMeds.group.lastTakenDate?.isEqual(today) == true

if (isPersonSuspended || isAlreadyTakenToday) {
    if (action == ACTION_FIRE_ALARM) {
        // Automatically reschedule for the next valid day without alerting
        alarmScheduler.schedule(groupWithMeds.group)
    }
    return@launch
}
```

### 3. Explicit In-Memory Copy & Dual-Notification Invalidation
In `AlarmActivity`, `NotificationActionReceiver`, and `MainViewModel`:
```kotlin
groupDao.markGroupAsTaken(groupId, today)
notificationHelper.cancelNotification(groupId.toInt())          // Main alarm notification
notificationHelper.cancelNotification(groupId.toInt() + 100000) // Pre-alarm notification

// Ensure refreshed state is scheduled
val updatedGroup = group.copy(lastTakenDate = today, snoozeUntilEpochMs = null)
alarmScheduler.schedule(updatedGroup)
```

---

## 4. Reference Summary

| Layer | Pitfall | Hardened Pattern |
| :--- | :--- | :--- |
| **Scheduler** | Target time in the future treated as pending today | Check `lastTakenDate >= targetDate` to force rollover to next cycle |
| **Receiver** | Blindly dispatching alarm UI upon OS wake-up | Check `lastTakenDate == LocalDate.now()` as a fail-safe gate |
| **Action Handlers** | Passing stale in-memory entity copies to scheduler | Use `group.copy(lastTakenDate = today)` or refetch from DAO |
| **Notifications** | Orphaned pre-alarm notifications lingering | Cancel both `groupId` and `groupId + 100000` identifiers |
