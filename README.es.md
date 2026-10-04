<p align="center">
  <img src="icon.png" alt="meds-reminder Logo" width="120" />
</p>

# Meds Reminder

[English](README.md) | [Español](README.es.md)

[![Version](https://img.shields.io/badge/Version-1.4.0-emerald.svg?style=flat)](releases/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-purple.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg?style=flat&logo=android)](https://developer.android.com/jetpack/compose)
[![Room](https://img.shields.io/badge/Room%20DB-2.6.1-3DDC84.svg?style=flat&logo=sqlite)](https://developer.android.com/training/data-storage/room)
[![Koin](https://img.shields.io/badge/Koin-3.5.6-orange.svg?style=flat&logo=koin)](https://insert-koin.io)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=flat)](LICENSE)

---


### 1. Descripción del Proyecto
**Meds Reminder** es una aplicación nativa de Android 100% local (offline-first), diseñada con estándares de alta confiabilidad para la adherencia a tratamientos médicos y recordatorios de dosis multi-perfil. Creada para familias y cuidadores, permite administrar múltiples perfiles, mantener un catálogo maestro de medicamentos, configurar horarios flexibles con alarmas de precisión determinista, asignar tonos personalizados del dispositivo, suspender temporalmente alarmas por persona (6 horas o el resto del día), recibir avisos previos silenciosos (15/30 min antes), configurar ciclos de descanso (por ejemplo, anticonceptivos: 21 días de toma y 7 de descanso), mostrar ventanas emergentes interactivas sobre la pantalla de bloqueo y realizar respaldos o restauraciones atómicas en JSON mediante el *Storage Access Framework (SAF)* de Android.

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
En la versión 1.2.0, el motor de alarmas fue rediseñado exhaustivamente para garantizar una ejecución determinista y eliminar condiciones de carrera:
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

---

## Licencia

Este proyecto está bajo la Licencia MIT. Consulta el archivo [LICENSE](LICENSE) para más detalles.

