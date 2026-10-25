package com.medsreminder.ui.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.medsreminder.core.widget.WidgetUpdater
import com.medsreminder.data.local.dao.PersonDao
import com.medsreminder.data.local.entity.PersonEntity
import com.medsreminder.ui.theme.MedsReminderTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Configuration Activity launched when placing a widget on the home screen.
 * Allows the user to bind the widget instance to a specific person's profile or "Todos".
 */
class WidgetConfigurationActivity : ComponentActivity() {

    private val personDao: PersonDao by inject()
    private val widgetUpdater: WidgetUpdater by inject()
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cancel upfront in case the user backs out without picking
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            val persons by personDao.getAllPersons().collectAsState(initial = emptyList())

            MedsReminderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WidgetConfigurationContent(
                        persons = persons,
                        onSelectPerson = { selectedPersonId ->
                            saveConfigurationAndExit(selectedPersonId)
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }

    private fun saveConfigurationAndExit(selectedPersonId: Long?) {
        lifecycleScope.launch {
            try {
                // 1. Dual-store: Immediately save to SharedPreferences keyed by appWidgetId (atomic & synchronous commit)
                val sp = applicationContext.getSharedPreferences("meds_widgets_prefs", Context.MODE_PRIVATE)
                sp.edit().putLong("widget_person_$appWidgetId", selectedPersonId ?: -1L).commit()

                // 2. Identify target AppWidget provider
                val appWidgetInfo = AppWidgetManager.getInstance(applicationContext).getAppWidgetInfo(appWidgetId)
                val providerClassName = appWidgetInfo?.provider?.className ?: ""

                // 3. Update Glance internal state with retry loop
                val glanceManager = GlanceAppWidgetManager(applicationContext)
                var glanceId: GlanceId? = null
                for (attempt in 1..4) {
                    glanceId = runCatching { glanceManager.getGlanceIdBy(appWidgetId) }.getOrNull()
                    if (glanceId != null) break
                    delay(60)
                }

                if (glanceId != null) {
                    runCatching {
                        updateAppWidgetState(applicationContext, glanceId) { prefs ->
                            prefs[WidgetConstants.PREF_SELECTED_PERSON_ID] = selectedPersonId ?: -1L
                        }
                    }

                    // 4. Update the exact matching widget class safely without cross-class collision
                    if (providerClassName.contains("NextDose")) {
                        runCatching { NextDoseWidget().update(applicationContext, glanceId) }
                    } else if (providerClassName.contains("DailySchedule")) {
                        runCatching { DailyScheduleWidget().update(applicationContext, glanceId) }
                    } else {
                        runCatching { NextDoseWidget().update(applicationContext, glanceId) }
                        runCatching { DailyScheduleWidget().update(applicationContext, glanceId) }
                    }
                }

                // 5. Trigger broadcast update across active widgets
                widgetUpdater.notifyWidgetsChanged()
            } catch (e: Exception) {
                Log.e("WidgetConfig", "Failed to update widget state for appWidgetId=$appWidgetId", e)
            } finally {
                val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                setResult(Activity.RESULT_OK, resultValue)
                finish()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun WidgetConfigurationContent(
        persons: List<PersonEntity>,
        onSelectPerson: (Long?) -> Unit,
        onCancel: () -> Unit
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Configurar Widget") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                Text(
                    text = "¿Qué perfil deseas mostrar en este widget?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Puedes tener múltiples widgets en tu inicio configurados para distintas personas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Option: Todos los perfiles
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPerson(null) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "Todos los perfiles",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Muestra los recordatorios de toda la familia",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }

                    // Individual profile options
                    items(persons, key = { it.id }) { person ->
                        val personColor = remember(person.colorHex) {
                            runCatching { Color(AndroidColor.parseColor(person.colorHex)) }.getOrDefault(Color.Gray)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPerson(person.id) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(personColor)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = person.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Solo recordatorios de ${person.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancelar")
                }
            }
        }
    }
}
