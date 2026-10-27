package com.medsreminder.core.widget

import com.medsreminder.data.local.entity.MedicationEntity
import com.medsreminder.data.local.entity.MedicationGroupEntity
import com.medsreminder.data.local.entity.MedicationGroupWithMedications
import com.medsreminder.data.local.entity.PersonEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class WidgetScheduleResolverTest {

    private val personMom = PersonEntity(id = 1L, name = "Mamá", colorHex = "#9C27B0")
    private val personDad = PersonEntity(id = 2L, name = "Papá", colorHex = "#2196F3")
    private val allPersons = listOf(personMom, personDad)

    private val medLosartan = MedicationEntity(id = 101L, name = "Losartán 50mg")
    private val medAspirin = MedicationEntity(id = 102L, name = "Aspirina 100mg")

    @Test
    fun `resolveDailySchedule filters by selectedPersonId correctly`() {
        // Monday 2026-10-26 at 07:00 AM
        val referenceNow = LocalDateTime.of(2026, 10, 26, 7, 0)

        val groupMom = MedicationGroupWithMedications(
            group = MedicationGroupEntity(
                id = 10L,
                personId = 1L,
                name = "Desayuno Mamá",
                scheduledTime = LocalTime.of(8, 0),
                daysOfWeekMask = 127
            ),
            medications = listOf(medLosartan)
        )

        val groupDad = MedicationGroupWithMedications(
            group = MedicationGroupEntity(
                id = 20L,
                personId = 2L,
                name = "Desayuno Papá",
                scheduledTime = LocalTime.of(8, 30),
                daysOfWeekMask = 127
            ),
            medications = listOf(medAspirin)
        )

        val allGroups = listOf(groupMom, groupDad)

        // When filtering for Mom
        val momSchedule = WidgetScheduleResolver.resolveDailySchedule(
            groupsWithMeds = allGroups,
            persons = allPersons,
            selectedPersonId = 1L,
            referenceNow = referenceNow
        )
        assertEquals(1, momSchedule.size)
        assertEquals("Desayuno Mamá", momSchedule[0].groupName)
        assertEquals("Mamá", momSchedule[0].personName)

        // When filtering for all (null or -1)
        val allSchedule = WidgetScheduleResolver.resolveDailySchedule(
            groupsWithMeds = allGroups,
            persons = allPersons,
            selectedPersonId = null,
            referenceNow = referenceNow
        )
        assertEquals(2, allSchedule.size)
    }

    @Test
    fun `resolveDailySchedule excludes inactive and wrong-day groups`() {
        // Monday 2026-10-26 (Monday is bit 1: mask = 1)
        val referenceNow = LocalDateTime.of(2026, 10, 26, 7, 0)

        val inactiveGroup = MedicationGroupWithMedications(
            group = MedicationGroupEntity(
                id = 1L,
                personId = 1L,
                name = "Inactive",
                scheduledTime = LocalTime.of(8, 0),
                isActive = false,
                daysOfWeekMask = 127
            ),
            medications = emptyList()
        )

        // Only on Sundays (bit 64)
        val sundayOnlyGroup = MedicationGroupWithMedications(
            group = MedicationGroupEntity(
                id = 2L,
                personId = 1L,
                name = "Sunday Only",
                scheduledTime = LocalTime.of(8, 0),
                isActive = true,
                daysOfWeekMask = 64
            ),
            medications = emptyList()
        )

        val result = WidgetScheduleResolver.resolveDailySchedule(
            groupsWithMeds = listOf(inactiveGroup, sundayOnlyGroup),
            persons = allPersons,
            selectedPersonId = null,
            referenceNow = referenceNow
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `resolveNextDose returns the immediate upcoming untaken dose`() {
        // 08:15 AM
        val referenceNow = LocalDateTime.of(2026, 10, 26, 8, 15)

        val doseMorning = MedicationGroupWithMedications(
            group = MedicationGroupEntity(
                id = 1L,
                personId = 1L,
                name = "Mañana",
                scheduledTime = LocalTime.of(8, 0),
                lastTakenDate = LocalDate.of(2026, 10, 26) // Already taken
            ),
            medications = listOf(medLosartan)
        )

        val doseNoon = MedicationGroupWithMedications(
            group = MedicationGroupEntity(
                id = 2L,
                personId = 1L,
                name = "Almuerzo",
                scheduledTime = LocalTime.of(13, 0)
            ),
            medications = listOf(medAspirin)
        )

        val nextDose = WidgetScheduleResolver.resolveNextDose(
            groupsWithMeds = listOf(doseMorning, doseNoon),
            persons = allPersons,
            selectedPersonId = null,
            referenceNow = referenceNow
        )

        assertNotNull(nextDose)
        assertEquals(2L, nextDose!!.groupId)
        assertEquals("Almuerzo", nextDose.groupName)
    }

    @Test
    fun `resolveNextDose prioritizes active snoozed dose over future doses`() {
        // Reference: 10:00 AM
        val referenceNow = LocalDateTime.of(2026, 10, 26, 10, 0)
        val snoozeEpoch = referenceNow.plusMinutes(15).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val snoozedDose = MedicationGroupWithMedications(
            group = MedicationGroupEntity(
                id = 1L,
                personId = 1L,
                name = "Dosis Pospuesta",
                scheduledTime = LocalTime.of(8, 0),
                snoozeUntilEpochMs = snoozeEpoch
            ),
            medications = listOf(medLosartan)
        )

        val futureDose = MedicationGroupWithMedications(
            group = MedicationGroupEntity(
                id = 2L,
                personId = 1L,
                name = "Dosis Noche",
                scheduledTime = LocalTime.of(21, 0)
            ),
            medications = listOf(medAspirin)
        )

        val nextDose = WidgetScheduleResolver.resolveNextDose(
            groupsWithMeds = listOf(futureDose, snoozedDose),
            persons = allPersons,
            selectedPersonId = null,
            referenceNow = referenceNow
        )

        assertNotNull(nextDose)
        assertEquals(1L, nextDose!!.groupId)
        assertTrue(nextDose.isSnoozed)
    }

    @Test
    fun `resolveNextDose returns null when all doses for today are taken`() {
        val referenceNow = LocalDateTime.of(2026, 10, 26, 15, 0)

        val doseTaken = MedicationGroupWithMedications(
            group = MedicationGroupEntity(
                id = 1L,
                personId = 1L,
                name = "Desayuno",
                scheduledTime = LocalTime.of(8, 0),
                lastTakenDate = LocalDate.of(2026, 10, 26)
            ),
            medications = listOf(medLosartan)
        )

        val nextDose = WidgetScheduleResolver.resolveNextDose(
            groupsWithMeds = listOf(doseTaken),
            persons = allPersons,
            selectedPersonId = null,
            referenceNow = referenceNow
        )

        assertNull(nextDose)
    }

    @Test
    fun `resolveNextDose prioritizes overdue untaken dose over upcoming future doses`() {
        // Current time: 10:00 AM (8:00 AM dose was missed and is still untaken)
        val referenceNow = LocalDateTime.of(2026, 10, 26, 10, 0)

        val overdueUntakenDose = MedicationGroupWithMedications(
            group = MedicationGroupEntity(
                id = 1L,
                personId = 1L,
                name = "Desayuno Pendiente",
                scheduledTime = LocalTime.of(8, 0),
                lastTakenDate = null
            ),
            medications = listOf(medLosartan)
        )

        val upcomingAfternoonDose = MedicationGroupWithMedications(
            group = MedicationGroupEntity(
                id = 2L,
                personId = 1L,
                name = "Almuerzo Futuro",
                scheduledTime = LocalTime.of(13, 0),
                lastTakenDate = null
            ),
            medications = listOf(medAspirin)
        )

        val nextDose = WidgetScheduleResolver.resolveNextDose(
            groupsWithMeds = listOf(upcomingAfternoonDose, overdueUntakenDose),
            persons = allPersons,
            selectedPersonId = null,
            referenceNow = referenceNow
        )

        assertNotNull(nextDose)
        assertEquals(1L, nextDose!!.groupId)
        assertEquals("Desayuno Pendiente", nextDose.groupName)
    }
}
