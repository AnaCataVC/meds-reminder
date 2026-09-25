package com.medsreminder.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.medsreminder.data.local.entity.PersonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {

    @Query("SELECT * FROM persons ORDER BY name ASC")
    fun getAllPersons(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons WHERE id = :id")
    fun getPersonById(id: Long): Flow<PersonEntity?>

    @Query("UPDATE persons SET suspended_until_epoch_ms = :suspendedUntilEpochMs WHERE id = :personId")
    suspend fun setSuspendedUntil(personId: Long, suspendedUntilEpochMs: Long?)

    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun getPersonByIdSync(id: Long): PersonEntity?

    // Upsert, not REPLACE: REPLACE deletes the row first, which CASCADE-deletes the person's groups.
    @Upsert
    suspend fun upsertPerson(person: PersonEntity): Long

    @Update
    suspend fun updatePerson(person: PersonEntity)

    @Delete
    suspend fun deletePerson(person: PersonEntity)
}
