package com.medsreminder.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medsreminder.ui.dialogs.BatteryOptimizationDialog
import com.medsreminder.ui.main.MainUiIntent
import com.medsreminder.ui.main.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesScreen(
    state: MainUiState,
    onIntent: (MainUiIntent) -> Unit,
    onRequestAlarmPermission: () -> Unit
) {
    val context = LocalContext.current
    var showBatteryDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { onIntent(MainUiIntent.ExportBackup(it)) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onIntent(MainUiIntent.ImportBackup(it)) }
    }

    if (showBatteryDialog) {
        BatteryOptimizationDialog(onDismiss = { showBatteryDialog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Ajustes y Respaldo",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Confiabilidad y Permisos
            SectionHeader(title = "Confiabilidad del Sistema y Alarmas")

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Outlined.Alarm,
                        title = "Permiso de Alarma Exacta",
                        subtitle = if (state.hasExactAlarmPermission) "Concedido (Máxima precisión activa)" else "Restringido (Toca para activar)",
                        statusColor = if (state.hasExactAlarmPermission) Color(0xFF2E7D32) else Color(0xFFBA1A1A),
                        onClick = onRequestAlarmPermission
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsRow(
                        icon = Icons.Outlined.BatteryAlert,
                        title = "Optimización de Batería (OEM)",
                        subtitle = "Guía para Xiaomi, Samsung, Huawei y otros",
                        onClick = { showBatteryDialog = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsRow(
                        icon = Icons.Outlined.Notifications,
                        title = "Ajustes de Notificaciones",
                        subtitle = "Configurar sonido y vibración global del sistema",
                        onClick = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // Section 2: Copia de Seguridad (SAF)
            SectionHeader(title = "Copia de Seguridad (100% Local)")

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Outlined.CloudUpload,
                        title = "Exportar Respaldo (.json)",
                        subtitle = "Guarda tus personas, medicamentos y horarios en tu dispositivo o Google Drive",
                        onClick = {
                            val filename = "meds_backup_${System.currentTimeMillis()}.json"
                            exportLauncher.launch(filename)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsRow(
                        icon = Icons.Outlined.CloudDownload,
                        title = "Restaurar Respaldo (.json)",
                        subtitle = "Carga un archivo de respaldo previo y reprograma todas las alarmas",
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "text/*"))
                        }
                    )
                }
            }

            // Section 3: Acerca de la App
            SectionHeader(title = "Información")

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Meds Reminder v${com.medsreminder.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Aplicación 100% nativa y offline-first. Tus datos médicos nunca salen de este dispositivo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    statusColor: Color? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = statusColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor ?: MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}
