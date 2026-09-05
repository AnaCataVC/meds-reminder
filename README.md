# 💊 Meds Reminder — Native Android Application

[![Version](https://img.shields.io/badge/Version-1.1.0-emerald.svg?style=flat)](releases/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-purple.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg?style=flat&logo=android)](https://developer.android.com/jetpack/compose)
[![Room](https://img.shields.io/badge/Room%20DB-2.6.1-3DDC84.svg?style=flat&logo=sqlite)](https://developer.android.com/training/data-storage/room)
[![Koin](https://img.shields.io/badge/Koin-3.5.6-orange.svg?style=flat&logo=koin)](https://insert-koin.io)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

*Read this in [Español](#-versión-en-español)*

---

## English Version

### 1. Project Description
**Meds Reminder** is a 100% offline-first, native Android application engineered for high-reliability medication adherence and multi-profile dosage reminders. Built for families and caregivers, it enables users to manage multiple profiles, maintain a master medicine catalog, configure flexible schedule groups with deterministic alarm precision, assign custom device ringtones, temporarily suspend reminders per person (6 hours or remainder of the day), receive silent pre-alarm notifications (15/30 min before), display full-screen popups directly over lock screens, and atomically back up or restore data in JSON format via Android's Storage Access Framework (SAF).

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
In version 1.1.0, the alarm engine was comprehensively remediated into a robust, deterministic system that completely eliminates race conditions:
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

## Versión en Español

### 1. Descripción del Proyecto
**Meds Reminder** es una aplicación nativa de Android 100% local (offline-first), diseñada con estándares de alta confiabilidad para la adherencia a tratamientos médicos y recordatorios de dosis multi-perfil. Creada para familias y cuidadores, permite administrar múltiples perfiles, mantener un catálogo maestro de medicamentos, configurar horarios flexibles con alarmas de precisión determinista, asignar tonos personalizados del dispositivo, suspender temporalmente alarmas por persona (6 horas o el resto del día), recibir avisos previos silenciosos (15/30 min antes), mostrar ventanas emergentes interactivas sobre la pantalla de bloqueo y realizar respaldos o restauraciones atómicas en JSON mediante el *Storage Access Framework (SAF)* de Android.

### 2. Stack Tecnológico y Arquitectura
* **Lenguaje:** Kotlin (v2.0)
* **Interfaz de Usuario:** Jetpack Compose con Material Design 3
* **Arquitectura:** Clean Architecture + MVI/MVVM con `StateFlow` reactivo
* **Fuente Única de Verdad (SSOT):** Room Database con Kotlin Symbol Processing (KSP) y Auto-Migraciones
* **Repositorio de Dominio:** `MedicationScheduleRepository` orquestando transiciones de estado atómicas entre Room, `AlarmManager` y `NotificationManager`
* **Ciclo de Vida Consciente:** `AlarmViewModel` gobernando `AlarmActivity` mediante `collectAsStateWithLifecycle`
* **Inyección de Dependencias:** Koin (DSL liviano en Kotlin, sin reflexión ni sobrecarga de generación de código)
* **Serialización:** `kotlinx.serialization` para exportación/importación atómica de JSON con esquemas versionados
* **Servicios de Sistema y Segundo Plano:**
  * `AlarmManager.setAlarmClock()` (disparos exactos bajo excepción médica)
  * `BroadcastReceiver.goAsync()` para operaciones asíncronas seguras y sin bloqueos
  * Canales de notificación dinámicos por hash de URI de tono (`meds_channel_tone_${hash}`)
  * Full-Screen Intents (`USE_FULL_SCREEN_INTENT`) con `KeyguardManager` y `setTurnScreenOn`

### 3. Arquitectura Determinista de Alarmas
En la versión 1.1.0, el motor de alarmas fue rediseñado exhaustivamente para garantizar una ejecución determinista y eliminar condiciones de carrera:
* **Room como Fuente Única de Verdad (SSOT):** Todos los registros de toma, aplazamientos y modificaciones de horarios mutan en primer lugar la base de datos Room (`markGroupAsTaken`, `setSnoozeTime`, `markGroupSkippedToday`). La interfaz de usuario, los receptores del sistema y los planificadores consultan el estado directo de Room, evitando desfasajes de memoria y dobles alertas.
* **Contrato `MedicationScheduleRepository`:** Coordinador de dominio que garantiza atomicidad. Cuando una dosis se confirma, pospone o descarta, el repositorio ejecuta de forma atómica:
  1. La actualización del registro en Room.
  2. La cancelación inmediata de todas las notificaciones asociadas (alarma principal, pre-alarma silenciosa y banners interactivos) mediante `NotificationHelper.cancelAllForGroup()`.
  3. La reprogramación o cancelación determinista en `AlarmManager`.
* **Desacoplamiento con `AlarmViewModel`:** `AlarmActivity` delega todas las operaciones asíncronas y corrutinas a su propio `AlarmViewModel`. El estado de la pantalla se expone vía `StateFlow<AlarmUiState>` y se consume con `collectAsStateWithLifecycle()`. El cierre de la actividad se produce reactivamente al cambiar `uiState.isFinished`, eliminando fugas de contexto y corrutinas ligadas a la vista.
* **Eliminación Total de Delays Artificiales:** Se erradicaron por completo las pausas heurísticas y esperas artificiales (`delay(500)`) en los flujos de disparo, confirmación y cierre de alarmas. Cada transición responde de inmediato al término de la transacción asíncrona de Room.

### 4. Aprendizajes Clave de Ingeniería y Restricciones del Sistema
* **Concurrencia en AlarmManager y Precedencia de Posposiciones:** Al activarse `ACTION_FIRE_ALARM`, el receptor programa el siguiente día del calendario como respaldo en caso de que el usuario ignore la alerta. Sin embargo, si existe una posposición activa futura (`snoozeUntilEpochMs > now`) en Room, la reprogramación cede la prioridad para no sobrescribir el `PendingIntent` del snooze.
* **Resiliencia en Modo Doze:** El uso de `AlarmManager.setAlarmClock()` asegura la activación del procesador incluso en suspensión profunda (*Doze mode*) y bajo las restricciones de *App Standby* mediante la excepción médica (`USE_EXACT_ALARM`), mostrando el icono de reloj en la pantalla de bloqueo y logrando precisión al milisegundo.
* **Ciclo de Vida con `BroadcastReceiver.goAsync()`:** Android finaliza los procesos de `BroadcastReceiver` en cuanto `onReceive()` retorna en el hilo principal. Mediante `val pendingResult = goAsync()`, el receptor delega las consultas a Room y el envío de notificaciones a un `CoroutineScope(Dispatchers.IO)` finalizando con `pendingResult.finish()` dentro de un bloque `finally`, evitando que el sistema operativo mate el proceso prematuramente.
* **Cancelación Dual de Notificaciones:** Los avisos previos silenciosos (`groupId + 100000`) y las alarmas sonoras principales (`groupId`) operan con identificadores distintos. Registrar la toma anticipada cancela ambos identificadores a la vez, impidiendo notificaciones fantasma posteriores.

### 5. Instrucciones de Configuración Local
1. Clona el repositorio:
   ```bash
   git clone https://github.com/AnaCataVC/meds-reminder.git
   ```
2. Abre el proyecto en **Android Studio Jellyfish (2024.1+)** o superior.
3. Asegúrate de tener configurado JDK 17 en los ajustes de Gradle.
4. Ejecuta las pruebas unitarias:
   ```bash
   ./gradlew testDebugUnitTest
   ```
5. Ejecuta la app en un emulador o dispositivo físico con Android 8.0+ (API 26+).

### 6. Optimización de Batería y Ejecución en Segundo Plano
Para asegurar que las alarmas de medicamentos suenen sin retraso en Android:
* **Filtro de Optimización de Batería**: En los ajustes de *Optimización de batería*, cambia el selector superior de *"Sin optimizar"* a *"Todas las aplicaciones"*, localiza **Meds Reminder** y marca *"No optimizar"*.
* **Ajuste Directo**: También puedes ir a *Información de la app -> Batería* y seleccionar **"Sin restricciones"**.
* **Fabricantes OEM**: En Xiaomi activa *Inicio automático* y en Samsung agrega la app a *Aplicaciones nunca suspendidas*.
