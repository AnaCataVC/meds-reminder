package com.medsreminder.ui.alarm

import app.cash.turbine.test
import com.medsreminder.core.notification.NotificationHelper
import com.medsreminder.data.local.dao.MedicationGroupDao
import com.medsreminder.data.local.dao.PersonDao
import com.medsreminder.data.local.entity.MedicationGroupEntity
import com.medsreminder.data.local.entity.MedicationGroupWithMedications
import com.medsreminder.data.local.entity.PersonEntity
import com.medsreminder.domain.repository.MedicationScheduleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mockGroupDao = mockk<MedicationGroupDao>(relaxed = true)
    private val mockPersonDao = mockk<PersonDao>(relaxed = true)
    private val mockScheduleRepository = mockk<MedicationScheduleRepository>(relaxed = true)
    private val mockNotificationHelper = mockk<NotificationHelper>(relaxed = true)

    private lateinit var viewModel: AlarmViewModel

    private val samplePerson = PersonEntity(id = 1L, name = "Carlos", colorHex = "#0061A4")
    private val sampleGroup = MedicationGroupEntity(
        id = 10L,
        personId = 1L,
        name = "Matutino",
        scheduledTime = LocalTime.of(8, 0),
        daysOfWeekMask = 127
    )
    private val sampleGroupWithMeds = MedicationGroupWithMedications(
        group = sampleGroup,
        medications = emptyList()
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        coEvery { mockGroupDao.getGroupById(10L) } returns sampleGroupWithMeds
        every { mockPersonDao.getPersonById(1L) } returns flowOf(samplePerson)

        viewModel = AlarmViewModel(
            groupDao = mockGroupDao,
            personDao = mockPersonDao,
            scheduleRepository = mockScheduleRepository,
            notificationHelper = mockNotificationHelper
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadAlarm sets group and person in uiState`() = runTest {
        viewModel.loadAlarm(10L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertFalse(state.isFinished)
            assertEquals(sampleGroupWithMeds, state.groupWithMeds)
            assertEquals(samplePerson, state.person)
        }
    }

    @Test
    fun `confirmTaken immediately cancels all notifications and silences audio`() = runTest {
        viewModel.loadAlarm(10L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.confirmTaken()

        // Notification and audio cancel MUST happen synchronously before async work finishes
        verify(exactly = 1) { mockNotificationHelper.cancelAllForGroup(10L) }

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockScheduleRepository.confirmIntake(10L, any()) }
        assertTrue(viewModel.uiState.value.isFinished)
    }

    @Test
    fun `snooze10Min immediately cancels all notifications and silences audio`() = runTest {
        viewModel.loadAlarm(10L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.snooze10Min()

        verify(exactly = 1) { mockNotificationHelper.cancelAllForGroup(10L) }

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockScheduleRepository.snoozeSchedule(10L, any()) }
        assertTrue(viewModel.uiState.value.isFinished)
    }

    @Test
    fun `cancelToday immediately cancels all notifications and silences audio`() = runTest {
        viewModel.loadAlarm(10L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.cancelToday()

        verify(exactly = 1) { mockNotificationHelper.cancelAllForGroup(10L) }

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockScheduleRepository.skipSchedule(10L, any()) }
        assertTrue(viewModel.uiState.value.isFinished)
    }
}
