package com.medsreminder.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medsreminder.data.local.entity.PersonEntity
import com.medsreminder.ui.dialogs.AddEditPersonSheet
import com.medsreminder.ui.main.MainUiIntent
import com.medsreminder.ui.main.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilesScreen(
    state: MainUiState,
    onIntent: (MainUiIntent) -> Unit
) {
    var showAddPersonSheet by remember { mutableStateOf(false) }
    var personToEdit by remember { mutableStateOf<PersonEntity?>(null) }
    var personToDelete by remember { mutableStateOf<PersonEntity?>(null) }

    if (showAddPersonSheet || personToEdit != null) {
        AddEditPersonSheet(
            personToEdit = personToEdit,
            onDismiss = {
                showAddPersonSheet = false
                personToEdit = null
            },
            onSave = { id, name, colorHex ->
                onIntent(MainUiIntent.SavePerson(id = id, name = name, colorHex = colorHex))
            }
        )
    }

    if (personToDelete != null) {
        AlertDialog(
            onDismissRequest = { personToDelete = null },
            title = { Text("Eliminar Perfil") },
            text = {
                Text("¿Estás seguro de eliminar a '${personToDelete?.name}'? Todos los horarios y recordatorios asociados a este perfil también serán eliminados.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        personToDelete?.let { onIntent(MainUiIntent.DeletePerson(it)) }
                        personToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { personToDelete = null }) {
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
                        text = "Perfiles y Pacientes",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddPersonSheet = true },
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("Nueva Persona") },
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
            if (state.persons.isEmpty()) {
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
                            imageVector = Icons.Outlined.People,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                        Text(
                            text = "No hay perfiles registrados",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Button(onClick = { showAddPersonSheet = true }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Crear Primer Perfil")
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.persons, key = { it.id }) { person ->
                        val personColor = remember(person.colorHex) {
                            runCatching { Color(AndroidColor.parseColor(person.colorHex)) }
                                .getOrDefault(Color.Gray)
                        }

                        val schedulesCount = state.groupsWithMedications.count {
                            it.group.personId == person.id
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
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
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(personColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = person.name.take(2).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column {
                                        Text(
                                            text = person.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "$schedulesCount ${if (schedulesCount == 1) "horario asignado" else "horarios asignados"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row {
                                    IconButton(onClick = { personToEdit = person }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = "Editar",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = { personToDelete = person }) {
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
