package com.medsreminder.core.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.medsreminder.MainActivity
import com.medsreminder.R
import com.medsreminder.core.alarm.NotificationActionReceiver
import com.medsreminder.data.local.entity.MedicationGroupWithMedications

/**
 * Handles creation and presentation of notifications with dynamic sound channels.
 */
class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val DEFAULT_CHANNEL_ID = "meds_reminder_alarm_channel_v2"
        const val ACTION_CONFIRM = "com.medsreminder.ACTION_CONFIRM"
        const val ACTION_SNOOZE_10 = "com.medsreminder.ACTION_SNOOZE_10"
        const val ACTION_POSTPONE_6H = "com.medsreminder.ACTION_POSTPONE_6H"
        const val ACTION_CANCEL_TODAY = "com.medsreminder.ACTION_CANCEL_TODAY"

        const val EXTRA_GROUP_ID = "extra_group_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    /**
     * Resolves or dynamically registers a NotificationChannel configured with the given custom ringtone URI.
     */
    fun getOrCreateChannelForSound(ringtoneUriString: String?): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return DEFAULT_CHANNEL_ID

        val channelId = if (ringtoneUriString.isNullOrBlank()) {
            DEFAULT_CHANNEL_ID
        } else {
            "meds_channel_tone_${ringtoneUriString.hashCode()}"
        }

        if (notificationManager.getNotificationChannel(channelId) == null) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val soundUri = ringtoneUriString?.let { Uri.parse(it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val channelName = if (ringtoneUriString.isNullOrBlank()) {
                "Recordatorios de Medicamentos"
            } else {
                "Recordatorios Personalizados"
            }

            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarmas de recordatorio de tomas médicas"
                setSound(soundUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        return channelId
    }

    /**
     * Builds and presents an ongoing, interactive medication notification.
     */
    fun showMedicationNotification(
        groupWithMeds: MedicationGroupWithMedications,
        personName: String
    ) {
        val group = groupWithMeds.group
        val channelId = getOrCreateChannelForSound(group.ringtoneUriString)
        val notificationId = group.id.toInt()

        val medListSummary = if (groupWithMeds.medications.isEmpty()) {
            "Sin medicamentos asignados"
        } else {
            groupWithMeds.medications.joinToString(", ") {
                "${it.name}${it.dosage?.let { d -> " ($d)" } ?: ""}"
            }
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action PendingIntents
        val confirmIntent = createActionPendingIntent(ACTION_CONFIRM, group.id, notificationId, 1)
        val snoozeIntent = createActionPendingIntent(ACTION_SNOOZE_10, group.id, notificationId, 2)
        val postponeIntent = createActionPendingIntent(ACTION_POSTPONE_6H, group.id, notificationId, 3)
        val cancelIntent = createActionPendingIntent(ACTION_CANCEL_TODAY, group.id, notificationId, 4)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_medication)
            .setContentTitle("⏰ Hora de tomar: ${group.name}")
            .setContentText("Para $personName: $medListSummary")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Para $personName:\n$medListSummary\n\nHora programada: ${group.scheduledTime}"
                )
            )
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true) // Prevent accidental swipe until user takes action
            .addAction(R.drawable.ic_check, "✅ Tomado", confirmIntent)
            .addAction(R.drawable.ic_snooze, "⏳ 10 min", snoozeIntent)
            .addAction(R.drawable.ic_schedule, "🕒 +6h", postponeIntent)
            .addAction(R.drawable.ic_close, "❌ Cancelar hoy", cancelIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    private fun createActionPendingIntent(
        action: String,
        groupId: Long,
        notificationId: Int,
        suffix: Int
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_GROUP_ID, groupId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val requestCode = (groupId * 10 + suffix).toInt()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
