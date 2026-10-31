package com.medsreminder.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medsreminder.core.alarm.AlarmSettings
import com.medsreminder.core.alarm.AndroidAlarmScheduler
import com.medsreminder.core.notification.NotificationHelper
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
    val isIndividualConfirmationEnabled: Boolean = false,
    val isFinished: Boolean = false
)

/**
 * ViewModel for AlarmActivity. Decouples asynchronous repository calls
 * from UI composables and manages structured lifecycle-aware coroutines.
 */
class AlarmViewModel(
    private val groupDao: MedicationGroupDao,
    private val personDao: PersonDao,
    private val scheduleRepository: MedicationScheduleRepository,
    private val notificationHelper: NotificationHelper,
    private val alarmSettings: AlarmSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlarmUiState())
    val uiState: StateFlow<AlarmUiState> = _uiState.asStateFlow()

    private var currentGroupId: Long = -1L
    private var isResolving = false

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
                    isIndividualConfirmationEnabled = alarmSettings.isIndividualMedicationConfirmationEnabled,
                    isFinished = group == null
                )
            }
        }
    }

    fun confirmTaken() = resolve { groupId ->
        scheduleRepository.confirmIntake(groupId, currentDoseDate())
    }

    fun confirmPartial(takenMedicationIds: List<Long>, skippedMedicationIds: List<Long>) = resolve { groupId ->
        scheduleRepository.confirmPartialIntake(
            groupId = groupId,
            date = currentDoseDate(),
            takenMedicationIds = takenMedicationIds,
            skippedMedicationIds = skippedMedicationIds
        )
    }

    fun snooze(minutes: Long) = resolve { groupId ->
        scheduleRepository.snoozeSchedule(groupId, System.currentTimeMillis() + (minutes * 60 * 1000L))
    }

    fun snooze10Min() = snooze(10L)

    fun cancelToday() = resolve { groupId ->
        scheduleRepository.skipSchedule(groupId, currentDoseDate())
    }

    private fun currentDoseDate(): LocalDate =
        _uiState.value.groupWithMeds?.group?.let { AndroidAlarmScheduler.currentDoseDate(it) } ?: LocalDate.now()

    // Ignores further taps once one action is in flight, so a quick double tap can't run two transitions.
    private fun resolve(action: suspend (groupId: Long) -> Unit) {
        val groupId = currentGroupId
        if (groupId == -1L || isResolving) return
        isResolving = true

        // Cut ringing audio and dismiss ongoing notifications synchronously on main thread
        notificationHelper.cancelAllForGroup(groupId)

        viewModelScope.launch {
            try {
                action(groupId)
                _uiState.update { it.copy(isFinished = true) }
            } finally {
                isResolving = false
            }
        }
    }
}
