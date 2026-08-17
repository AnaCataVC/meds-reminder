package com.medsreminder.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * Composite relational model representing a medication group and all associated catalog medications.
 */
data class MedicationGroupWithMedications(
    @Embedded
    val group: MedicationGroupEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = MedicationGroupCrossRef::class,
            parentColumn = "group_id",
            entityColumn = "medication_id"
        )
    )
    val medications: List<MedicationEntity>
)
