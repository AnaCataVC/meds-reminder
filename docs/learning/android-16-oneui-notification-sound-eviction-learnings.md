> **Created:** 2026-10-12
> **Last Updated:** 2026-10-12

# Learnings: Android 16 & Samsung One UI Notification Sound Eviction and Immediate Audio Muting

## 1. Context & Architectural Overview
On Android 16 (API 36) and Samsung devices running One UI 8.x / 6.x, users reported that when answering a full-screen medication reminder popup by tapping **"¡TOMADO! (Confirmar)"**, the alarm ringtone continued ringing loudly for 30–60 seconds after the screen closed.

This caused severe user friction: users were uncertain whether their confirmation had been registered, while having to listen to the rest of the ringtone melody at full volume.

---

## 2. Root Cause Analysis

### A. The OS Decoupling of Visual Notifications vs Audio Playback
- **The Pitfall:** In Android, high-priority notifications configured with `AudioAttributes.USAGE_ALARM` delegate audio playback asynchronously to the system audio player (`NotificationPlayer` / `AudioService`).
- **The Failure Mode:** When the application called `NotificationManager.cancel(notificationId)` upon intake confirmation, Android removed the visual notification badge and dismissed the Heads-Up notification shade. However, **`NotificationManager.cancel()` does not terminate the underlying audio playback session** in the system mixer. 
- Because standard OEM ringtones (especially Samsung default alarm tunes like *Homecoming* or *Morning Flower*) last 30 to 60 seconds, the audio engine plays the file to completion unless forcefully evicted.

### B. Asynchronous Database Latency before Dismissal
- In previous versions, state transitions (`confirmIntake`, `snoozeSchedule`, `skipSchedule`) performed asynchronous SQLite writes to Room before dispatching `cancelAllForGroup()`.
- Any I/O thread latency or coroutine dispatch delays postponed the notification cancellation, creating perceptible lag before the system received the cancel signal.

---

## 3. Key Architectural Remediations

### 1. Active Audio Focus Eviction (`AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`)
In `NotificationHelper.silenceAlarmAudio()`:
```kotlin
val alarmAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ALARM)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
    .setAudioAttributes(alarmAttributes)
    .build()

audioManager.requestAudioFocus(focusRequest)
audioManager.abandonAudioFocusRequest(focusRequest)
```
- Requesting `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` with `USAGE_ALARM` signals the OS audio policy manager (`MediaFocusControl`) that an exclusive alarm event takes immediate precedence.
- This immediately revokes audio focus from `NotificationPlayer`, killing the ongoing ringtone stream.
- Abandoning focus immediately restores the normal audio routing state.

### 2. Transient Stream Mute Hardware Safety Cycle
As a defense-in-depth measure against aggressive OEM skins:
```kotlin
runCatching {
    audioManager.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_MUTE, 0)
    audioManager.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_UNMUTE, 0)
}
```
This forces the audio hardware mixer to drop any buffered PCM frames on `STREAM_ALARM` without altering the user's permanent alarm volume.

### 3. Immediate UI-Thread Invocation before Coroutine Dispatch
In `AlarmViewModel`:
```kotlin
private fun resolve(action: suspend (groupId: Long) -> Unit) {
    val groupId = currentGroupId
    if (groupId == -1L || isResolving) return
    isResolving = true

    // Synchronously silence audio and clear notifications on the main thread
    notificationHelper.cancelAllForGroup(groupId)

    viewModelScope.launch {
        try {
            action(groupId)
            _uiState.update { it.copy(isFinished = true) }
        } finally {
            isResolving = false
        }
    }
}
```
The instant the user's finger taps the screen, the notification is cancelled and the audio stream is silenced within the same UI frame loop, prior to Room database writes.

### 4. Immediate Notification Action Receiver Dismissal
In `NotificationActionReceiver`:
```kotlin
if (action != NotificationHelper.ACTION_DISMISS_PRE_ALARM) {
    notificationHelper.cancelAllForGroup(groupId, notificationId)
}
```
Interactive notification action clicks ("Tomado", "Posponer") silence audio instantly before launching asynchronous background tasks via `goAsync()`.

---

## 4. Reference Matrix

| Layer | Symptom / Anti-Pattern | Hardened Architecture |
| :--- | :--- | :--- |
| **Notification Audio** | `NotificationManager.cancel()` only removes visual card; audio plays to end | `NotificationHelper.silenceAlarmAudio()` via `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` |
| **Hardware Mixer** | Stuck PCM ringtone buffers on custom OEM ROMs (Samsung One UI) | Transient `ADJUST_MUTE` -> `ADJUST_UNMUTE` cycle on `STREAM_ALARM` |
| **Vibration Engine** | Residual haptic patterns vibrating after screen close | `vibrator.cancel()` through `VibratorManager` (API 31+) |
| **UI Touch Responsiveness** | Waiting for Room coroutine writes before calling cancel | Synchronous dismissal directly inside `AlarmViewModel.resolve` |
| **Notification Actions** | Sound persisting while `goAsync()` coroutine executes | Immediate synchronous `cancelAllForGroup()` call in `onReceive()` |
