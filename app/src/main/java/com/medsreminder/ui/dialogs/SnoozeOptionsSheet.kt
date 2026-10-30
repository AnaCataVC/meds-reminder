package com.medsreminder.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Modal bottom sheet allowing the user to select quick snooze durations,
 * enter custom minutes, or cancel an active snooze.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnoozeOptionsSheet(
    title: String = "Posponer Recordatorio",
    currentSnoozeFormatted: String? = null,
    onDismissRequest: () -> Unit,
    onSnoozeSelected: (minutes: Long) -> Unit,
    onCancelSnooze: (() -> Unit)? = null
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
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (currentSnoozeFormatted != null) {
                    "Actualmente pospuesto hasta las $currentSnoozeFormatted. Elige cuándo reprogramar:"
                } else {
                    "Elige cuándo quieres que vuelva a sonar hoy:"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            val options = listOf(
                5L to "5 minutos",
                10L to "10 minutos",
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
                    onClick = {
                        onDismissRequest()
                        onSnoozeSelected(mins)
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tiempo personalizado...")
            }

            if (onCancelSnooze != null && currentSnoozeFormatted != null) {
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        onDismissRequest()
                        onCancelSnooze()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restablecer horario original")
                }
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
                    Text(
                        "Ingresa los minutos a posponer a partir de ahora:",
                        style = MaterialTheme.typography.bodyMedium
                    )
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
                            onDismissRequest()
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
