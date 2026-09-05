package com.medsreminder.di

import androidx.room.Room
import com.medsreminder.core.alarm.AndroidAlarmScheduler
import com.medsreminder.core.notification.NotificationHelper
import com.medsreminder.data.backup.BackupManager
import com.medsreminder.data.local.AppDatabase
import com.medsreminder.domain.scheduler.AlarmScheduler
import com.medsreminder.data.repository.MedicationScheduleRepositoryImpl
import com.medsreminder.domain.repository.MedicationScheduleRepository
import com.medsreminder.ui.alarm.AlarmViewModel
import com.medsreminder.ui.main.MainViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    // Room Database & DAOs
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).build()
    }

    single { get<AppDatabase>().personDao() }
    single { get<AppDatabase>().medicationDao() }
    single { get<AppDatabase>().medicationGroupDao() }

    // Alarm & Notification Services
    single { NotificationHelper(androidContext()) }
    single { AndroidAlarmScheduler(androidContext(), get()) }
    single<AlarmScheduler> { get<AndroidAlarmScheduler>() }

    // Schedule Domain Repository
    single<MedicationScheduleRepository> {
        MedicationScheduleRepositoryImpl(
            groupDao = get(),
            alarmScheduler = get(),
            notificationHelper = get()
        )
    }

    // Backup & Restore Manager
    single { BackupManager(androidContext(), get(), get()) }

    // ViewModels
    viewModel {
        MainViewModel(
            context = androidContext(),
            personDao = get(),
            medicationDao = get(),
            groupDao = get(),
            alarmScheduler = get(),
            notificationHelper = get(),
            backupManager = get(),
            scheduleRepository = get()
        )
    }

    viewModel {
        AlarmViewModel(
            groupDao = get(),
            personDao = get(),
            scheduleRepository = get()
        )
    }
}
