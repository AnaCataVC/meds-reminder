package com.medsreminder.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medsreminder.data.local.entity.MedicationGroupWithMedications
import com.medsreminder.data.local.entity.PersonEntity
import com.medsreminder.ui.dialogs.AddEditPersonSheet
import com.medsreminder.ui.main.MainUiIntent
import com.medsreminder.ui.main.MainUiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HorariosScreen(
    state: MainUiState,
    onIntent: (MainUiIntent) -> Unit,
    onNavigateToAddGroup: (groupId: Long?) -> Unit,
    onRequestAlarmPermission: () -> Unit
) {
    var showAddPersonSheet by remember { mutableStateOf(false) }

    if (showAddPersonSheet) {
        AddEditPersonSheet(
            onDismiss = { showAddPersonSheet = false },
            onSave = { _, name, colorHex ->
                onIntent(MainUiIntent.SavePerson(name = name, colorHex = colorHex))
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MedicalServices,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Meds Reminder",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigateToAddGroup(null) },
                icon = { Icon(Icons.Default.AlarmAdd, contentDescription = null) },
                text = { Text("Nuevo Horario") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Permission Banner
            if (!state.hasExactAlarmPermission) {
                PermissionWarningBanner(onActionClick = onRequestAlarmPermission)
            }

            // Person Filter Chips
            PersonFilterBar(
                persons = state.persons,
                selectedPersonId = state.selectedPersonId,
                onPersonSelected = { onIntent(MainUiIntent.SelectPerson(it)) },
                onAddPersonClick = { showAddPersonSheet = true }
            )

            // Active Person Suspension Banner (if filtered person is suspended or options)
            val selectedPerson = state.persons.find { it.id == state.selectedPersonId }
            if (selectedPerson != null) {
                PersonSuspensionStatusBanner(
                    person = selectedPerson,
                    onSuspend6h = { onIntent(MainUiIntent.SuspendPerson(selectedPerson.id, hours = 6)) },
                    onSuspendToday = { onIntent(MainUiIntent.SuspendPerson(selectedPerson.id, hours = null, untilEndOfDay = true)) },
                    onResume = { onIntent(MainUiIntent.ResumePerson(selectedPerson.id)) }
                )
            }

            // Groups List
            if (state.groupsWithMedications.isEmpty() && !state.isLoading) {
                EmptyHorariosPlaceholder(
                    hasPersons = state.persons.isNotEmpty(),
                    onAddPerson = { showAddPersonSheet = true },
                    onAddGroup = { onNavigateToAddGroup(null) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = state.groupsWithMedications,
                        key = { it.group.id }
                    ) { groupWithMeds ->
                        val person = state.persons.find { it.id == groupWithMeds.group.personId }
                        HorarioReminderCard(
                            groupWithMeds = groupWithMeds,
                            person = person,
                            onToggleActive = { isActive ->
                                onIntent(MainUiIntent.ToggleGroupActive(groupWithMeds.group.id, isActive))
                            },
                            onConfirmToday = {
                                onIntent(MainUiIntent.ConfirmIntakeToday(groupWithMeds.group.id))
                            },
                            onSnooze10Min = {
                                onIntent(MainUiIntent.SnoozeGroup(groupWithMeds.group.id, 10))
                            },
                            onTestAlarm = {
                                onIntent(MainUiIntent.TestAlarm(groupWithMeds.group.id))
                            },
                            onClickEdit = {
                                onNavigateToAddGroup(groupWithMeds.group.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonFilterBar(
    persons: List<PersonEntity>,
    selectedPersonId: Long?,
    onPersonSelected: (Long?) -> Unit,
    onAddPersonClick: () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedPersonId == null,
                onClick = { onPersonSelected(null) },
                label = { Text("Todos") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }

        items(persons, key = { it.id }) { person ->
            val personColor = remember(person.colorHex) {
                runCatching { Color(AndroidColor.parseColor(person.colorHex)) }.getOrDefault(Color.Gray)
            }

            FilterChip(
                selected = selectedPersonId == person.id,
                onClick = { onPersonSelected(person.id) },
                label = { Text(person.name) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(personColor)
                    )
                }
            )
        }

        item {
            AssistChip(
                onClick = onAddPersonClick,
                label = { Text("+ Persona") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun PersonSuspensionStatusBanner(
    person: PersonEntity,
    onSuspend6h: () -> Unit,
    onSuspendToday: () -> Unit,
    onResume: () -> Unit
) {
    val isSuspended = person.suspendedUntilEpochMs?.let { it > System.currentTimeMillis() } ?: false

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSuspended) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isSuspended) Icons.Default.Snooze else Icons.Outlined.Alarm,
                    contentDescription = null,
                    tint = if (isSuspended) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (isSuspended) "Alarmas de ${person.name} pausadas" else "Pausar alarmas de ${person.name}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isSuspended) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isSuspended) "Se reactivarán automáticamente al vencer el plazo." else "Suspende todos sus recordatorios temporalmente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSuspended) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (isSuspended) {
                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Reanudar", fontSize = 12.sp)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                        onClick = onSuspend6h,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("6h", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    FilledTonalButton(
                        onClick = onSuspendToday,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Hoy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HorarioReminderCard(
    groupWithMeds: MedicationGroupWithMedications,
    person: PersonEntity?,
    onToggleActive: (Boolean) -> Unit,
    onConfirmToday: () -> Unit,
    onSnooze10Min: () -> Unit,
    onTestAlarm: () -> Unit,
    onClickEdit: () -> Unit
) {
    val group = groupWithMeds.group
    val today = remember { LocalDate.now() }
    val isTakenToday = group.lastTakenDate == today

    val personColor = remember(person?.colorHex) {
        runCatching { Color(AndroidColor.parseColor(person?.colorHex ?: "#0061A4")) }
            .getOrDefault(Color.Gray)
    }

    val timeFormatted = remember(group.scheduledTime) {
        group.scheduledTime.format(DateTimeFormatter.ofPattern("hh:mm a"))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClickEdit() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isTakenToday) 1.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Person badge and active switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(personColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = person?.name ?: "Sin asignar",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = group.isActive,
                    onCheckedChange = onToggleActive
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Time and Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (group.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!group.ringtoneUriString.isNullOrBlank()) {
                        Icon(
                            imageVector = Icons.Outlined.MusicNote,
                            contentDescription = "Tono Personalizado",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isTakenToday) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                    ) {
                        Text(
                            text = if (isTakenToday) "✓ Tomado hoy" else "Pendiente",
                            color = if (isTakenToday) Color(0xFF2E7D32) else Color(0xFFE65100),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(10.dp))

            // Pills chips list
            if (groupWithMeds.medications.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    groupWithMeds.medications.forEach { med ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text("${med.name}${med.dosage?.let { " ($it)" } ?: ""}") },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Medication,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            } else {
                Text(
                    text = "No hay medicamentos en este horario (Toca para editar)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Test Alarm Button
                IconButton(
                    onClick = onTestAlarm,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsActive,
                        contentDescription = "Probar Alarma",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }

                OutlinedButton(
                    onClick = onSnooze10Min,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Snooze, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("10m", fontSize = 13.sp)
                }

                Button(
                    onClick = onConfirmToday,
                    enabled = !isTakenToday,
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isTakenToday) "¡Listo!" else "Marcar tomado",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionWarningBanner(onActionClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Permiso de Alarma Exacta",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Activa 'Alarmas y recordatorios' para sonar a la hora precisa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            TextButton(onClick = onActionClick) {
                Text("ACTIVAR", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyHorariosPlaceholder(
    hasPersons: Boolean,
    onAddPerson: () -> Unit,
    onAddGroup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Alarm,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.outlineVariant
            )
            Text(
                text = "No tienes horarios configurados",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (!hasPersons) {
                    "Primero crea un perfil de persona para comenzar a organizar los medicamentos."
                } else {
                    "Toca el botón '+' para agregar el primer recordatorio con hora y medicamentos."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (!hasPersons) {
                Button(onClick = onAddPerson, shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Crear Primera Persona")
                }
            } else {
                Button(onClick = onAddGroup, shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Default.AlarmAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Crear Primer Horario")
                }
            }
        }
    }
}
