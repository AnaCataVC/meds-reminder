package com.medsreminder.data.backup.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Root envelope for versioned JSON backup and restoration.
 */
@Serializable
data class BackupEnvelope(
    @SerialName("schema_version")
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    @SerialName("export_timestamp_epoch_ms")
    val exportTimestampEpochMs: Long,
    val payload: BackupPayload
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class BackupPayload(
    val persons: List<PersonDto>,
    val medications: List<MedicationDto>,
    @SerialName("medication_groups")
    val medicationGroups: List<MedicationGroupDto>
)

@Serializable
data class PersonDto(
    val id: Long,
    val name: String,
    @SerialName("color_hex")
    val colorHex: String
)

@Serializable
data class MedicationDto(
    val id: Long,
    val name: String,
    val dosage: String? = null,
    val description: String? = null
)

@Serializable
data class MedicationGroupDto(
    val id: Long,
    @SerialName("person_id")
    val personId: Long,
    val name: String,
    @SerialName("scheduled_time")
    val scheduledTime: String, // HH:mm format
    @SerialName("ringtone_uri")
    val ringtoneUriString: String? = null,
    @SerialName("is_active")
    val isActive: Boolean = true,
    @SerialName("days_of_week_mask")
    val daysOfWeekMask: Int = 127,
    @SerialName("medication_ids")
    val medicationIds: List<Long>
)
