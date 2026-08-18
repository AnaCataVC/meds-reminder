package com.medsreminder.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medsreminder.data.local.entity.MedicationEntity
import com.medsreminder.data.local.entity.PersonEntity
import com.medsreminder.ui.dialogs.AddEditMedicationSheet
import com.medsreminder.ui.dialogs.AddEditPersonSheet
import com.medsreminder.ui.main.MainUiIntent
import com.medsreminder.ui.main.MainUiState
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditGroupScreen(
    groupId: Long?,
    state: MainUiState,
    onIntent: (MainUiIntent) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val existingGroup = remember(groupId, state.groupsWithMedications) {
        groupId?.let { id -> state.groupsWithMedications.find { it.group.id == id } }
    }

    var name by remember(existingGroup) {
        mutableStateOf(existingGroup?.group?.name ?: "")
    }
    var selectedPersonId by remember(existingGroup, state.persons) {
        mutableStateOf(
            existingGroup?.group?.personId
                ?: state.selectedPersonId
                ?: state.persons.firstOrNull()?.id
                ?: 0L
        )
    }
    var scheduledTime by remember(existingGroup) {
        mutableStateOf(existingGroup?.group?.scheduledTime ?: LocalTime.of(8, 0))
    }
    var ringtoneUriString by remember(existingGroup) {
        mutableStateOf(existingGroup?.group?.ringtoneUriString)
    }
    var ringtoneTitle by remember(ringtoneUriString) {
        mutableStateOf(
            if (ringtoneUriString.isNullOrBlank()) "Tono por defecto del sistema"
            else {
                runCatching {
                    val ringtone = RingtoneManager.getRingtone(context, Uri.parse(ringtoneUriString))
                    ringtone?.getTitle(context) ?: "Tono personalizado"
                }.getOrDefault("Tono personalizado")
            }
        )
    }
    var daysOfWeekMask by remember(existingGroup) {
        mutableStateOf(existingGroup?.group?.daysOfWeekMask ?: 127)
    }
    var selectedMedicationIds by remember(existingGroup) {
        mutableStateOf(existingGroup?.medications?.map { it.id }?.toSet() ?: emptySet())
    }

    var showTimePicker by remember { mutableStateOf(false) }
    var showInlinePersonSheet by remember { mutableStateOf(false) }
    var showInlineMedSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Auto-select person if none selected yet and persons become available
    LaunchedEffect(state.persons) {
        if (selectedPersonId == 0L && state.persons.isNotEmpty()) {
            selectedPersonId = state.selectedPersonId ?: state.persons.first().id
        }
    }

    // Ringtone Picker Intent Launcher
    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            ringtoneUriString = uri?.toString()
            ringtoneTitle = if (uri != null) {
                runCatching {
                    RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: "Tono seleccionado"
                }.getOrDefault("Tono seleccionado")
            } else {
                "Silencio / Sin sonido"
            }
        }
    }

    if (showInlinePersonSheet) {
        AddEditPersonSheet(
            onDismiss = { showInlinePersonSheet = false },
            onSave = { _, pName, colorHex ->
                onIntent(MainUiIntent.SavePerson(name = pName, colorHex = colorHex))
            }
        )
    }

    if (showInlineMedSheet) {
        AddEditMedicationSheet(
            onDismiss = { showInlineMedSheet = false },
            onSave = { _, mName, dosage, desc ->
                onIntent(MainUiIntent.SaveMedication(name = mName, dosage = dosage, description = desc))
            }
        )
    }

    if (showDeleteConfirm && groupId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar Horario") },
            text = { Text("¿Deseas eliminar este recordatorio y cancelar su alarma?") },
            confirmButton = {
                Button(
                    onClick = {
                        onIntent(MainUiIntent.DeleteGroup(groupId))
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = scheduledTime.hour,
            initialMinute = scheduledTime.minute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Selecciona la hora de la alarma") },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scheduledTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (groupId == null) "Nuevo Horario" else "Editar Horario",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (groupId != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Eliminar Horario",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Selector de Persona
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "1. Persona asignada *",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { showInlinePersonSheet = true }) {
                        Text("+ Nueva Persona")
                    }
                }

                if (state.persons.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "No tienes perfiles creados todavía. Crea un perfil para asignar este horario.",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                onClick = { showInlinePersonSheet = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Crear Primera Persona", fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.persons.forEach { person ->
                            val isSelected = selectedPersonId == person.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedPersonId = person.id },
                                label = { Text(person.name) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(
                                                runCatching {
                                                    Color(android.graphics.Color.parseColor(person.colorHex))
                                                }.getOrDefault(Color.Gray)
                                            )
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // 2. Nombre del Horario
            Column {
                Text(
                    text = "2. Nombre del Recordatorio *",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("ej. Dosis de la Mañana, Antes de Dormir") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    supportingText = {
                        if (name.isBlank()) {
                            Text("Requerido para identificar el recordatorio", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                )
            }

            // 3. Hora Exacta
            Column {
                Text(
                    text = "3. Hora programada *",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimePicker = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = scheduledTime.format(DateTimeFormatter.ofPattern("hh:mm a")),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(onClick = { showTimePicker = true }) {
                            Text("Cambiar Hora")
                        }
                    }
                }
            }

            // 4. Selección de Medicamentos
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "4. Medicamentos incluidos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { showInlineMedSheet = true }) {
                        Text("+ Nuevo Fármaco")
                    }
                }

                if (state.catalogMedications.isEmpty()) {
                    Text(
                        text = "No hay fármacos en el catálogo. Puedes agregar uno con '+ Nuevo Fármaco'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.catalogMedications.forEach { med ->
                            val isSelected = selectedMedicationIds.contains(med.id)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedMedicationIds = if (isSelected) {
                                        selectedMedicationIds - med.id
                                    } else {
                                        selectedMedicationIds + med.id
                                    }
                                },
                                label = {
                                    Text("${med.name}${med.dosage?.let { " ($it)" } ?: ""}")
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Medication,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // 5. Tono de Alarma
            Column {
                Text(
                    text = "5. Sonido de Alarma",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                    ringtoneUriString?.let { Uri.parse(it) }
                                )
                            }
                            ringtonePickerLauncher.launch(intent)
                        },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = ringtoneTitle,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Toca para elegir desde la biblioteca del dispositivo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // 6. Días de la Semana
            Column {
                Text(
                    text = "6. Días de repetición",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                val days = listOf(
                    Triple("L", DayOfWeek.MONDAY, 1),
                    Triple("M", DayOfWeek.TUESDAY, 2),
                    Triple("M", DayOfWeek.WEDNESDAY, 4),
                    Triple("J", DayOfWeek.THURSDAY, 8),
                    Triple("V", DayOfWeek.FRIDAY, 16),
                    Triple("S", DayOfWeek.SATURDAY, 32),
                    Triple("D", DayOfWeek.SUNDAY, 64)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    days.forEach { (label, _, bit) ->
                        val isEnabled = (daysOfWeekMask and bit) != 0
                        Surface(
                            shape = CircleShape,
                            color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(40.dp)
                                .clickable {
                                    daysOfWeekMask = if (isEnabled) {
                                        daysOfWeekMask and bit.inv()
                                    } else {
                                        daysOfWeekMask or bit
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    color = if (isEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Save Button
            val isNameMissing = name.isBlank()
            val isPersonMissing = selectedPersonId == 0L
            val canSave = !isNameMissing && !isPersonMissing

            Button(
                onClick = {
                    onIntent(
                        MainUiIntent.SaveGroup(
                            groupId = groupId ?: 0L,
                            personId = selectedPersonId,
                            name = name,
                            scheduledTime = scheduledTime,
                            ringtoneUriString = ringtoneUriString,
                            daysOfWeekMask = if (daysOfWeekMask == 0) 127 else daysOfWeekMask,
                            medicationIds = selectedMedicationIds.toList()
                        )
                    )
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (groupId == null) "Guardar y Activar Alarma" else "Actualizar Horario",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            if (!canSave) {
                val missingList = mutableListOf<String>()
                if (isPersonMissing) missingList.add("seleccionar una persona (paso 1)")
                if (isNameMissing) missingList.add("escribir el nombre del recordatorio (paso 2)")

                Text(
                    text = "⚠️ Para guardar debes: ${missingList.joinToString(" y ")}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
