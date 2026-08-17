package com.medsreminder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medsreminder.data.local.entity.MedicationEntity
import com.medsreminder.ui.dialogs.AddEditMedicationSheet
import com.medsreminder.ui.main.MainUiIntent
import com.medsreminder.ui.main.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicamentosScreen(
    state: MainUiState,
    onIntent: (MainUiIntent) -> Unit
) {
    var showAddMedSheet by remember { mutableStateOf(false) }
    var medicationToEdit by remember { mutableStateOf<MedicationEntity?>(null) }
    var medicationToDelete by remember { mutableStateOf<MedicationEntity?>(null) }

    if (showAddMedSheet || medicationToEdit != null) {
        AddEditMedicationSheet(
            medicationToEdit = medicationToEdit,
            onDismiss = {
                showAddMedSheet = false
                medicationToEdit = null
            },
            onSave = { id, name, dosage, description ->
                onIntent(
                    MainUiIntent.SaveMedication(
                        id = id,
                        name = name,
                        dosage = dosage,
                        description = description
                    )
                )
            }
        )
    }

    if (medicationToDelete != null) {
        AlertDialog(
            onDismissRequest = { medicationToDelete = null },
            title = { Text("Eliminar Medicamento") },
            text = {
                Text("¿Estás seguro de eliminar '${medicationToDelete?.name}' del catálogo maestro? Se desvinculará de los horarios que lo usen.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        medicationToDelete?.let { onIntent(MainUiIntent.DeleteMedication(it)) }
                        medicationToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { medicationToDelete = null }) {
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
                        text = "Catálogo de Medicamentos",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddMedSheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nuevo Medicamento") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = state.medicationSearchQuery,
                onValueChange = { onIntent(MainUiIntent.SearchMedications(it)) },
                placeholder = { Text("Buscar en el catálogo...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.medicationSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { onIntent(MainUiIntent.SearchMedications("")) }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            // Medication List
            if (state.catalogMedications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Medication,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                        Text(
                            text = if (state.medicationSearchQuery.isNotBlank()) {
                                "No se encontraron medicamentos para '${state.medicationSearchQuery}'"
                            } else {
                                "El catálogo está vacío"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        if (state.medicationSearchQuery.isBlank()) {
                            Button(onClick = { showAddMedSheet = true }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Registrar Primer Medicamento")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.catalogMedications, key = { it.id }) { med ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Medication,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column {
                                        Text(
                                            text = med.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (!med.dosage.isNullOrBlank()) {
                                            Text(
                                                text = "Dosis: ${med.dosage}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (!med.description.isNullOrBlank()) {
                                            Text(
                                                text = med.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }

                                Row {
                                    IconButton(onClick = { medicationToEdit = med }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = "Editar",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = { medicationToDelete = med }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = "Eliminar",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
