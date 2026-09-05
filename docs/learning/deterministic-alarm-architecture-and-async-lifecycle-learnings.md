> **Created:** 2026-09-05
> **Last Updated:** 2026-09-05

# Learnings: Deterministic Alarm Architecture, Single Source of Truth, and Asynchronous Lifecycle Resilience

## 1. Context & Architectural Motivation
In version 1.0.0 of Meds Reminder, medication confirmation, snooze, and dismissal were partially fragmented across ViewModels, Activities, and BroadcastReceivers. This occasionally caused:
1. **Race conditions with stale state:** UI actions and background broadcast receivers updating Room or rescheduling alarms with outdated in-memory copies.
2. **Orphaned alarms on early confirmation:** A postponed (snoozed) alarm or pre-alarm still triggering after a dose was logged manually in the app.
3. **Heuristic delay anti-patterns:** ViewModels and activities using `delay(500)` to wait for Room writes before finishing or re-arming alarms.
4. **Premature process death:** `BroadcastReceiver` performing coroutine calls on `Dispatchers.IO` without holding process execution guarantees, exposing operations to OS termination under aggressive memory reclamation or Doze mode.

---

## 2. Core Remediations & Architectural Decisions

### A. Room as Single Source of Truth (SSOT) & `MedicationScheduleRepository`
All state transitions for medication groups are now routed strictly through `MedicationScheduleRepository`:
```kotlin
interface MedicationScheduleRepository {
    suspend fun confirmIntake(groupId: Long, date: LocalDate = LocalDate.now(), notificationId: Int? = null)
    suspend fun snoozeSchedule(groupId: Long, snoozeTriggerEpochMs: Long, notificationId: Int? = null)
    suspend fun skipSchedule(groupId: Long, date: LocalDate = LocalDate.now(), notificationId: Int? = null)
}
```
**Benefits:**
- **Atomic 3-Step Coordination:** Every transition atomically (1) updates Room, (2) clears all associated notifications (main + pre-alarm + action banner via `cancelAllForGroup`), and (3) re-arms `AlarmManager` with the newly updated entity.
- **Stale Reference Immunity:** The repository refetches the freshly persisted entity from `groupDao.getGroupById(groupId)` prior to scheduling, eliminating stale in-memory reference drift.

---

### B. UI Decoupling with `AlarmViewModel` & `collectAsStateWithLifecycle`
`AlarmActivity` previously handled direct DAO injections and coroutine launches. In v1.1.0:
- Coroutines are scoped to `AlarmViewModel.viewModelScope`.
- `AlarmUiState` models `isLoading`, `groupWithMeds`, `person`, and `isFinished`.
- The Composable observes the state via `collectAsStateWithLifecycle()`.
- When `uiState.isFinished` becomes true, `AlarmActivity` finishes cleanly.

---

### C. Elimination of Artificial Delays (`delay()`)
- **Anti-Pattern:** Inserting `delay(500)` or thread sleep to let Room writes "settle".
- **Solution:** Structured concurrency with Kotlin suspend functions and Room transactional calls ensures sequential, guaranteed completion. Once `confirmIntake()` returns, state is guaranteed to be persisted in SQLite.

---

### D. Concurrency in `AlarmReceiver` & `goAsync()` Lifecycle Guarantee
- **Issue:** Broadcast receivers running coroutines with `CoroutineScope(Dispatchers.IO).launch` can be terminated by Android the moment `onReceive()` returns on the main thread.
- **Solution:**
  ```kotlin
  val pendingResult = goAsync()
  CoroutineScope(Dispatchers.IO).launch {
      try {
          // Perform database query, check suspension, show notification, reschedule fallback
      } finally {
          pendingResult.finish()
      }
  }
  ```
- **Fallback Reschedule with Active Snooze Precedence:** When `AlarmReceiver` fires `ACTION_FIRE_ALARM`, it reschedules the next calendar occurrence as a fallback in case the alarm is ignored. However, it checks `freshGroup.snoozeUntilEpochMs > System.currentTimeMillis()`. If an active snooze exists, it yields precedence to avoid overwriting the snooze `PendingIntent`.

---

## 3. Summary Matrix

| Concern | Legacy Pattern (v1.0.0) | Hardened Pattern (v1.1.0) |
| :--- | :--- | :--- |
| **State Coordination** | Ad-hoc DAO calls across Activities/Receivers | Unified `MedicationScheduleRepository` |
| **Synchronization** | Heuristic `delay(500)` workarounds | Sequential suspend functions & Room SSOT |
| **Alarm UI Architecture** | Activity-scoped coroutines with context leaks | Dedicated `AlarmViewModel` + `StateFlow` |
| **Receiver Lifecycle** | Unmanaged background coroutines | `goAsync()` with guaranteed `finish()` in `finally` |
| **Notification Invalidation** | Manual individual ID dismissals | `NotificationHelper.cancelAllForGroup(groupId)` |
| **Snooze Precedence** | Blind rescheduling overwriting snoozes | Guarded check: active snoozes take precedence |
