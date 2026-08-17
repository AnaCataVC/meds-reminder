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
}
