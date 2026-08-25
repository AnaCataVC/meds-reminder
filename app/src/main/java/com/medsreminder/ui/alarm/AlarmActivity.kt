package com.medsreminder.ui.alarm

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medsreminder.core.notification.NotificationHelper
import com.medsreminder.data.local.dao.MedicationGroupDao
import com.medsreminder.data.local.dao.PersonDao
import com.medsreminder.data.local.entity.MedicationGroupWithMedications
import com.medsreminder.data.local.entity.PersonEntity
import com.medsreminder.domain.scheduler.AlarmScheduler
import com.medsreminder.ui.theme.MedsReminderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Fullscreen / Popup Alert Activity presented directly over lockscreen when alarm triggers.
 */
class AlarmActivity : ComponentActivity() {

    private val groupDao: MedicationGroupDao by inject()
    private val personDao: PersonDao by inject()
    private val alarmScheduler: AlarmScheduler by inject()
    private val notificationHelper: NotificationHelper by inject()

    companion object {
        const val EXTRA_GROUP_ID = "extra_group_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenDisplay()

        val groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1L)

        setContent {
            MedsReminderTheme {
                var groupWithMeds by remember { mutableStateOf<MedicationGroupWithMedications?>(null) }
                var person by remember { mutableStateOf<PersonEntity?>(null) }
                var isLoading by remember { mutableStateOf(true) }

                LaunchedEffect(groupId) {
                    if (groupId != -1L) {
                        val group = withContext(Dispatchers.IO) { groupDao.getGroupById(groupId) }
                        groupWithMeds = group
                        if (group != null) {
                            person = withContext(Dispatchers.IO) {
                                personDao.getPersonById(group.group.personId).firstOrNull()
                            }
                        }
                    }
                    isLoading = false
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (groupWithMeds != null) {
                    AlarmPopupContent(
                        groupWithMeds = groupWithMeds!!,
                        person = person,
                        onConfirmTaken = {
                            CoroutineScope(Dispatchers.IO).launch {
                                val today = LocalDate.now()
                                groupDao.markGroupAsTaken(groupId, today)
                                notificationHelper.cancelNotification(groupId.toInt())
                                notificationHelper.cancelNotification(groupId.toInt() + 100000)
                                val updatedGroup = groupWithMeds!!.group.copy(lastTakenDate = today, snoozeUntilEpochMs = null)
                                alarmScheduler.schedule(updatedGroup)
                                withContext(Dispatchers.Main) { finish() }
                            }
                        },
                        onSnooze10Min = {
                            CoroutineScope(Dispatchers.IO).launch {
                                val snoozeEpoch = System.currentTimeMillis() + (10 * 60 * 1000L)
                                groupDao.setSnoozeTime(groupId, snoozeEpoch)
                                notificationHelper.cancelNotification(groupId.toInt())
                                notificationHelper.cancelNotification(groupId.toInt() + 100000)
                                alarmScheduler.scheduleSnooze(groupId, snoozeEpoch)
                                withContext(Dispatchers.Main) { finish() }
                            }
                        },
                        onCancelToday = {
                            CoroutineScope(Dispatchers.IO).launch {
                                val today = LocalDate.now()
                                groupDao.markGroupSkippedToday(groupId, today)
                                notificationHelper.cancelNotification(groupId.toInt())
                                notificationHelper.cancelNotification(groupId.toInt() + 100000)
                                val updatedGroup = groupWithMeds!!.group.copy(lastTakenDate = today, snoozeUntilEpochMs = null)
                                alarmScheduler.schedule(updatedGroup)
                                withContext(Dispatchers.Main) { finish() }
                            }
                        }
                    )
                } else {
                    LaunchedEffect(Unit) { finish() }
                }
            }
        }
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
    onConfirmTaken: () -> Unit,
    onSnooze10Min: () -> Unit,
    onCancelToday: () -> Unit
) {
    val group = groupWithMeds.group
    val personName = person?.name ?: "Usuario"
    val personColor = remember(person?.colorHex) {
        runCatching { Color(AndroidColor.parseColor(person?.colorHex ?: "#0061A4")) }
            .getOrDefault(Color(0xFF0061A4))
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
                        Text(
                            text = "Medicamentos a tomar:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (groupWithMeds.medications.isEmpty()) {
                            Text(
                                text = "Sin medicamentos especificados",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onConfirmTaken,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("¡TOMADO! (Confirmar)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSnooze10Min,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Snooze, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Posponer 10m", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        FilledTonalButton(
                            onClick = onCancelToday,
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
                            Text("Cancelar hoy", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
