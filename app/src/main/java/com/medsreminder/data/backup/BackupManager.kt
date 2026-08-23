package com.medsreminder.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.medsreminder.core.alarm.AndroidAlarmScheduler
import com.medsreminder.data.backup.model.BackupEnvelope
import com.medsreminder.data.backup.model.BackupPayload
import com.medsreminder.data.backup.model.MedicationDto
import com.medsreminder.data.backup.model.MedicationGroupDto
import com.medsreminder.data.backup.model.PersonDto
import com.medsreminder.data.local.AppDatabase
import com.medsreminder.data.local.entity.MedicationEntity
import com.medsreminder.data.local.entity.MedicationGroupEntity
import com.medsreminder.data.local.entity.PersonEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalTime

/**
 * Handles JSON serialization and restoration of local database content using Storage Access Framework (SAF) URIs.
 */
class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val alarmScheduler: AndroidAlarmScheduler
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Serializes all persons, medications, and groups to a versioned JSON document at the specified SAF URI.
     */
    suspend fun exportBackup(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val personDao = database.personDao()
            val medicationDao = database.medicationDao()
            val groupDao = database.medicationGroupDao()

            val persons = personDao.getAllPersons().first().map {
                PersonDto(id = it.id, name = it.name, colorHex = it.colorHex)
            }

            val medications = medicationDao.getAllMedications().first().map {
                MedicationDto(
                    id = it.id,
                    name = it.name,
                    dosage = it.dosage,
                    description = it.description
                )
            }

            val groups = groupDao.getAllGroupsWithMedications().first().map { item ->
                MedicationGroupDto(
                    id = item.group.id,
                    personId = item.group.personId,
                    name = item.group.name,
                    scheduledTime = item.group.scheduledTime.toString(),
                    ringtoneUriString = item.group.ringtoneUriString,
                    isActive = item.group.isActive,
                    daysOfWeekMask = item.group.daysOfWeekMask,
                    advanceNoticeMinutes = item.group.advanceNoticeMinutes,
                    medicationIds = item.medications.map { it.id }
                )
            }

            val envelope = BackupEnvelope(
                exportTimestampEpochMs = System.currentTimeMillis(),
                payload = BackupPayload(
                    persons = persons,
                    medications = medications,
                    medicationGroups = groups
                )
            )

            val jsonContent = json.encodeToString(envelope)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonContent.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            } ?: error("Failed to open output stream for given URI.")
        }
    }

    /**
     * Parses and atomically imports persons, medications, and groups from a JSON document at the specified SAF URI.
     */
    suspend fun importBackup(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val jsonContent = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).readText()
            } ?: error("Failed to open input stream for given URI.")

            val envelope = json.decodeFromString<BackupEnvelope>(jsonContent)
            if (envelope.schemaVersion > BackupEnvelope.CURRENT_SCHEMA_VERSION) {
                error("Unsupported backup schema version: ${envelope.schemaVersion}")
            }

            database.withTransaction {
                val personDao = database.personDao()
                val medicationDao = database.medicationDao()
                val groupDao = database.medicationGroupDao()

                // Insert Persons
                for (pDto in envelope.payload.persons) {
                    personDao.insertPerson(
                        PersonEntity(
                            id = pDto.id,
                            name = pDto.name,
                            colorHex = pDto.colorHex
                        )
                    )
                }

                // Insert Medications
                for (mDto in envelope.payload.medications) {
                    medicationDao.insertMedication(
                        MedicationEntity(
                            id = mDto.id,
                            name = mDto.name,
                            dosage = mDto.dosage,
                            description = mDto.description
                        )
                    )
                }

                // Insert Groups and cross references
                for (gDto in envelope.payload.medicationGroups) {
                    groupDao.saveGroupWithMedicationIds(
                        group = MedicationGroupEntity(
                            id = gDto.id,
                            personId = gDto.personId,
                            name = gDto.name,
                            scheduledTime = LocalTime.parse(gDto.scheduledTime),
                            ringtoneUriString = gDto.ringtoneUriString,
                            isActive = gDto.isActive,
                            daysOfWeekMask = gDto.daysOfWeekMask,
                            advanceNoticeMinutes = gDto.advanceNoticeMinutes
                        ),
                        medicationIds = gDto.medicationIds
                    )
                }
            }

            // Reschedule all active alarms in system
            alarmScheduler.rescheduleAllActive()
        }
    }
}
