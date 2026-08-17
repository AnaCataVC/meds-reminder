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
**Meds Reminder** is a 100% offline-first, native Android application engineered for medication adherence and multi-profile dosage reminders. It allows users to manage multiple family members or profiles, create master medicine catalogs, configure schedule groups with exact alarm precision, choose custom device ringtones, and seamlessly back up or restore data in JSON format via Android's Storage Access Framework (SAF).

### 2. Technologies Used
* **Language:** Kotlin (v2.0)
* **UI Toolkit:** Jetpack Compose with Material Design 3
* **Architecture:** Clean Architecture + MVI/MVVM pattern with reactive `StateFlow`
* **Local Persistence:** Room Database with Kotlin Symbol Processing (KSP)
* **Dependency Injection:** Koin (Zero-codegen, lightweight Kotlin DSL)
* **Serialization:** `kotlinx.serialization` for zero-reflection JSON processing
* **System Services:** `AlarmManager.setAlarmClock()`, `RingtoneManager`, and `NotificationManager` dynamic channels

### 3. Key Learnings & Engineering Highlights
* **Android 14/15 Exact Alarm Reliability:** Leveraged `USE_EXACT_ALARM` (under the medical compliance exception) and `AlarmManager.setAlarmClock()` to guarantee alarm execution even in deep Android Doze mode.
* **NotificationChannel Sound Immutability:** Addressed Android 8.0+ sound binding limitations by programmatically constructing deterministic notification channels per custom ringtone URI hash (`meds_channel_tone_${hash}`).
* **Zero-History Ephemeral Tracking:** Maintained a clean 4-table relational database schema with same-day completion tracking (`lastTakenDate`) without bloating the device storage with unnecessary audit logs.
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
   ./gradlew test
   ```
5. Run the app on an Android device or emulator running Android 8.0+ (API 26+).

---

## 🇪🇸 Versión en Español

### 1. Descripción del Proyecto
**Meds Reminder** es una aplicación nativa de Android 100% local (sin nube) diseñada para la gestión y recordatorio puntual de medicamentos por persona. Permite administrar múltiples perfiles, mantener un catálogo maestro de fármacos reutilizables, agrupar tomas en horarios con alarmas de alta precisión, personalizar tonos de alerta desde la biblioteca del dispositivo y realizar respaldos o restauraciones en formato JSON mediante el *Storage Access Framework (SAF)* de Android.

### 2. Tecnologías Utilizadas
* **Lenguaje:** Kotlin (v2.0)
* **Interfaz de Usuario:** Jetpack Compose con Material Design 3
* **Arquitectura:** Clean Architecture + MVI/MVVM con `StateFlow` reactivo
* **Base de Datos Local:** Room Database con KSP
* **Inyección de Dependencias:** Koin (DSL liviano en Kotlin, sin sobrecarga de generación de código)
* **Serialización:** `kotlinx.serialization` para exportación/importación JSON atómica y rápida
* **Servicios de Sistema:** `AlarmManager.setAlarmClock()`, `RingtoneManager` y canales dinámicos de `NotificationManager`

### 3. Aprendizajes Clave de Ingeniería
* **Confiabilidad en Android 14/15:** Uso de `USE_EXACT_ALARM` y `setAlarmClock()` para asegurar el disparo exacto al milisegundo aún en modo *Doze*.
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
   ./gradlew test
   ```
5. Ejecuta la app en un emulador o dispositivo físico con Android 8.0+ (API 26+).
