package com.medsreminder.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
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
import androidx.glance.Button
import com.medsreminder.MainActivity
import com.medsreminder.core.widget.WidgetScheduleItem
import com.medsreminder.core.widget.WidgetScheduleResolver
import com.medsreminder.data.local.dao.MedicationGroupDao
import com.medsreminder.data.local.dao.PersonDao
import com.medsreminder.data.local.entity.PersonEntity
import com.medsreminder.ui.widget.action.ConfirmIntakeActionCallback
import com.medsreminder.ui.widget.action.SnoozeActionCallback
import org.koin.core.context.GlobalContext
import java.time.format.DateTimeFormatter

/**
 * Compact home screen widget (2x2 / 3x2) displaying the immediate next upcoming or due dose.
 */
class NextDoseWidget : GlanceAppWidget() {

    override var stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        var selectedPersonId = prefs[WidgetConstants.PREF_SELECTED_PERSON_ID]?.takeIf { it > 0 }

        if (selectedPersonId == null) {
            val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrNull()
            if (appWidgetId != null && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val sp = context.getSharedPreferences("meds_widgets_prefs", Context.MODE_PRIVATE)
                val spPersonId = sp.getLong("widget_person_$appWidgetId", -1L)
                if (spPersonId > 0) {
                    selectedPersonId = spPersonId
                }
            }
        }

        // Fetch data from Room using Koin
        val koin = runCatching { GlobalContext.get() }.getOrNull()
        val groupDao: MedicationGroupDao? = koin?.getOrNull()
        val personDao: PersonDao? = koin?.getOrNull()

        val activeGroups = groupDao?.getAllActiveGroupsSync() ?: emptyList()
        val allPersons = personDao?.getAllPersonsList() ?: emptyList()

        val nextDose = WidgetScheduleResolver.resolveNextDose(
            groupsWithMeds = activeGroups,
            persons = allPersons,
            selectedPersonId = selectedPersonId
        )
        val configuredPerson = selectedPersonId?.let { pid -> allPersons.find { it.id == pid } }

        provideContent {
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
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize()
            ) {
                // Header Row
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val badgeColor = runCatching {
                        Color(AndroidColor.parseColor(configuredPerson?.colorHex ?: nextDose?.personColorHex ?: "#0061A4"))
                    }.getOrDefault(Color(0xFF0061A4))

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
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                } else {
                    // Empty State: All completed for today
                    Box(
                        modifier = GlanceModifier.fillMaxSize(),
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
