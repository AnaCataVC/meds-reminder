package com.medsreminder.data.backup

import com.medsreminder.data.backup.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import com.medsreminder.data.local.entity.MedicationGroupEntity
import java.time.LocalDate
import java.time.LocalTime

class BackupSerializationTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `encode and decode BackupEnvelope preserves integrity`() {
        val originalEnvelope = BackupEnvelope(
            schemaVersion = 1,
            exportTimestampEpochMs = 1755450000000L,
            payload = BackupPayload(
                persons = listOf(
                    PersonDto(id = 1L, name = "Ana", colorHex = "#4CAF50"),
                    PersonDto(id = 2L, name = "Carlos", colorHex = "#2196F3")
                ),
                medications = listOf(
                    MedicationDto(id = 101L, name = "Paracetamol", dosage = "500mg", description = "For fever"),
                    MedicationDto(id = 102L, name = "Ibuprofeno", dosage = "400mg", description = null)
                ),
                medicationGroups = listOf(
                    MedicationGroupDto(
                        id = 501L,
                        personId = 1L,
                        name = "Dosis Mañana",
                        scheduledTime = "08:30",
                        ringtoneUriString = "content://media/internal/audio/1",
                        isActive = true,
                        daysOfWeekMask = 127,
                        medicationIds = listOf(101L, 102L)
                    )
                )
            )
        )

        val jsonString = json.encodeToString(originalEnvelope)
        assertNotNull(jsonString)

        val decodedEnvelope = json.decodeFromString<BackupEnvelope>(jsonString)
        assertEquals(1, decodedEnvelope.schemaVersion)
        assertEquals(2, decodedEnvelope.payload.persons.size)
        assertEquals("Ana", decodedEnvelope.payload.persons[0].name)
        assertEquals(2, decodedEnvelope.payload.medications.size)
        assertEquals(1, decodedEnvelope.payload.medicationGroups.size)
        assertEquals(listOf(101L, 102L), decodedEnvelope.payload.medicationGroups[0].medicationIds)
    }

    @Test
    fun `decode backup without new person fields still succeeds`() {
        val legacyJson = """
            {"schema_version":1,"export_timestamp_epoch_ms":1,
             "payload":{"persons":[{"id":1,"name":"Ana","color_hex":"#FF0000"}],
                        "medications":[],"medication_groups":[]}}
        """.trimIndent()

        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<BackupEnvelope>(legacyJson)

        assertEquals(null, decoded.payload.persons.single().ringtoneUriString)
        assertEquals(null, decoded.payload.persons.single().suspendedUntilEpochMs)
    }

    @Test
    fun `rest cycle survives a backup round trip`() {
        val group = MedicationGroupEntity(
            id = 7, personId = 1, name = "Anticonceptivo", scheduledTime = LocalTime.of(21, 0),
            cycleActiveDays = 21, cycleRestDays = 7, cycleStartDate = LocalDate.of(2026, 9, 1)
        )

        val encoded = json.encodeToString(group.toBackupDto(emptyList()))
        val restored = json.decodeFromString<MedicationGroupDto>(encoded).toEntity()

        assertEquals(group, restored)
    }

    @Test
    fun `decode backup without cycle fields restores a group without cycle`() {
        val legacyGroup = """{"id":1,"person_id":1,"name":"X","scheduled_time":"08:00","medication_ids":[]}"""

        val restored = Json { ignoreUnknownKeys = true }.decodeFromString<MedicationGroupDto>(legacyGroup).toEntity()

        assertEquals(0, restored.cycleActiveDays)
        assertEquals(null, restored.cycleStartDate)
    }
}
