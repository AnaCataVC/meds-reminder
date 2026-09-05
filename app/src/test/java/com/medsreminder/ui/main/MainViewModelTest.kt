package com.medsreminder.ui.main

import android.content.Context
import app.cash.turbine.test
import com.medsreminder.core.alarm.AndroidAlarmScheduler
import com.medsreminder.core.notification.NotificationHelper
import com.medsreminder.data.backup.BackupManager
import com.medsreminder.data.local.dao.MedicationDao
import com.medsreminder.data.local.dao.MedicationGroupDao
import com.medsreminder.data.local.dao.PersonDao
import com.medsreminder.data.local.entity.PersonEntity
import com.medsreminder.domain.repository.MedicationScheduleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockPersonDao = mockk<PersonDao>(relaxed = true)
    private val mockMedicationDao = mockk<MedicationDao>(relaxed = true)
    private val mockGroupDao = mockk<MedicationGroupDao>(relaxed = true)
    private val mockAlarmScheduler = mockk<AndroidAlarmScheduler>(relaxed = true)
    private val mockNotificationHelper = mockk<NotificationHelper>(relaxed = true)
    private val mockBackupManager = mockk<BackupManager>(relaxed = true)
    private val mockScheduleRepository = mockk<MedicationScheduleRepository>(relaxed = true)

    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { mockPersonDao.getAllPersons() } returns flowOf(
            listOf(PersonEntity(id = 1, name = "Ana", colorHex = "#FF0000"))
        )
        every { mockMedicationDao.getAllMedications() } returns flowOf(emptyList())
        every { mockGroupDao.getAllGroupsWithMedications() } returns flowOf(emptyList())

        viewModel = MainViewModel(
            context = mockContext,
            personDao = mockPersonDao,
            medicationDao = mockMedicationDao,
            groupDao = mockGroupDao,
            alarmScheduler = mockAlarmScheduler,
            notificationHelper = mockNotificationHelper,
            backupManager = mockBackupManager,
            scheduleRepository = mockScheduleRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState emits initial loaded state with persons`() = runTest {
        viewModel.uiState.test {
            val item = awaitItem()
            testDispatcher.scheduler.advanceUntilIdle()
            val loadedItem = if (item.isLoading) awaitItem() else item

            assertEquals(1, loadedItem.persons.size)
            assertEquals("Ana", loadedItem.persons.first().name)
        }
    }

    @Test
    fun `confirm intake intent delegates to scheduleRepository`() = runTest {
        val today = LocalDate.now()
        viewModel.onIntent(MainUiIntent.ConfirmIntakeToday(groupId = 42L))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockScheduleRepository.confirmIntake(42L, today) }
    }

    @Test
    fun `snooze intent delegates to scheduleRepository with calculated future epoch`() = runTest {
        val beforeMs = System.currentTimeMillis() + (10 * 60 * 1000L) - 500
        viewModel.onIntent(MainUiIntent.SnoozeGroup(groupId = 42L, minutes = 10))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            mockScheduleRepository.snoozeSchedule(
                eq(42L),
                match { it >= beforeMs }
            )
        }
    }

    @Test
    fun `savePerson inserts when id is zero`() = runTest {
        viewModel.onIntent(MainUiIntent.SavePerson(id = 0L, name = "Carlos", colorHex = "#00FF00"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            mockPersonDao.insertPerson(match { it.name == "Carlos" && it.colorHex == "#00FF00" })
        }
    }

    @Test
    fun `suspendPerson sets suspension timestamp in database`() = runTest {
        viewModel.onIntent(MainUiIntent.SuspendPerson(personId = 1L, hours = 2))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            mockPersonDao.setSuspendedUntil(1L, match { (it ?: 0L) > System.currentTimeMillis() })
        }
    }

    @Test
    fun `resumePerson clears suspension timestamp in database`() = runTest {
        viewModel.onIntent(MainUiIntent.ResumePerson(personId = 1L))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            mockPersonDao.setSuspendedUntil(1L, null)
        }
    }
}
