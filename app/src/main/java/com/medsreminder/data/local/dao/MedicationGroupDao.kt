package com.medsreminder.data.local.dao

import androidx.room.*
import com.medsreminder.data.local.entity.MedicationGroupCrossRef
import com.medsreminder.data.local.entity.MedicationGroupEntity
import com.medsreminder.data.local.entity.MedicationGroupWithMedications
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface MedicationGroupDao {

    @Transaction
    @Query("SELECT * FROM medication_groups ORDER BY scheduled_time ASC")
    fun getAllGroupsWithMedications(): Flow<List<MedicationGroupWithMedications>>

    @Transaction
    @Query("SELECT * FROM medication_groups WHERE person_id = :personId ORDER BY scheduled_time ASC")
    fun getGroupsForPerson(personId: Long): Flow<List<MedicationGroupWithMedications>>

    @Transaction
    @Query("SELECT * FROM medication_groups WHERE id = :groupId")
    suspend fun getGroupById(groupId: Long): MedicationGroupWithMedications?

    @Transaction
    @Query("SELECT * FROM medication_groups WHERE is_active = 1")
    suspend fun getAllActiveGroupsSync(): List<MedicationGroupWithMedications>

    @Upsert
    suspend fun upsertGroup(group: MedicationGroupEntity): Long

    @Update
    suspend fun updateGroup(group: MedicationGroupEntity)

    @Delete
    suspend fun deleteGroup(group: MedicationGroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(crossRefs: List<MedicationGroupCrossRef>)

    @Query("DELETE FROM medication_group_cross_ref WHERE group_id = :groupId")
    suspend fun deleteCrossRefsForGroup(groupId: Long)

    @Query("UPDATE medication_groups SET last_taken_date = :date, last_skipped_date = NULL, snooze_until_epoch_ms = NULL WHERE id = :groupId")
    suspend fun markGroupAsTaken(groupId: Long, date: LocalDate)

    @Query("UPDATE medication_groups SET snooze_until_epoch_ms = :snoozeEpoch WHERE id = :groupId")
    suspend fun setSnoozeTime(groupId: Long, snoozeEpoch: Long?)

    @Query("UPDATE medication_groups SET last_skipped_date = :date, last_taken_date = NULL, snooze_until_epoch_ms = NULL WHERE id = :groupId")
    suspend fun markGroupSkippedToday(groupId: Long, date: LocalDate)

    @Query("UPDATE medication_groups SET last_taken_date = NULL, last_skipped_date = NULL, snooze_until_epoch_ms = NULL WHERE id = :groupId")
    suspend fun clearGroupResolutionToday(groupId: Long)

    @Query("UPDATE medication_group_cross_ref SET last_taken_date = :date, last_skipped_date = NULL WHERE group_id = :groupId AND medication_id = :medicationId")
    suspend fun markCrossRefTaken(groupId: Long, medicationId: Long, date: LocalDate)

    @Query("UPDATE medication_group_cross_ref SET last_skipped_date = :date, last_taken_date = NULL WHERE group_id = :groupId AND medication_id = :medicationId")
    suspend fun markCrossRefSkipped(groupId: Long, medicationId: Long, date: LocalDate)

    @Query("UPDATE medication_group_cross_ref SET last_taken_date = :date, last_skipped_date = NULL WHERE group_id = :groupId")
    suspend fun markAllCrossRefsTaken(groupId: Long, date: LocalDate)

    @Query("UPDATE medication_group_cross_ref SET last_skipped_date = :date, last_taken_date = NULL WHERE group_id = :groupId")
    suspend fun markAllCrossRefsSkipped(groupId: Long, date: LocalDate)

    @Query("UPDATE medication_group_cross_ref SET last_taken_date = NULL, last_skipped_date = NULL WHERE group_id = :groupId")
    suspend fun clearCrossRefResolutions(groupId: Long)

    @Transaction
    suspend fun recordPartialIntake(
        groupId: Long,
        date: LocalDate,
        takenMedicationIds: List<Long>,
        skippedMedicationIds: List<Long>
    ) {
        markGroupAsTaken(groupId, date)
        for (medId in takenMedicationIds) {
            markCrossRefTaken(groupId, medId, date)
        }
        for (medId in skippedMedicationIds) {
            markCrossRefSkipped(groupId, medId, date)
        }
    }

    /**
     * Atomically saves or updates a medication group and updates its many-to-many cross references.
     */
    @Transaction
    suspend fun saveGroupWithMedicationIds(
        group: MedicationGroupEntity,
        medicationIds: List<Long>
    ): Long {
        // Upsert keeps restored ids (backup import) and never deletes the row, so cross-refs survive.
        val upsertedId = upsertGroup(group)
        val groupId = if (group.id == 0L) upsertedId else group.id

        deleteCrossRefsForGroup(groupId)
        if (medicationIds.isNotEmpty()) {
            val crossRefs = medicationIds.map { medId ->
                MedicationGroupCrossRef(groupId = groupId, medicationId = medId)
            }
            insertCrossRefs(crossRefs)
        }
        return groupId
    }
}
