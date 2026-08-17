package com.medsreminder.ui.main

import com.medsreminder.data.local.entity.MedicationEntity
import com.medsreminder.data.local.entity.MedicationGroupWithMedications
import com.medsreminder.data.local.entity.PersonEntity
import java.time.LocalTime

/**
 * Main application UI state holding observable data across all navigation tabs.
 */
data class MainUiState(
    val isLoading: Boolean = false,
    val persons: List<PersonEntity> = emptyList(),
    val selectedPersonId: Long? = null, // null = View all persons
    val groupsWithMedications: List<MedicationGroupWithMedications> = emptyList(),
    val catalogMedications: List<MedicationEntity> = emptyList(),
    val medicationSearchQuery: String = "",
    val hasExactAlarmPermission: Boolean = true,
    val hasNotificationPermission: Boolean = true,
    val userMessage: String? = null
)

/**
 * User intents / actions processed by the ViewModel.
 */
sealed interface MainUiIntent {
    // Person Actions
    data class SelectPerson(val personId: Long?) : MainUiIntent
    data class SavePerson(val id: Long = 0, val name: String, val colorHex: String) : MainUiIntent
    data class DeletePerson(val person: PersonEntity) : MainUiIntent

    // Medication Actions
    data class SaveMedication(
        val id: Long = 0,
        val name: String,
        val dosage: String?,
        val description: String?
    ) : MainUiIntent
    data class DeleteMedication(val medication: MedicationEntity) : MainUiIntent
    data class SearchMedications(val query: String) : MainUiIntent

    // Group & Schedule Actions
    data class ToggleGroupActive(val groupId: Long, val isActive: Boolean) : MainUiIntent
    data class ConfirmIntakeToday(val groupId: Long) : MainUiIntent
    data class SnoozeGroup(val groupId: Long, val minutes: Int) : MainUiIntent
    data class SaveGroup(
        val groupId: Long = 0,
        val personId: Long,
        val name: String,
        val scheduledTime: LocalTime,
        val ringtoneUriString: String?,
        val daysOfWeekMask: Int,
        val medicationIds: List<Long>
    ) : MainUiIntent
    data class DeleteGroup(val groupId: Long) : MainUiIntent
    data class TestAlarm(val groupId: Long) : MainUiIntent

    // Backup & Diagnostic Actions
    data class ExportBackup(val uri: android.net.Uri) : MainUiIntent
    data class ImportBackup(val uri: android.net.Uri) : MainUiIntent
    data object RefreshPermissions : MainUiIntent
    data object ClearMessage : MainUiIntent
}

/**
 * One-shot side effects emitted to the UI (e.g. snackbars, toasts).
 */
sealed interface MainSideEffect {
    data class ShowSnackbar(val message: String) : MainSideEffect
    data object RequestExactAlarmPermission : MainSideEffect
    data object NavigateBack : MainSideEffect
}
