package com.medsreminder.ui.main

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medsreminder.core.alarm.AndroidAlarmScheduler
import com.medsreminder.core.notification.NotificationHelper
import com.medsreminder.data.backup.BackupManager
import com.medsreminder.data.local.dao.MedicationDao
import com.medsreminder.data.local.dao.MedicationGroupDao
import com.medsreminder.data.local.dao.PersonDao
import com.medsreminder.data.local.entity.MedicationEntity
import com.medsreminder.data.local.entity.MedicationGroupEntity
import com.medsreminder.data.local.entity.PersonEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val context: Context,
    private val personDao: PersonDao,
    private val medicationDao: MedicationDao,
    private val groupDao: MedicationGroupDao,
    private val alarmScheduler: AndroidAlarmScheduler,
    private val notificationHelper: NotificationHelper,
    private val backupManager: BackupManager
) : ViewModel() {

    private val _selectedPersonId = MutableStateFlow<Long?>(null)
    private val _medicationSearchQuery = MutableStateFlow("")
    private val _userMessage = MutableStateFlow<String?>(null)

    private val _sideEffects = Channel<MainSideEffect>(Channel.BUFFERED)
    val sideEffects = _sideEffects.receiveAsFlow()

    private val _medicationsFlow = _medicationSearchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            medicationDao.getAllMedications()
        } else {
            medicationDao.searchMedications(query)
        }
    }

    val uiState: StateFlow<MainUiState> = combine(
        personDao.getAllPersons(),
        _selectedPersonId,
        _medicationsFlow,
        _medicationSearchQuery,
        _userMessage
    ) { persons, selectedId, meds, search, message ->
        Tuple5(persons, selectedId, meds, search, message)
    }.flatMapLatest { (persons, selectedId, meds, search, message) ->
        val groupsFlow = if (selectedId == null) {
            groupDao.getAllGroupsWithMedications()
        } else {
            groupDao.getGroupsForPerson(selectedId)
        }

        groupsFlow.map { groups ->
            MainUiState(
                isLoading = false,
                persons = persons,
                selectedPersonId = selectedId,
                groupsWithMedications = groups,
                catalogMedications = meds,
                medicationSearchQuery = search,
                hasExactAlarmPermission = checkExactAlarmPermission(),
                userMessage = message
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState(isLoading = true)
    )

    fun onIntent(intent: MainUiIntent) {
        when (intent) {
            is MainUiIntent.SelectPerson -> _selectedPersonId.value = intent.personId
            is MainUiIntent.SavePerson -> savePerson(intent.id, intent.name, intent.colorHex)
            is MainUiIntent.DeletePerson -> deletePerson(intent.person)
            is MainUiIntent.SuspendPerson -> suspendPerson(intent.personId, intent.hours, intent.untilEndOfDay)
            is MainUiIntent.ResumePerson -> resumePerson(intent.personId)

            is MainUiIntent.SaveMedication -> saveMedication(
                intent.id,
                intent.name,
                intent.dosage,
                intent.description
            )
            is MainUiIntent.DeleteMedication -> deleteMedication(intent.medication)
            is MainUiIntent.SearchMedications -> _medicationSearchQuery.value = intent.query

            is MainUiIntent.ToggleGroupActive -> toggleGroupActive(intent.groupId, intent.isActive)
            is MainUiIntent.ConfirmIntakeToday -> confirmIntakeToday(intent.groupId)
            is MainUiIntent.SnoozeGroup -> snoozeGroup(intent.groupId, intent.minutes)
            is MainUiIntent.SaveGroup -> saveGroup(intent)
            is MainUiIntent.DeleteGroup -> deleteGroup(intent.groupId)
            is MainUiIntent.TestAlarm -> testAlarmNow(intent.groupId)

            is MainUiIntent.ExportBackup -> exportBackup(intent.uri)
            is MainUiIntent.ImportBackup -> importBackup(intent.uri)
            is MainUiIntent.RefreshPermissions -> checkExactAlarmPermission()
            is MainUiIntent.ClearMessage -> _userMessage.value = null
        }
    }

    private fun suspendPerson(personId: Long, hours: Int?, untilEndOfDay: Boolean) {
        viewModelScope.launch {
            val untilEpochMs = if (untilEndOfDay) {
                java.time.LocalDate.now().atTime(java.time.LocalTime.MAX)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            } else if (hours != null) {
                System.currentTimeMillis() + (hours * 3600 * 1000L)
            } else {
                null
            }

            personDao.setSuspendedUntil(personId, untilEpochMs)
            val durationLabel = if (untilEndOfDay) "el resto del día" else "${hours}h"
            _sideEffects.send(MainSideEffect.ShowSnackbar("Alarmas pausadas por $durationLabel"))
        }
    }

    private fun resumePerson(personId: Long) {
        viewModelScope.launch {
            personDao.setSuspendedUntil(personId, null)
            _sideEffects.send(MainSideEffect.ShowSnackbar("Alarmas reanudadas"))
        }
    }

    private fun savePerson(id: Long, name: String, colorHex: String) {
        viewModelScope.launch {
            if (name.isBlank()) {
                _sideEffects.send(MainSideEffect.ShowSnackbar("El nombre no puede estar vacío"))
                return@launch
            }
            personDao.insertPerson(PersonEntity(id = id, name = name.trim(), colorHex = colorHex))
            _sideEffects.send(MainSideEffect.ShowSnackbar("Perfil guardado: $name"))
        }
    }

    private fun deletePerson(person: PersonEntity) {
        viewModelScope.launch {
            personDao.deletePerson(person)
            if (_selectedPersonId.value == person.id) {
                _selectedPersonId.value = null
            }
            // Reschedule remaining alarms
            alarmScheduler.rescheduleAllActive()
            _sideEffects.send(MainSideEffect.ShowSnackbar("Perfil eliminado: ${person.name}"))
        }
    }

    private fun saveMedication(id: Long, name: String, dosage: String?, description: String?) {
        viewModelScope.launch {
            if (name.isBlank()) {
                _sideEffects.send(MainSideEffect.ShowSnackbar("El nombre del medicamento es requerido"))
                return@launch
            }
            medicationDao.insertMedication(
                MedicationEntity(
                    id = id,
                    name = name.trim(),
                    dosage = dosage?.trim()?.takeIf { it.isNotEmpty() },
                    description = description?.trim()?.takeIf { it.isNotEmpty() }
                )
            )
            _sideEffects.send(MainSideEffect.ShowSnackbar("Medicamento guardado: $name"))
        }
    }

    private fun deleteMedication(medication: MedicationEntity) {
        viewModelScope.launch {
            medicationDao.deleteMedication(medication)
            _sideEffects.send(MainSideEffect.ShowSnackbar("Medicamento eliminado: ${medication.name}"))
        }
    }

    private fun saveGroup(intent: MainUiIntent.SaveGroup) {
        viewModelScope.launch {
            if (intent.name.isBlank()) {
                _sideEffects.send(MainSideEffect.ShowSnackbar("Por favor ingresa un nombre para el horario"))
                return@launch
            }
            if (intent.personId == 0L) {
                _sideEffects.send(MainSideEffect.ShowSnackbar("Debes asignar este horario a una persona"))
                return@launch
            }

            val group = MedicationGroupEntity(
                id = intent.groupId,
                personId = intent.personId,
                name = intent.name.trim(),
                scheduledTime = intent.scheduledTime,
                ringtoneUriString = intent.ringtoneUriString,
                daysOfWeekMask = intent.daysOfWeekMask,
                isActive = true,
                advanceNoticeMinutes = intent.advanceNoticeMinutes
            )

            val savedId = groupDao.saveGroupWithMedicationIds(group, intent.medicationIds)
            val updatedGroup = group.copy(id = savedId)
            alarmScheduler.schedule(updatedGroup)

            _sideEffects.send(MainSideEffect.ShowSnackbar("Horario programado: ${group.name}"))
            _sideEffects.send(MainSideEffect.NavigateBack)
        }
    }

    private fun deleteGroup(groupId: Long) {
        viewModelScope.launch {
            val groupWithMeds = groupDao.getGroupById(groupId) ?: return@launch
            alarmScheduler.cancel(groupWithMeds.group)
            groupDao.deleteGroup(groupWithMeds.group)
            _sideEffects.send(MainSideEffect.ShowSnackbar("Horario eliminado"))
            _sideEffects.send(MainSideEffect.NavigateBack)
        }
    }

    private fun toggleGroupActive(groupId: Long, isActive: Boolean) {
        viewModelScope.launch {
            val groupWithMeds = groupDao.getGroupById(groupId) ?: return@launch
            val updated = groupWithMeds.group.copy(isActive = isActive)
            groupDao.updateGroup(updated)
            if (isActive) {
                alarmScheduler.schedule(updated)
                _sideEffects.send(MainSideEffect.ShowSnackbar("Alarma activada"))
            } else {
                alarmScheduler.cancel(updated)
                _sideEffects.send(MainSideEffect.ShowSnackbar("Alarma pausada"))
            }
        }
    }

    private fun confirmIntakeToday(groupId: Long) {
        viewModelScope.launch {
            val today = LocalDate.now()
            groupDao.markGroupAsTaken(groupId, today)
            _sideEffects.send(MainSideEffect.ShowSnackbar("¡Toma confirmada para hoy!"))
        }
    }

    private fun snoozeGroup(groupId: Long, minutes: Int) {
        viewModelScope.launch {
            val triggerEpoch = System.currentTimeMillis() + (minutes * 60 * 1000L)
            groupDao.setSnoozeTime(groupId, triggerEpoch)
            alarmScheduler.scheduleSnooze(groupId, triggerEpoch)
            _sideEffects.send(MainSideEffect.ShowSnackbar("Pospuesto por $minutes minutos"))
        }
    }

    private fun testAlarmNow(groupId: Long) {
        viewModelScope.launch {
            val groupWithMeds = groupDao.getGroupById(groupId)
            if (groupWithMeds != null) {
                val person = personDao.getAllPersons().first().find { it.id == groupWithMeds.group.personId }
                notificationHelper.showMedicationNotification(
                    groupWithMeds = groupWithMeds,
                    personName = person?.name ?: "Usuario"
                )
                _sideEffects.send(MainSideEffect.ShowSnackbar("🔔 Alarma de prueba activada"))
            }
        }
    }

    private fun exportBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            backupManager.exportBackup(uri)
                .onSuccess {
                    _sideEffects.send(MainSideEffect.ShowSnackbar("✅ Respaldo exportado exitosamente"))
                }
                .onFailure {
                    _sideEffects.send(MainSideEffect.ShowSnackbar("❌ Error al exportar respaldo: ${it.message}"))
                }
        }
    }

    private fun importBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            backupManager.importBackup(uri)
                .onSuccess {
                    _sideEffects.send(MainSideEffect.ShowSnackbar("✅ Respaldo restaurado exitosamente"))
                }
                .onFailure {
                    _sideEffects.send(MainSideEffect.ShowSnackbar("❌ Error al restaurar respaldo: ${it.message}"))
                }
        }
    }

    fun checkExactAlarmPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
}

private data class Tuple5<T1, T2, T3, T4, T5>(
    val a: T1,
    val b: T2,
    val c: T3,
    val d: T4,
    val e: T5
)
