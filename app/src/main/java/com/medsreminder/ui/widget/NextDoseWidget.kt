package com.medsreminder.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.medsreminder.MainActivity
import com.medsreminder.core.widget.WidgetScheduleItem
import com.medsreminder.core.widget.WidgetScheduleResolver
import com.medsreminder.data.local.dao.MedicationGroupDao
import com.medsreminder.data.local.dao.PersonDao
import com.medsreminder.data.local.entity.PersonEntity
import com.medsreminder.ui.theme.PrimaryBlue
import com.medsreminder.ui.widget.action.ConfirmIntakeActionCallback
import com.medsreminder.ui.widget.action.SnoozeActionCallback
import kotlinx.coroutines.flow.emptyFlow
import org.koin.core.context.GlobalContext
import java.time.format.DateTimeFormatter

/**
 * Compact home screen widget (2x2 / 3x2) displaying the immediate next upcoming or due dose.
 */
class NextDoseWidget : GlanceAppWidget() {

    override var stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val koin = runCatching { GlobalContext.get() }.getOrNull()
        val groupDao: MedicationGroupDao? = koin?.getOrNull()
        val personDao: PersonDao? = koin?.getOrNull()

        val initialGroups = groupDao?.getAllActiveGroupsSync() ?: emptyList()
        val initialPersons = personDao?.getAllPersonsList() ?: emptyList()

        provideContent {
            val prefs = currentState<Preferences>()
            var selectedPersonId = prefs[WidgetConstants.PREF_SELECTED_PERSON_ID]?.takeIf { it > 0 }

            // Fallback: Infallible lookup from SharedPreferences keyed by platform appWidgetId
            if (selectedPersonId == null) {
                val appWidgetId = WidgetUtils.extractAppWidgetId(id)
                if (appWidgetId != null && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val sp = context.getSharedPreferences("meds_widgets_prefs", Context.MODE_PRIVATE)
                    val spPersonId = sp.getLong("widget_person_$appWidgetId", -1L)
                    if (spPersonId > 0) {
                        selectedPersonId = spPersonId
                    }
                }
            }

            val groupsFlow = remember { groupDao?.getAllGroupsWithMedications() ?: emptyFlow() }
            val personsFlow = remember { personDao?.getAllPersons() ?: emptyFlow() }

            val groups by groupsFlow.collectAsState(initial = initialGroups)
            val persons by personsFlow.collectAsState(initial = initialPersons)

            val nextDose = remember(groups, persons, selectedPersonId) {
                WidgetScheduleResolver.resolveNextDose(
                    groupsWithMeds = groups,
                    persons = persons,
                    selectedPersonId = selectedPersonId
                )
            }
            val configuredPerson = remember(persons, selectedPersonId) {
                selectedPersonId?.let { pid -> persons.find { it.id == pid } }
            }

            GlanceTheme {
                NextDoseContent(
                    nextDose = nextDose,
                    configuredPerson = configuredPerson
                )
            }
        }
    }

    @Composable
    private fun NextDoseContent(
        nextDose: WidgetScheduleItem?,
        configuredPerson: PersonEntity?
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(16.dp)
                .padding(14.dp)
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize()
            ) {
                // Header Row (Clickable to open MainActivity)
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val badgeColor = runCatching {
                        val hex = configuredPerson?.colorHex ?: nextDose?.personColorHex
                        if (hex != null) Color(AndroidColor.parseColor(hex)) else PrimaryBlue
                    }.getOrDefault(PrimaryBlue)

                    Box(
                        modifier = GlanceModifier
                            .size(10.dp)
                            .background(badgeColor)
                            .cornerRadius(5.dp)
                    ) {}
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = configuredPerson?.let { "Meds • ${it.name}" } ?: "Meds Reminder",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                if (nextDose != null) {
                    val timeFormatted = nextDose.scheduledTime.format(DateTimeFormatter.ofPattern("hh:mm a"))

                    // Dose Info Section (Clickable to open MainActivity)
                    Column(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .clickable(actionStartActivity<MainActivity>())
                    ) {
                        // Time and Snooze Tag
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = timeFormatted,
                                style = TextStyle(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GlanceTheme.colors.primary
                                )
                            )
                            if (nextDose.isSnoozed) {
                                Spacer(modifier = GlanceModifier.width(6.dp))
                                Text(
                                    text = "⏱ Pospuesto",
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = GlanceTheme.colors.error
                                    )
                                )
                            }
                        }

                        // Group Name & Person Label (if multi-person view)
                        val labelText = if (configuredPerson == null) {
                            "${nextDose.groupName} (${nextDose.personName})"
                        } else {
                            nextDose.groupName
                        }

                        Text(
                            text = labelText,
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = GlanceTheme.colors.onSurface
                            ),
                            maxLines = 1
                        )

                        // Medications list
                        if (nextDose.medications.isNotEmpty()) {
                            Text(
                                text = nextDose.medications.joinToString(", "),
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    color = GlanceTheme.colors.onSurfaceVariant
                                ),
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    // Interactive Action Buttons
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            text = "✓ Tomar",
                            onClick = actionRunCallback<ConfirmIntakeActionCallback>(
                                actionParametersOf(WidgetConstants.PARAM_GROUP_ID to nextDose.groupId)
                            ),
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Button(
                            text = "⏱ 15m",
                            onClick = actionRunCallback<SnoozeActionCallback>(
                                actionParametersOf(WidgetConstants.PARAM_GROUP_ID to nextDose.groupId)
                            ),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = GlanceTheme.colors.secondaryContainer,
                                contentColor = GlanceTheme.colors.onSecondaryContainer
                            ),
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                } else {
                    // Empty State: All completed for today
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .clickable(actionStartActivity<MainActivity>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "✓",
                                style = TextStyle(
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GlanceTheme.colors.primary
                                )
                            )
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            Text(
                                text = "¡Todo al día por hoy!",
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GlanceTheme.colors.onSurface
                                )
                            )
                            Text(
                                text = if (configuredPerson != null) {
                                    "Sin tomas pendientes para ${configuredPerson.name}"
                                } else {
                                    "No hay recordatorios pendientes"
                                },
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    color = GlanceTheme.colors.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
