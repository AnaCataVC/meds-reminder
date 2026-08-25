package com.medsreminder.core.alarm

import com.medsreminder.data.local.dao.MedicationGroupDao
import com.medsreminder.data.local.entity.MedicationGroupEntity
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class AlarmCalculationTest {

    private val mockAlarmManager = mockk<android.app.AlarmManager>(relaxed = true)
    private val mockContext = mockk<android.content.Context>(relaxed = true) {
        io.mockk.every { getSystemService(android.content.Context.ALARM_SERVICE) } returns mockAlarmManager
    }
    private val mockGroupDao = mockk<MedicationGroupDao>(relaxed = true)
    private val scheduler = AndroidAlarmScheduler(mockContext, mockGroupDao)

    @Test
    fun `calculateNextTriggerTime schedules for same day if target time is in future`() {
        // Given reference time is 08:00 AM on Monday (2026-08-17)
        val referenceNow = LocalDateTime.of(2026, 8, 17, 8, 0)
        val targetTime = LocalTime.of(14, 30) // 02:30 PM
        val group = MedicationGroupEntity(
            id = 1,
            personId = 1,
            name = "Afternoon Dose",
            scheduledTime = targetTime,
            daysOfWeekMask = 127 // Everyday
        )

        val triggerEpoch = scheduler.calculateNextTriggerTime(group, referenceNow)
        val expectedEpoch = LocalDateTime.of(2026, 8, 17, 14, 30)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        assertEquals(expectedEpoch, triggerEpoch)
    }

    @Test
    fun `calculateNextTriggerTime rolls over to tomorrow if target time already passed today`() {
        // Given reference time is 18:00 PM on Monday (2026-08-17)
        val referenceNow = LocalDateTime.of(2026, 8, 17, 18, 0)
        val targetTime = LocalTime.of(8, 0) // 08:00 AM
        val group = MedicationGroupEntity(
            id = 1,
            personId = 1,
            name = "Morning Dose",
            scheduledTime = targetTime,
            daysOfWeekMask = 127 // Everyday
        )

        val triggerEpoch = scheduler.calculateNextTriggerTime(group, referenceNow)
        val expectedEpoch = LocalDateTime.of(2026, 8, 18, 8, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        assertEquals(expectedEpoch, triggerEpoch)
        assertTrue(triggerEpoch > referenceNow.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }

    @Test
    fun `pre-alarm offset calculation matches advance notice minutes`() {
        val referenceNow = LocalDateTime.of(2026, 8, 17, 8, 0)
        val targetTime = LocalTime.of(14, 0) // 02:00 PM
        val group = MedicationGroupEntity(
            id = 1,
            personId = 1,
            name = "Afternoon Dose",
            scheduledTime = targetTime,
            daysOfWeekMask = 127,
            advanceNoticeMinutes = 15
        )

        val triggerEpoch = scheduler.calculateNextTriggerTime(group, referenceNow)
        val preAlarmEpoch = triggerEpoch - (group.advanceNoticeMinutes * 60 * 1000L)

        val expectedPreAlarmEpoch = LocalDateTime.of(2026, 8, 17, 13, 45)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        assertEquals(expectedPreAlarmEpoch, preAlarmEpoch)
    }

    @Test
    fun `calculateNextTriggerTime rolls over to tomorrow if medication was already taken today before scheduled time`() {
        // Reference time is 08:00 AM on Monday (2026-08-17), scheduled time is 14:00 (in the future today)
        val referenceNow = LocalDateTime.of(2026, 8, 17, 8, 0)
        val targetTime = LocalTime.of(14, 0) // 02:00 PM
        val group = MedicationGroupEntity(
            id = 1,
            personId = 1,
            name = "Afternoon Dose",
            scheduledTime = targetTime,
            daysOfWeekMask = 127, // Everyday
            lastTakenDate = java.time.LocalDate.of(2026, 8, 17) // Already taken today!
        )

        val triggerEpoch = scheduler.calculateNextTriggerTime(group, referenceNow)
        // Must be scheduled for tomorrow (2026-08-18 14:00), not today
        val expectedEpoch = LocalDateTime.of(2026, 8, 18, 14, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        assertEquals(expectedEpoch, triggerEpoch)
    }

    @Test
    fun `calculateNextTriggerTime respects dayOfWeek mask when taken today on selective days`() {
        // Reference time is Monday morning (2026-08-17). Group is only scheduled for Mon(1), Wed(4) -> mask = 1 + 4 = 5
        val referenceNow = LocalDateTime.of(2026, 8, 17, 9, 0)
        val targetTime = LocalTime.of(12, 0)
        val monWedMask = 1 or (1 shl (java.time.DayOfWeek.WEDNESDAY.value - 1))
        val group = MedicationGroupEntity(
            id = 2,
            personId = 1,
            name = "Mon-Wed Dose",
            scheduledTime = targetTime,
            daysOfWeekMask = monWedMask,
            lastTakenDate = java.time.LocalDate.of(2026, 8, 17) // Taken Monday early
        )

        val triggerEpoch = scheduler.calculateNextTriggerTime(group, referenceNow)
        // Next occurrence should be Wednesday (2026-08-19 12:00)
        val expectedEpoch = LocalDateTime.of(2026, 8, 19, 12, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        assertEquals(expectedEpoch, triggerEpoch)
    }
}
