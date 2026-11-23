> **Created:** 2026-11-22
> **Last Updated:** 2026-11-22

# Learnings: Full-Screen Alarm Lockscreen Keyguard Overlay Trap & Bounded Scrollable Layout

## 1. Context & Problem Overview
Two critical usability issues were reported in the full-screen alarm screen (`AlarmActivity`):
1. **Unreachable Action Buttons with Many Medications:** When a schedule contained several active medications (either in the individual checkbox confirmation mode or the chip summary mode), the medication card expanded beyond the screen height and pushed the bottom action buttons (**"¡TOMADO!"**, **"Posponer..."**, **"Omitir hoy"**) completely off-screen with no way to scroll to them.
2. **Inability to Dismiss Alarm on a Locked Device:** When the full-screen alarm appeared while the phone was locked, the user could not stop or dismiss the ringing alarm.

---

## 2. Root Cause Analysis

### A. The `requestDismissKeyguard()` Lockscreen Trap
- **The Pitfall:** `AlarmActivity.configureLockScreenDisplay()` called `setShowWhenLocked(true)` and `setTurnScreenOn(true)`, and then immediately invoked:
  ```kotlin
  val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
  keyguardManager.requestDismissKeyguard(this, null)
  ```
  (and `FLAG_DISMISS_KEYGUARD` on legacy API levels).
- **The Failure Mode:** On any device protected by a secure lock screen (PIN, pattern, password, fingerprint, or face unlock), `requestDismissKeyguard()` commands the Android OS (`WindowManagerService` / `KeyguardController`) to immediately bring up the security authentication Bouncer (PIN pad / biometric prompt) **on top of** `AlarmActivity`.
- While the alarm ringtone played continuously on `STREAM_ALARM`, the user could not interact with the alarm buttons because the OS blocked touch delivery or covered the activity with the keyguard bouncer until the user unlocked the phone.
- **The Solution:** An alarm activity configured with `android:showWhenLocked="true"` and `setShowWhenLocked(true)` is already granted a window layer above the lock screen. It must **never** call `requestDismissKeyguard()` or set `FLAG_DISMISS_KEYGUARD` during alarm presentation. Removing those calls allows `AlarmActivity` to render and accept touch input directly over the locked keyguard, and calling `finish()` after the user taps an action button returns the device safely to the lock screen.

### B. Unbounded Card Expansion inside `Arrangement.SpaceBetween`
- **The Pitfall:** `AlarmPopupContent` placed three sections (Header, Medication `Card`, and Action Buttons `Column`) inside a parent `Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween)` without any weight constraint or scroll modifier on the middle `Card`.
- **The Failure Mode:** In Jetpack Compose, children without `Modifier.weight(...)` are measured first with unconstrained/full remaining height in declaration order. When the medication `Card` contained many items, it consumed all remaining vertical height, leaving zero space for the third child (the Action Buttons `Column`) and pushing it outside the viewport bounds.
- **The Solution:**
  1. Apply `.weight(1f, fill = false)` to the middle medication `Card`. In Compose layout measurement, non-weighted children (the top header and the bottom action buttons) are measured first and guaranteed their full intrinsic height, while the weighted `Card` takes at most the remaining space between them (`fill = false` ensures it stays compact when there are only 1–2 medications).
  2. Wrap the medication list inside the `Card` with `Box(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()))` so users can scroll through arbitrarily large medication lists while the primary confirmation, snooze, and skip buttons remain permanently pinned at the bottom.

---

## 3. Reference Summary

| Layer | Anti-Pattern | Hardened Pattern |
| :--- | :--- | :--- |
| **Lockscreen Window** | Calling `requestDismissKeyguard()` or `FLAG_DISMISS_KEYGUARD` in `AlarmActivity.onCreate()` | Use only `setShowWhenLocked(true)`, `setTurnScreenOn(true)`, `FLAG_SHOW_WHEN_LOCKED`, `FLAG_TURN_SCREEN_ON`, `FLAG_KEEP_SCREEN_ON` |
| **Full-Screen Layout** | Unweighted dynamic `Card` inside `Column(Arrangement.SpaceBetween)` | Pin header and footer; constrain middle `Card` with `.weight(1f, fill = false)` and inner `.verticalScroll()` |
