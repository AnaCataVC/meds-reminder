package com.medsreminder.data.repository

import com.medsreminder.core.notification.NotificationHelper
import com.medsreminder.data.local.dao.MedicationGroupDao
import com.medsreminder.data.local.entity.MedicationGroupEntity
import com.medsreminder.data.local.entity.MedicationGroupWithMedications
import com.medsreminder.domain.scheduler.AlarmScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class MedicationScheduleRepositoryTest {

    private val mockGroupDao = mockk<MedicationGroupDao>(relaxed = true)
    private val mockAlarmScheduler = mockk<AlarmScheduler>(relaxed = true)
    private val mockNotificationHelper = mockk<NotificationHelper>(relaxed = true)

    private lateinit var repository: MedicationScheduleRepositoryImpl

    private val sampleGroup = MedicationGroupEntity(
        id = 5L,
        personId = 1L,
        name = "Vespertino",
        scheduledTime = LocalTime.of(14, 0),
        daysOfWeekMask = 127
    )
    private val sampleGroupWithMeds = MedicationGroupWithMedications(
        group = sampleGroup,
        medications = emptyList()
    )

    @Before
    fun setUp() {
        coEvery { mockGroupDao.getGroupById(5L) } returns sampleGroupWithMeds

        repository = MedicationScheduleRepositoryImpl(
            groupDao = mockGroupDao,
            alarmScheduler = mockAlarmScheduler,
            notificationHelper = mockNotificationHelper
        )
    }

    @Test
    fun `confirmIntake dismisses notifications and silences audio before updating Room`() = runTest {
        val today = LocalDate.now()

        repository.confirmIntake(5L, today, notificationId = 1005)

        verify(exactly = 1) { mockNotificationHelper.cancelAllForGroup(5L, 1005) }
        verify(exactly = 1) { mockAlarmScheduler.cancelRingTimeout(5L) }
        coVerify(exactly = 1) { mockGroupDao.markGroupAsTaken(5L, today) }
        verify(exactly = 1) { mockAlarmScheduler.schedule(match { it.lastTakenDate == today && it.lastSkippedDate == null }) }
    }

    @Test
    fun `confirmPartialIntake records partial intake and reschedules alarm`() = runTest {
        val today = LocalDate.now()
        val takenIds = listOf(10L, 20L)
        val skippedIds = listOf(30L)

        repository.confirmPartialIntake(5L, today, takenIds, skippedIds, notificationId = 1005)

        verify(exactly = 1) { mockNotificationHelper.cancelAllForGroup(5L, 1005) }
        verify(exactly = 1) { mockAlarmScheduler.cancelRingTimeout(5L) }
        coVerify(exactly = 1) { mockGroupDao.recordPartialIntake(5L, today, takenIds, skippedIds) }
        verify(exactly = 1) { mockAlarmScheduler.schedule(match { it.lastTakenDate == today }) }
    }

    @Test
    fun `snoozeSchedule dismisses notifications and schedules snooze`() = runTest {
        val snoozeEpoch = System.currentTimeMillis() + 600_000L

        repository.snoozeSchedule(5L, snoozeEpoch, notificationId = 1005)

        verify(exactly = 1) { mockNotificationHelper.cancelAllForGroup(5L, 1005) }
        verify(exactly = 1) { mockAlarmScheduler.cancelRingTimeout(5L) }
        coVerify(exactly = 1) { mockGroupDao.setSnoozeTime(5L, snoozeEpoch) }
        verify(exactly = 1) { mockAlarmScheduler.scheduleSnooze(5L, snoozeEpoch) }
    }

    @Test
    fun `cancelSnooze clears snooze time, cancels notifications, and reschedules regular alarm`() = runTest {
        repository.cancelSnooze(5L)

        verify(exactly = 1) { mockNotificationHelper.cancelAllForGroup(5L) }
        verify(exactly = 1) { mockAlarmScheduler.cancelRingTimeout(5L) }
        coVerify(exactly = 1) { mockGroupDao.setSnoozeTime(5L, null) }
        verify(exactly = 1) { mockAlarmScheduler.schedule(match { it.snoozeUntilEpochMs == null }) }
    }

    @Test
    fun `skipSchedule dismisses notifications and marks group skipped`() = runTest {
        val today = LocalDate.now()

        repository.skipSchedule(5L, today, notificationId = 1005)

        verify(exactly = 1) { mockNotificationHelper.cancelAllForGroup(5L, 1005) }
        verify(exactly = 1) { mockAlarmScheduler.cancelRingTimeout(5L) }
        coVerify(exactly = 1) { mockGroupDao.markGroupSkippedToday(5L, today) }
        verify(exactly = 1) { mockAlarmScheduler.schedule(match { it.lastSkippedDate == today && it.lastTakenDate == null }) }
    }

    @Test
    fun `reactivateSchedule clears resolution in Room and reschedules alarm`() = runTest {
        repository.reactivateSchedule(5L)

        verify(exactly = 1) { mockNotificationHelper.cancelAllForGroup(5L) }
        verify(exactly = 1) { mockAlarmScheduler.cancelRingTimeout(5L) }
        coVerify(exactly = 1) { mockGroupDao.clearGroupResolutionToday(5L) }
        coVerify(exactly = 1) { mockGroupDao.clearCrossRefResolutions(5L) }
    }
}
