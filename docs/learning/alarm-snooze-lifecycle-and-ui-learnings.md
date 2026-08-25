> **Created:** 2026-08-24
> **Last Updated:** 2026-08-24

# Learnings: Alarm Snooze Lifecycle Invalidation and Compose Card UX Cohesion

## 1. Context & Architectural Overview
During daily medication tracking interactions, we addressed three critical usability and scheduling edge cases:
- **Snooze Alarm Invalidation**: When a user postponed an alarm (`Snooze 10m`) and subsequently manually confirmed the medication intake via the app UI, the scheduled `AlarmManager` one-shot snooze broadcast remained queued and fired later unexpectedly.
- **Card Theming Consistency**: Groups marked as completed for the day used an opaque `surfaceVariant` tint that clashed with the surrounding Material 3 light/dark palette, creating harsh contrast borders.
- **Action Semantics**: Action verbs on group cards used infinitive labels (`"Tomar"`) that caused ambiguity regarding whether the button was an instruction or a state-recording action.

---

## 2. Key Architectural Decisions & Gotchas

### A. Explicit PendingIntent & Notification Cancellation on Manual Confirmation
- **Issue:** Pospone / Snooze triggers register an exact one-shot alarm via `AlarmManager.setAlarmClock()`. Marking a group as taken in the Room database (`markGroupAsTaken`) cleared the `snooze_until_epoch_ms` timestamp in the database, but left the active OS `PendingIntent` intact in the system `AlarmManager`.
- **Consequence:** Even after confirming the intake in the UI, the postponed alarm still went off at the snooze trigger time.
- **Solution:** 
  In `MainViewModel.confirmIntakeToday()`:
  1. Dismiss active notifications via `NotificationHelper.cancelNotification()`.
  2. Invoke `alarmScheduler.cancel(group)` to invalidate the active `PendingIntent` and pre-alarm triggers in `AlarmManager`.
  3. Re-schedule the regular daily cycle for the next scheduled day using `alarmScheduler.schedule(group)`.

---

### B. Compose Card Visual Hierarchy for Completed States
- **Issue:** Using `containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)` on completed cards produced an awkward gray container that visually appeared disabled or broken rather than completed.
- **Solution:**
  - Standardize container color to `MaterialTheme.colorScheme.surfaceContainerLow` across all states.
  - Express the completed status purely through contextual indicators: a dedicated badge (`"✓ Tomado hoy"` in green container) and a slight elevation reduction (`1.dp` vs `2.dp`).
  - This preserves UI cleanliness while clearly communicating state without jarring background contrast.

---

### C. UX Action Labeling: Direct Verbs vs State Actions
- **Issue:** Using `"Tomar"` on the confirmation CTA caused confusion because it read as a command rather than a user action to confirm a completed dose.
- **Solution:**
  - Use `"Marcar tomado"` for pending state to clearly denote a logging action.
  - Use `"¡Listo!"` / `"✓ Tomado"` (disabled) when the medication has already been logged for today.

---

## 3. Reference Summary

| Area | Pitfall | Recommended Pattern |
| :--- | :--- | :--- |
| **Snooze Lifecycle** | Clearing DB snooze timestamp without canceling `PendingIntent` | Always pair database updates with `alarmScheduler.cancel(group)` and `schedule(group)` |
| **Notification Sync** | Leaving notification visible after in-app confirmation | Call `notificationHelper.cancelNotification(groupId)` on intake confirmation |
| **Card Styling** | Opaque `surfaceVariant` grey backgrounds | Use uniform `surfaceContainerLow` with subtle badge status |
| **Button Action** | Ambiguous infinitive `"Tomar"` | Use explicit action `"Marcar tomado"` |
