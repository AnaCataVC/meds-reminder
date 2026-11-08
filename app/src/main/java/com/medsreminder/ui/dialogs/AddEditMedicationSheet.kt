package com.medsreminder.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medsreminder.data.local.entity.MedicationEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMedicationSheet(
    medicationToEdit: MedicationEntity? = null,
    onDismiss: () -> Unit,
    onSave: (
        id: Long,
        name: String,
        dosage: String?,
        description: String?,
        daysOfWeekMask: Int,
        cycleActiveDays: Int,
        cycleRestDays: Int,
        cycleStartDate: LocalDate?
    ) -> Unit
) {
    var name by remember(medicationToEdit) { mutableStateOf(medicationToEdit?.name ?: "") }
    var dosage by remember(medicationToEdit) { mutableStateOf(medicationToEdit?.dosage ?: "") }
    var description by remember(medicationToEdit) { mutableStateOf(medicationToEdit?.description ?: "") }
    var daysOfWeekMask by remember(medicationToEdit) { mutableStateOf(medicationToEdit?.daysOfWeekMask ?: 127) }
    var cycleEnabled by remember(medicationToEdit) { mutableStateOf((medicationToEdit?.cycleActiveDays ?: 0) > 0) }
    var cycleActiveDaysText by remember(medicationToEdit) {
        mutableStateOf(medicationToEdit?.cycleActiveDays?.takeIf { it > 0 }?.toString() ?: "21")
    }
    var cycleRestDaysText by remember(medicationToEdit) {
        mutableStateOf(medicationToEdit?.cycleRestDays?.takeIf { it > 0 }?.toString() ?: "7")
    }
    var cycleStartDate by remember(medicationToEdit) {
        mutableStateOf(medicationToEdit?.cycleStartDate ?: LocalDate.now())
    }
    var showCycleDatePicker by remember { mutableStateOf(false) }

    val cycleActiveDays = cycleActiveDaysText.toIntOrNull() ?: 0
    val cycleRestDays = cycleRestDaysText.toIntOrNull() ?: 0
    val isCycleInvalid = cycleEnabled && (cycleActiveDays <= 0 || cycleRestDays <= 0)
    val canSave = name.isNotBlank() && !isCycleInvalid

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = if (medicationToEdit == null) "Nuevo Medicamento" else "Editar Medicamento",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Configura el fármaco, sus días de toma y ciclos de descanso.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre del Medicamento *") },
                placeholder = { Text("ej. Losartán, Paracetamol, Insulina") },
                leadingIcon = { Icon(Icons.Default.Medication, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = dosage,
                onValueChange = { dosage = it },
                label = { Text("Dosis / Presentación (Opcional)") },
                placeholder = { Text("ej. 500 mg, 1 comprimido, 10 ml") },
                leadingIcon = { Icon(Icons.Default.Numbers, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Instrucciones / Notas (Opcional)") },
                placeholder = { Text("ej. Tomar con abundante agua después del almuerzo") },
                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Días de la Semana
            Text(
                text = "Días de repetición",
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
                            .size(38.dp)
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

            Spacer(modifier = Modifier.height(8.dp))

            // Quick preset chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = daysOfWeekMask == 127,
                    onClick = { daysOfWeekMask = 127 },
                    label = { Text("Diario", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = daysOfWeekMask == 31,
                    onClick = { daysOfWeekMask = 31 },
                    label = { Text("Lun a Vie", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = daysOfWeekMask == 96,
                    onClick = { daysOfWeekMask = 96 },
                    label = { Text("Fines de sem.", fontSize = 12.sp) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Ciclo de Descanso
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ciclo de descanso",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Toma durante unos días y descansa otros (ej. anticonceptivos 21/7).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(checked = cycleEnabled, onCheckedChange = { cycleEnabled = it })
            }

            if (cycleEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cycleActiveDaysText,
                        onValueChange = { cycleActiveDaysText = it.filter(Char::isDigit).take(3) },
                        label = { Text("Días toma") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cycleRestDaysText,
                        onValueChange = { cycleRestDaysText = it.filter(Char::isDigit).take(3) },
                        label = { Text("Días descanso") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { showCycleDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Inicio del ciclo: ${cycleStartDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}")
                }
            }

            if (showCycleDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = cycleStartDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                )
                DatePickerDialog(
                    onDismissRequest = { showCycleDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let {
                                cycleStartDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                            }
                            showCycleDatePicker = false
                        }) { Text("Aceptar") }
                    },
                    dismissButton = { TextButton(onClick = { showCycleDatePicker = false }) { Text("Cancelar") } }
                ) { DatePicker(state = datePickerState) }
            }

            Spacer(modifier = Modifier.height(26.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        if (canSave) {
                            onSave(
                                medicationToEdit?.id ?: 0L,
                                name,
                                dosage.takeIf { it.isNotBlank() },
                                description.takeIf { it.isNotBlank() },
                                if (daysOfWeekMask == 0) 127 else daysOfWeekMask,
                                if (cycleEnabled) cycleActiveDays else 0,
                                if (cycleEnabled) cycleRestDays else 0,
                                if (cycleEnabled) cycleStartDate else null
                            )
                            onDismiss()
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Guardar Fármaco", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
