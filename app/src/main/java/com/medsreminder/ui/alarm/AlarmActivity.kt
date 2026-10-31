package com.medsreminder.ui.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medsreminder.data.local.entity.MedicationGroupWithMedications
import com.medsreminder.data.local.entity.PersonEntity
import com.medsreminder.ui.theme.MedsReminderTheme
import com.medsreminder.ui.theme.PrimaryBlue
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Fullscreen / Popup Alert Activity presented directly over lockscreen when alarm triggers.
 */
class AlarmActivity : ComponentActivity() {

    private val viewModel: AlarmViewModel by viewModel()

    companion object {
        const val EXTRA_GROUP_ID = "extra_group_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenDisplay()

        val groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1L)
        viewModel.loadAlarm(groupId)

        setContent {
            MedsReminderTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(uiState.isFinished) {
                    if (uiState.isFinished) {
                        finish()
                    }
                }

                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.groupWithMeds != null) {
                    AlarmPopupContent(
                        groupWithMeds = uiState.groupWithMeds!!,
                        person = uiState.person,
                        isIndividualConfirmationEnabled = uiState.isIndividualConfirmationEnabled,
                        onConfirmTaken = { viewModel.confirmTaken() },
                        onConfirmPartial = { taken, skipped -> viewModel.confirmPartial(taken, skipped) },
                        onSnooze = { minutes -> viewModel.snooze(minutes) },
                        onCancelToday = { viewModel.cancelToday() }
                    )
                }
            }
        }
    }

    // singleTask: a second alarm arriving while this screen is up must replace the shown group.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.loadAlarm(intent.getLongExtra(EXTRA_GROUP_ID, -1L))
    }

    private fun configureLockScreenDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmPopupContent(
    groupWithMeds: MedicationGroupWithMedications,
    person: PersonEntity?,
    isIndividualConfirmationEnabled: Boolean = false,
    onConfirmTaken: () -> Unit,
    onConfirmPartial: (takenMedicationIds: List<Long>, skippedMedicationIds: List<Long>) -> Unit,
    onSnooze: (minutes: Long) -> Unit,
    onCancelToday: () -> Unit
) {
    val context = LocalContext.current
    var showSnoozeSheet by remember { mutableStateOf(false) }

    val handleSnooze: (Long) -> Unit = { minutes ->
        val targetTime = LocalTime.now().plusMinutes(minutes).format(DateTimeFormatter.ofPattern("hh:mm a"))
        Toast.makeText(context, "Alarma pospuesta para las $targetTime", Toast.LENGTH_SHORT).show()
        onSnooze(minutes)
    }

    val handleCancel: () -> Unit = {
        Toast.makeText(context, "Dosis omitida por hoy", Toast.LENGTH_SHORT).show()
        onCancelToday()
    }

    val group = groupWithMeds.group
    val personName = person?.name ?: "Usuario"
    val personColor = remember(person?.colorHex) {
        runCatching { Color(AndroidColor.parseColor(person?.colorHex ?: "#0061A4")) }
            .getOrDefault(PrimaryBlue)
    }

    val showIndividualSelection = isIndividualConfirmationEnabled && groupWithMeds.medications.size >= 2
    val selectedMedicationIds = remember(groupWithMeds) {
        mutableStateMapOf<Long, Boolean>().apply {
            groupWithMeds.medications.forEach { put(it.id, true) }
        }
    }

    val totalMeds = groupWithMeds.medications.size
    val takenCount = if (showIndividualSelection) selectedMedicationIds.count { it.value } else totalMeds

    val handleConfirm = {
        if (!showIndividualSelection || takenCount == totalMeds) {
            onConfirmTaken()
        } else if (takenCount == 0) {
            handleCancel()
        } else {
            val takenIds = selectedMedicationIds.filter { it.value }.keys.toList()
            val skippedIds = selectedMedicationIds.filter { !it.value }.keys.toList()
            Toast.makeText(context, "$takenCount de $totalMeds tomados. Restantes omitidos.", Toast.LENGTH_SHORT).show()
            onConfirmPartial(takenIds, skippedIds)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            personColor.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header & Pulsing Icon
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .scale(pulseScale)
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(personColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = "Alarma",
                            tint = Color.White,
                            modifier = Modifier.size(52.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = personColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Para: $personName",
                            color = personColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = group.scheduledTime.format(DateTimeFormatter.ofPattern("hh:mm a")),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Medication Cards Summary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (showIndividualSelection) "Marca los medicamentos tomados:" else "Medicamentos a tomar:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (showIndividualSelection) {
                                Text(
                                    text = "$takenCount/$totalMeds",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = personColor
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        if (groupWithMeds.medications.isEmpty()) {
                            Text(
                                text = "Sin medicamentos especificados",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        } else if (showIndividualSelection) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                groupWithMeds.medications.forEach { med ->
                                    val isChecked = selectedMedicationIds[med.id] ?: true
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isChecked) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedMedicationIds[med.id] = !isChecked }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { selectedMedicationIds[med.id] = it },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = personColor,
                                                    checkmarkColor = Color.White
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = med.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isChecked) {
                                                        MaterialTheme.colorScheme.onSurface
                                                    } else {
                                                        MaterialTheme.colorScheme.outline
                                                    }
                                                )
                                                med.dosage?.let {
                                                    Text(
                                                        text = it,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                groupWithMeds.medications.forEach { med ->
                                    SuggestionChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                text = "${med.name}${med.dosage?.let { " ($it)" } ?: ""}",
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.Medication,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = personColor
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Action Buttons
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showIndividualSelection && takenCount in 1 until totalMeds) {
                        Text(
                            text = "Los medicamentos no marcados se registrarán como omitidos por hoy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    Button(
                        onClick = handleConfirm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        val buttonText = when {
                            !showIndividualSelection || takenCount == totalMeds -> "¡TOMADO! (Confirmar)"
                            takenCount == 0 -> "Omitir todo hoy"
                            else -> "Confirmar tomas ($takenCount/$totalMeds)"
                        }
                        Text(buttonText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showSnoozeSheet = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Snooze, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Posponer...", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        FilledTonalButton(
                            onClick = handleCancel,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Omitir hoy", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showSnoozeSheet) {
        SnoozeOptionsBottomSheet(
            onDismissRequest = { showSnoozeSheet = false },
            onSnoozeSelected = { minutes ->
                showSnoozeSheet = false
                handleSnooze(minutes)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnoozeOptionsBottomSheet(
    onDismissRequest: () -> Unit,
    onSnoozeSelected: (minutes: Long) -> Unit
) {
    var showCustomDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Posponer Recordatorio",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Elige cuándo quieres que vuelva a sonar:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            val options = listOf(
                15L to "15 minutos",
                30L to "30 minutos",
                60L to "1 hora",
                120L to "2 horas"
            )

            options.forEach { (mins, label) ->
                val timeFormatted = remember(mins) {
                    LocalTime.now().plusMinutes(mins).format(DateTimeFormatter.ofPattern("hh:mm a"))
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onClick = { onSnoozeSelected(mins) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Snooze,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = timeFormatted,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showCustomDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tiempo personalizado...")
            }
        }
    }

    if (showCustomDialog) {
        var customMinutesText by remember { mutableStateOf("45") }
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("Posponer Alarma") },
            text = {
                Column {
                    Text("Ingresa los minutos a posponer:")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customMinutesText,
                        onValueChange = { customMinutesText = it.filter { char -> char.isDigit() } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Minutos") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val mins = customMinutesText.toLongOrNull() ?: 15L
                        if (mins > 0) {
                            showCustomDialog = false
                            onSnoozeSelected(mins)
                        }
                    }
                ) {
                    Text("Posponer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
