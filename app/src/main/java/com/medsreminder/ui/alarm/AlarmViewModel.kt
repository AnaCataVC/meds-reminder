package com.medsreminder.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medsreminder.data.local.dao.MedicationGroupDao
import com.medsreminder.data.local.dao.PersonDao
import com.medsreminder.data.local.entity.MedicationGroupWithMedications
import com.medsreminder.data.local.entity.PersonEntity
import com.medsreminder.domain.repository.MedicationScheduleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AlarmUiState(
    val isLoading: Boolean = true,
    val groupWithMeds: MedicationGroupWithMedications? = null,
    val person: PersonEntity? = null,
    val isFinished: Boolean = false
)

/**
 * ViewModel for AlarmActivity. Decouples asynchronous repository calls
 * from UI composables and manages structured lifecycle-aware coroutines.
 */
class AlarmViewModel(
    private val groupDao: MedicationGroupDao,
    private val personDao: PersonDao,
    private val scheduleRepository: MedicationScheduleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlarmUiState())
    val uiState: StateFlow<AlarmUiState> = _uiState.asStateFlow()

    private var currentGroupId: Long = -1L

    fun loadAlarm(groupId: Long) {
        if (groupId == -1L) {
            _uiState.update { it.copy(isLoading = false, isFinished = true) }
            return
        }
        currentGroupId = groupId
        viewModelScope.launch {
            val group = groupDao.getGroupById(groupId)
            val person = group?.let { personDao.getPersonById(it.group.personId).firstOrNull() }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    groupWithMeds = group,
                    person = person,
                    isFinished = group == null
                )
            }
        }
    }

    fun confirmTaken() {
        if (currentGroupId == -1L) return
        viewModelScope.launch {
            scheduleRepository.confirmIntake(currentGroupId, LocalDate.now())
            _uiState.update { it.copy(isFinished = true) }
        }
    }

    fun snooze10Min() {
        if (currentGroupId == -1L) return
        viewModelScope.launch {
            val snoozeEpoch = System.currentTimeMillis() + (10 * 60 * 1000L)
            scheduleRepository.snoozeSchedule(currentGroupId, snoozeEpoch)
            _uiState.update { it.copy(isFinished = true) }
        }
    }

    fun cancelToday() {
        if (currentGroupId == -1L) return
        viewModelScope.launch {
            scheduleRepository.skipSchedule(currentGroupId, LocalDate.now())
            _uiState.update { it.copy(isFinished = true) }
        }
    }
}
