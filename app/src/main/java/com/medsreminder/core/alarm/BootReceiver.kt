package com.medsreminder.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.medsreminder.domain.scheduler.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Re-schedules all active medication alarms whenever the device finishes booting or the time changes.
 */
class BootReceiver : BroadcastReceiver(), KoinComponent {

    private val alarmScheduler: AlarmScheduler by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val validActions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )

        if (intent.action in validActions) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    alarmScheduler.rescheduleAllActive()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
