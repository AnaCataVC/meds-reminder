package com.medsreminder.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medsreminder.data.local.entity.MedicationEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMedicationSheet(
    medicationToEdit: MedicationEntity? = null,
    onDismiss: () -> Unit,
    onSave: (id: Long, name: String, dosage: String?, description: String?) -> Unit
) {
    var name by remember(medicationToEdit) { mutableStateOf(medicationToEdit?.name ?: "") }
    var dosage by remember(medicationToEdit) { mutableStateOf(medicationToEdit?.dosage ?: "") }
    var description by remember(medicationToEdit) { mutableStateOf(medicationToEdit?.description ?: "") }

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
        ) {
            Text(
                text = if (medicationToEdit == null) "Nuevo Medicamento" else "Editar Medicamento",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Guarda el fármaco en el catálogo para reutilizarlo en cualquier horario.",
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
                        if (name.isNotBlank()) {
                            onSave(
                                medicationToEdit?.id ?: 0L,
                                name,
                                dosage.takeIf { it.isNotBlank() },
                                description.takeIf { it.isNotBlank() }
                            )
                            onDismiss()
                        }
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Guardar Fármaco", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
