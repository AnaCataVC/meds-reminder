# 💊 Meds Reminder — Native Android Application

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-purple.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg?style=flat&logo=android)](https://developer.android.com/jetpack/compose)
[![Room](https://img.shields.io/badge/Room%20DB-2.6.1-3DDC84.svg?style=flat&logo=sqlite)](https://developer.android.com/training/data-storage/room)
[![Koin](https://img.shields.io/badge/Koin-3.5.6-orange.svg?style=flat&logo=koin)](https://insert-koin.io)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

*Read this in [Español](#-versión-en-español)*

---

## 🇺🇸 English Version

### 1. Project Description
**Meds Reminder** is a 100% offline-first, native Android application engineered for medication adherence and multi-profile dosage reminders. It allows users to manage multiple family members or profiles, create master medicine catalogs, configure schedule groups with exact alarm precision, choose custom device ringtones, suspend alarms per person (6h or rest of the day), receive silent pre-alarm notifications (15/30 min before), display full-screen popups over lock screens, and seamlessly back up or restore data in JSON format via Android's Storage Access Framework (SAF).

### 2. Technologies Used
* **Language:** Kotlin (v2.0)
* **UI Toolkit:** Jetpack Compose with Material Design 3
* **Architecture:** Clean Architecture + MVI/MVVM pattern with reactive `StateFlow`
* **Local Persistence:** Room Database with Kotlin Symbol Processing (KSP) & Auto-Migrations
* **Dependency Injection:** Koin (Zero-codegen, lightweight Kotlin DSL)
* **Serialization:** `kotlinx.serialization` for zero-reflection JSON processing
* **System Services:** `AlarmManager.setAlarmClock()`, `RingtoneManager`, Full-Screen Intents (`USE_FULL_SCREEN_INTENT`), and `NotificationManager` dynamic channels

### 3. Key Features & Engineering Highlights
* **Full-Screen Alarm Popup (`AlarmActivity`):** Launches an interactive Compose activity directly over the lockscreen (`setShowWhenLocked`, `setTurnScreenOn`) or high-priority Heads-up banner when the phone is active, clearly indicating who the medication is for and exact dosages.
* **Person-Level Smart Suspension:** Ability to temporarily pause all alarms for a specific family member (for 6 hours or remainder of the day) with automatic reactivation when the period expires.
* **Advance Silent Notifications:** Configurable pre-alarm notifications (15 or 30 minutes before) on a silent low-priority channel allowing users to mark doses as taken or skip for today with a single tap.
* **Android 14/15/16 Exact Alarm Reliability:** Leveraged `USE_EXACT_ALARM` (under the medical compliance exception) and `AlarmManager.setAlarmClock()` to guarantee alarm execution even in deep Android Doze mode.
* **NotificationChannel Sound Immutability:** Addressed Android 8.0+ sound binding limitations by programmatically constructing deterministic notification channels per custom ringtone URI hash (`meds_channel_tone_${hash}`).
* **Zero-History Ephemeral Tracking:** Maintained a clean 4-table relational database schema with same-day completion tracking (`lastTakenDate`) without bloating device storage with audit logs.
* **Storage Access Framework (SAF):** Implemented schema-versioned JSON backup and atomic transactional import.

### 4. Local Setup Instructions
1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/meds-reminder.git
   ```
2. Open the project in **Android Studio Jellyfish | 2024.1+** (or newer).
3. Ensure JDK 17+ is configured in `Gradle Settings`.
4. Build and execute unit tests:
   ```bash
   ./gradlew testDebugUnitTest
   ```
5. Run the app on an Android device or emulator running Android 8.0+ (API 26+).

### 5. Battery Optimization & Background Execution
To ensure medication alarms ring reliably on Android:
* **Battery Optimization List Filter**: In system *Battery Optimization* settings, switch the top filter from *"Not optimized"* to *"All apps"*, find **Meds Reminder**, and set it to *"Don't optimize"*.
* **App Info Settings**: Alternatively, navigate to *App Info -> Battery* and select **"Unrestricted"**.
* **OEM Customizations**: On Xiaomi (MIUI/HyperOS) enable *Autostart*, and on Samsung add the app to *Never sleeping apps*.

---

## 🇪🇸 Versión en Español

### 1. Descripción del Proyecto
**Meds Reminder** es una aplicación nativa de Android 100% local (sin nube) diseñada para la gestión y recordatorio puntual de medicamentos por persona. Permite administrar múltiples perfiles, mantener un catálogo maestro de fármacos reutilizables, agrupar tomas en horarios con alarmas de alta precisión, suspender temporalmente alarmas por persona (6h o resto del día), recibir avisos previos silenciosos (15/30 min antes), mostrar alarmas popup en pantalla completa sobre la pantalla bloqueada, personalizar tonos de alerta desde la biblioteca del dispositivo y realizar respaldos o restauraciones en formato JSON mediante el *Storage Access Framework (SAF)* de Android.

### 2. Tecnologías Utilizadas
* **Lenguaje:** Kotlin (v2.0)
* **Interfaz de Usuario:** Jetpack Compose con Material Design 3
* **Arquitectura:** Clean Architecture + MVI/MVVM con `StateFlow` reactivo
* **Base de Datos Local:** Room Database con KSP y Auto-Migraciones
* **Inyección de Dependencias:** Koin (DSL liviano en Kotlin, sin sobrecarga de generación de código)
* **Serialización:** `kotlinx.serialization` para exportación/importación JSON atómica y rápida
* **Servicios de Sistema:** `AlarmManager.setAlarmClock()`, `RingtoneManager`, Full-Screen Intents (`USE_FULL_SCREEN_INTENT`) y canales dinámicos de `NotificationManager`

### 3. Características Principales y Aprendizajes de Ingeniería
* **Alarma Popup en Pantalla Completa (`AlarmActivity`):** Despliega una ventana interactiva directamente sobre la pantalla de bloqueo (`setShowWhenLocked`, `setTurnScreenOn`) o un banner flotante prioritario Heads-Up cuando el dispositivo está en uso, mostrando claramente el destinatario y los medicamentos.
* **Suspensión Inteligente por Persona:** Pausa temporal de todas las alarmas asociadas a un perfil (por 6 horas o por lo que resta del día) con reactivación automática sin intervención manual.
* **Avisos Previos Silenciosos:** Notificación anticipada (15 o 30 minutos antes) en un canal silencioso con botones de un solo toque para *"✅ Tomar ya"* o *"❌ Desactivar hoy"*, evitando que suene la alarma principal.
* **Confiabilidad en Android 14/15/16:** Uso de `USE_EXACT_ALARM` y `setAlarmClock()` para asegurar el disparo exacto al milisegundo aún en modo *Doze*.
* **Manejo de Tonos Dinámicos:** Solución a la inmutabilidad de sonido en canales de notificación (Android 8.0+) creando canales dinámicos según el hash del tono (`meds_channel_tone_${hash}`).
* **Arquitectura sin Historial:** Esquema relacional de 4 tablas sin acumulación de tablas de auditoría, controlando la toma diaria mediante estado efímero (`lastTakenDate`).
* **Respaldo con SAF:** Serialización atómica en JSON con DTOs versionados (`schema_version = 1`).

### 4. Instrucciones de Configuración Local
1. Clona el repositorio:
   ```bash
   git clone https://github.com/your-username/meds-reminder.git
   ```
2. Abre el proyecto en **Android Studio Jellyfish (2024.1+)** o superior.
3. Asegúrate de tener configurado JDK 17 en los ajustes de Gradle.
4. Ejecuta las pruebas unitarias:
   ```bash
   ./gradlew testDebugUnitTest
   ```
5. Ejecuta la app en un emulador o dispositivo físico con Android 8.0+ (API 26+).

### 5. Optimización de Batería y Ejecución en Segundo Plano
Para asegurar que las alarmas de medicamentos suenen sin retraso en Android:
* **Filtro de Optimización de Batería**: En los ajustes de *Optimización de batería*, cambia el selector superior de *"Sin optimizar"* a *"Todas las aplicaciones"*, localiza **Meds Reminder** y marca *"No optimizar"*.
* **Ajuste Directo**: También puedes ir a *Información de la app -> Batería* y seleccionar **"Sin restricciones"**.
* **Fabricantes OEM**: En Xiaomi activa *Inicio automático* y en Samsung agrega la app a *Aplicaciones nunca suspendidas*.

