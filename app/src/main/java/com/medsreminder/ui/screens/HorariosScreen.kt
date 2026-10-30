package com.medsreminder.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medsreminder.core.alarm.AndroidAlarmScheduler
import com.medsreminder.data.local.entity.MedicationGroupWithMedications
import com.medsreminder.data.local.entity.PersonEntity
import com.medsreminder.ui.dialogs.AddEditPersonSheet
import com.medsreminder.ui.dialogs.SnoozeOptionsSheet
import com.medsreminder.ui.main.MainUiIntent
import com.medsreminder.ui.main.MainUiState
import com.medsreminder.ui.theme.PrimaryBlue
import com.medsreminder.ui.theme.SuccessContainer
import com.medsreminder.ui.theme.SuccessGreen
import com.medsreminder.ui.theme.WarningContainer
import com.medsreminder.ui.theme.WarningOrange
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
            onSave = { _, name, colorHex, ringtone ->
                onIntent(MainUiIntent.SavePerson(name = name, colorHex = colorHex, ringtoneUriString = ringtone))
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
                            defaultSnoozeMinutes = state.defaultSnoozeMinutes,
                            onToggleActive = { isActive ->
                                onIntent(MainUiIntent.ToggleGroupActive(groupWithMeds.group.id, isActive))
                            },
                            onConfirmToday = {
                                onIntent(MainUiIntent.ConfirmIntakeToday(groupWithMeds.group.id))
                            },
                            onSnooze = { minutes ->
                                onIntent(MainUiIntent.SnoozeGroup(groupWithMeds.group.id, minutes))
                            },
                            onCancelSnooze = {
                                onIntent(MainUiIntent.CancelSnooze(groupWithMeds.group.id))
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
                runCatching { Color(AndroidColor.parseColor(person.colorHex)) }.getOrDefault(PrimaryBlue)
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
                .padding(14.dp),
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
                Column(modifier = Modifier.padding(end = 8.dp)) {
                    Text(
                        text = if (isSuspended) "Alarmas de ${person.name} pausadas" else "Pausar alarmas de ${person.name}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isSuspended) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isSuspended) "Se reactivarán automáticamente al vencer el plazo." else "Suspende todos sus recordatorios temporalmente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSuspended) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isSuspended) {
                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Reanudar", fontSize = 12.sp, maxLines = 1)
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onSuspend6h,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("6h", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = onSuspendToday,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Hoy", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
    defaultSnoozeMinutes: Int,
    onToggleActive: (Boolean) -> Unit,
    onConfirmToday: () -> Unit,
    onSnooze: (Int) -> Unit,
    onCancelSnooze: () -> Unit,
    onTestAlarm: () -> Unit,
    onClickEdit: () -> Unit
) {
    val group = groupWithMeds.group
    val today = remember { LocalDate.now() }
    val isTakenToday = group.lastTakenDate == today
    val isRestDay = !AndroidAlarmScheduler.isInActivePhase(group, today)
    val isSnoozed = group.snoozeUntilEpochMs != null && group.snoozeUntilEpochMs > System.currentTimeMillis()
    val snoozeFormatted = remember(group.snoozeUntilEpochMs) {
        group.snoozeUntilEpochMs?.let {
            java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
                .format(DateTimeFormatter.ofPattern("hh:mm a"))
        }
    }

    var showSnoozeSheet by remember { mutableStateOf(false) }

    if (showSnoozeSheet) {
        SnoozeOptionsSheet(
            title = "Posponer - ${group.name}",
            currentSnoozeFormatted = if (isSnoozed) snoozeFormatted else null,
            onDismissRequest = { showSnoozeSheet = false },
            onSnoozeSelected = { mins ->
                onSnooze(mins.toInt())
            },
            onCancelSnooze = if (isSnoozed) onCancelSnooze else null
        )
    }

    val personColor = remember(person?.colorHex) {
        runCatching { Color(AndroidColor.parseColor(person?.colorHex ?: "#0061A4")) }
            .getOrDefault(PrimaryBlue)
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
                Column(modifier = Modifier.weight(1f, fill = false)) {
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
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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

                    val isDark = isSystemInDarkTheme()
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            isTakenToday -> if (isDark) SuccessGreen.copy(alpha = 0.25f) else SuccessContainer
                            isSnoozed -> MaterialTheme.colorScheme.tertiaryContainer
                            isRestDay -> MaterialTheme.colorScheme.surfaceVariant
                            else -> if (isDark) WarningOrange.copy(alpha = 0.25f) else WarningContainer
                        },
                        modifier = if (isSnoozed) Modifier.clickable { showSnoozeSheet = true } else Modifier
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = when {
                                    isTakenToday -> "✓ Tomado hoy"
                                    isSnoozed -> "⏳ Pospuesto ($snoozeFormatted)"
                                    isRestDay -> "En descanso"
                                    else -> "Pendiente"
                                },
                                color = when {
                                    isTakenToday -> if (isDark) SuccessContainer else SuccessGreen
                                    isSnoozed -> MaterialTheme.colorScheme.onTertiaryContainer
                                    isRestDay -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> if (isDark) WarningContainer else WarningOrange
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (isSnoozed) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancelar posposición",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier
                                        .size(13.dp)
                                        .clickable { onCancelSnooze() }
                                )
                            }
                        }
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

            // Action Buttons Strip with strict 40.dp height and aligned elements
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Test Alarm Button
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(40.dp)
                ) {
                    IconButton(
                        onClick = onTestAlarm,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsActive,
                            contentDescription = "Probar Alarma",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Split Snooze Button (Quick cumulative interval + dropdown trigger)
                Row(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSnooze(defaultSnoozeMinutes) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp, topEnd = 0.dp, bottomEnd = 0.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        enabled = !isTakenToday,
                        colors = if (isSnoozed) {
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.tertiary
                            )
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Snooze,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "+${defaultSnoozeMinutes}m",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = { showSnoozeSheet = true },
                        modifier = Modifier
                            .width(28.dp)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 10.dp, bottomEnd = 10.dp),
                        contentPadding = PaddingValues(0.dp),
                        enabled = !isTakenToday,
                        colors = if (isSnoozed) {
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.tertiary
                            )
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Opciones de posposición",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Confirm Intake Button
                Button(
                    onClick = onConfirmToday,
                    enabled = !isTakenToday,
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isTakenToday) "¡Listo!" else "Marcar tomado",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
