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
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.medsreminder.MainActivity
import com.medsreminder.core.widget.WidgetScheduleItem
import com.medsreminder.core.widget.WidgetScheduleResolver
import com.medsreminder.data.local.dao.MedicationGroupDao
import com.medsreminder.data.local.dao.PersonDao
import com.medsreminder.data.local.entity.PersonEntity
import com.medsreminder.ui.theme.PrimaryBlue
import com.medsreminder.ui.widget.action.ConfirmIntakeActionCallback
import kotlinx.coroutines.flow.emptyFlow
import org.koin.core.context.GlobalContext
import java.time.format.DateTimeFormatter

/**
 * Expanded home screen list widget (4x3 / 4x2) displaying today's complete medication schedule.
 */
class DailyScheduleWidget : GlanceAppWidget() {

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

            val dailyItems = remember(groups, persons, selectedPersonId) {
                WidgetScheduleResolver.resolveDailySchedule(
                    groupsWithMeds = groups,
                    persons = persons,
                    selectedPersonId = selectedPersonId
                )
            }
            val configuredPerson = remember(persons, selectedPersonId) {
                selectedPersonId?.let { pid -> persons.find { it.id == pid } }
            }

            GlanceTheme {
                DailyScheduleContent(
                    items = dailyItems,
                    configuredPerson = configuredPerson
                )
            }
        }
    }

    @Composable
    private fun DailyScheduleContent(
        items: List<WidgetScheduleItem>,
        configuredPerson: PersonEntity?
    ) {
        val completedCount = items.count { it.isTakenToday }
        val totalCount = items.size

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(16.dp)
                .padding(16.dp)
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize()
            ) {
                // Header Row
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (configuredPerson != null) {
                        val personColor = runCatching {
                            Color(AndroidColor.parseColor(configuredPerson.colorHex))
                        }.getOrDefault(PrimaryBlue)

                        Box(
                            modifier = GlanceModifier
                                .size(10.dp)
                                .background(personColor)
                                .cornerRadius(5.dp)
                        ) {}
                        Spacer(modifier = GlanceModifier.width(8.dp))
                    }

                    val title = configuredPerson?.let { "Tomas de ${it.name}" } ?: "Tomas de Hoy"
                    Text(
                        text = title,
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.onSurface
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )

                    if (totalCount > 0) {
                        Text(
                            text = "$completedCount/$totalCount",
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = GlanceTheme.colors.primary
                            )
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(12.dp))

                if (items.isEmpty()) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .clickable(actionStartActivity<MainActivity>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (configuredPerson != null) {
                                "Sin horarios para ${configuredPerson.name} hoy"
                            } else {
                                "Sin horarios programados para hoy"
                            },
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = GlanceTheme.colors.onSurfaceVariant
                            )
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                    ) {
                        items(
                            items = items,
                            itemId = { it.groupId }
                        ) { item ->
                            ScheduleItemRow(item = item, showPersonBadge = configuredPerson == null)
                            Spacer(modifier = GlanceModifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ScheduleItemRow(
        item: WidgetScheduleItem,
        showPersonBadge: Boolean
    ) {
        val timeFormatted = item.scheduledTime.format(DateTimeFormatter.ofPattern("hh:mm a"))
        val isDone = item.isTakenToday

        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(12.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showPersonBadge) {
                    val badgeColor = runCatching {
                        Color(AndroidColor.parseColor(item.personColorHex))
                    }.getOrDefault(PrimaryBlue)

                    Box(
                        modifier = GlanceModifier
                            .size(10.dp)
                            .background(badgeColor)
                            .cornerRadius(5.dp)
                    ) {}
                    Spacer(modifier = GlanceModifier.width(8.dp))
                }

                Column(
                    modifier = GlanceModifier.defaultWeight()
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = timeFormatted,
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDone) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.primary,
                                textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                            )
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Text(
                            text = item.groupName,
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDone) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface
                            ),
                            maxLines = 1
                        )
                    }

                    if (item.medications.isNotEmpty()) {
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = item.medications.joinToString(", "),
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = GlanceTheme.colors.onSurfaceVariant
                            ),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.width(10.dp))

                if (isDone) {
                    Text(
                        text = "✓ Listo",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.primary
                        )
                    )
                } else {
                    Button(
                        text = "✓ Tomar",
                        onClick = actionRunCallback<ConfirmIntakeActionCallback>(
                            actionParametersOf(WidgetConstants.PARAM_GROUP_ID to item.groupId)
                        )
                    )
                }
            }
        }
    }
}
