# Enhanced Alarm System Architecture Reference (Android 12–16)

This document details the architectural and technical patterns for:
1. **Person-level alarm suspension** (6 hours or remainder of the day).
2. **Advance silent pre-alarm notifications** (15 or 30 minutes before exact alarm time).
3. **Full-screen / Popup Alarm Activity (`AlarmActivity`)** over lock screen and heads-up banner display when ringing.

---

## 1. Person-Level Alarm Suspension (6 Hours vs Rest of Day)

### Data Modeling:
- Added `suspended_until_epoch_ms: Long?` to `persons` entity.
- When a person is suspended until $T_{suspend}$:
  - If suspending for **6 hours**: `suspendedUntilEpochMs = System.currentTimeMillis() + (6 * 3600 * 1000L)`
  - If suspending for **Rest of the day**: `suspendedUntilEpochMs = LocalDate.now().atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()`
- When `AlarmReceiver` triggers for a group:
  - It checks if the associated person has an active suspension (`person.suspendedUntilEpochMs > System.currentTimeMillis()`).
  - If suspended, it skips firing the notification / popup alarm, cancels any pending pre-alarm, and automatically schedules the next valid occurrence for tomorrow.
- UI:
  - In `HorariosScreen` (Person Filter Bar or top banner) and `PerfilesScreen`, quick intuitive action chips/menus: **"Pausar alarmas (6h)"**, **"Pausar por hoy"**, and **"Reanudar alarmas"**.

---

## 2. Advance Silent Pre-Alarm Notifications (15 / 30 mins before)

### Behavior:
- Configurable advance notice time (e.g. 15 or 30 min before, or default 15m/30m selectable in Settings or per group).
- **Separate Alarm Schedule**:
  - `AlarmScheduler` calculates $T_{pre} = T_{exact} - M_{advance}$.
  - If $T_{pre} > \text{now}$, schedules an exact alarm or intent for `AlarmReceiver` with action `ACTION_PRE_ALARM`.
- **Silent Notification Channel**:
  - `NotificationChannel` with `IMPORTANCE_LOW` or `IMPORTANCE_DEFAULT` without sound / vibration (Silent).
  - Title: "Próxima toma: [Nombre Horario] en [15/30] min"
  - Content: "Para [Persona]: [Medicamentos]"
  - Interactive Action Buttons:
    1. **"✅ Tomar ahora"**: Marks dose as taken for today, cancels the upcoming exact alarm + popup for today, and schedules tomorrow.
    2. **"❌ Desactivar por hoy"**: Skips today's intake without turning off the schedule, dismisses notification, and cancels today's alarm.
    3. **"Silenciar"**: Just dismisses the advance notification while keeping the exact alarm on time.

---

## 3. Full-Screen Alarm Popup (`AlarmActivity`)

### Android Constraints & System Behavior:
- Permission: `<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />`.
- Channel: `IMPORTANCE_HIGH` with `CATEGORY_ALARM`, sound & vibration.
- Lock screen handling:
  - `setShowWhenLocked(true)`
  - `setTurnScreenOn(true)`
  - `window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)`
- Display:
  - When locked: Android opens `AlarmActivity` full-screen immediately over the lock screen.
  - When unlocked: Displays a high-priority Heads-up banner that opens `AlarmActivity` or launches directly if configured.
- `AlarmActivity` Compose UI:
  - Visual pulsating alert banner with person badge, scheduled time, list of pills & dosages.
  - Big intuitive buttons:
    - **"✅ Confirmar Toma"** (Stops sound, cancels alarm, marks as taken).
    - **"⏳ Posponer 10 min"** (Stops sound, sets 10-minute snooze).
    - **"❌ Cancelar por hoy"** (Stops sound, skips for today).
