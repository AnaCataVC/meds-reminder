package com.medsreminder.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.medsreminder.data.local.entity.MedicationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {

    @Query("SELECT * FROM medications ORDER BY name ASC")
    fun getAllMedications(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getMedicationById(id: Long): MedicationEntity?

    @Query("SELECT * FROM medications WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchMedications(query: String): Flow<List<MedicationEntity>>

    // Upsert, not REPLACE: REPLACE deletes the row first, which CASCADE-deletes its group cross-refs.
    @Upsert
    suspend fun upsertMedication(medication: MedicationEntity): Long

    @Update
    suspend fun updateMedication(medication: MedicationEntity)

    @Delete
    suspend fun deleteMedication(medication: MedicationEntity)
}
