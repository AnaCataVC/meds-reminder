package com.medsreminder.ui.dialogs

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medsreminder.data.local.entity.PersonEntity

val PredefinedColors = listOf(
    "#0061A4", // Primary Blue
    "#2E7D32", // Forest Green
    "#E65100", // Warm Orange
    "#6A1B9A", // Deep Purple
    "#C2185B", // Rose / Pink
    "#00838F", // Teal / Cyan
    "#455A64", // Slate Gray
    "#D84315"  // Rust Red
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPersonSheet(
    personToEdit: PersonEntity? = null,
    onDismiss: () -> Unit,
    onSave: (id: Long, name: String, colorHex: String) -> Unit
) {
    var name by remember(personToEdit) { mutableStateOf(personToEdit?.name ?: "") }
    var selectedColor by remember(personToEdit) {
        mutableStateOf(personToEdit?.colorHex ?: PredefinedColors.first())
    }

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
                text = if (personToEdit == null) "Nuevo Perfil / Persona" else "Editar Perfil",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Crea un perfil para asignar medicamentos y horarios personalizados.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre o Parentesco") },
                placeholder = { Text("ej. Mamá, Carlos, Abuela Rosa") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Color identificador",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(PredefinedColors) { hex ->
                    val color = remember(hex) {
                        runCatching { Color(AndroidColor.parseColor(hex)) }.getOrDefault(Color.Gray)
                    }
                    val isSelected = selectedColor.equals(hex, ignoreCase = true)

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { selectedColor = hex }
                            .then(
                                if (isSelected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

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
                            onSave(personToEdit?.id ?: 0L, name, selectedColor)
                            onDismiss()
                        }
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Guardar Perfil", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
