package com.getmaincourse.app.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.getmaincourse.app.MainActivity
import com.getmaincourse.app.R
import java.util.concurrent.atomic.AtomicInteger

object NotificationPresenter {
    const val REMINDERS_CHANNEL = "recipe_reminders"
    const val ACTIVITY_CHANNEL = "cookbook_activity"

    private val nextId = AtomicInteger(1)

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    REMINDERS_CHANNEL,
                    context.getString(R.string.notification_channel_reminders),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    ACTIVITY_CHANNEL,
                    context.getString(R.string.notification_channel_activity),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            ),
        )
    }

    fun show(context: Context, title: String, body: String, data: Map<String, String>) {
        if (NotificationDestination.from(data) == null) return

        val channel = if (data.containsKey("campaign")) REMINDERS_CHANNEL else ACTIVITY_CHANNEL
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data.forEach { (key, value) -> putExtra(key, value) }
        }
        val requestCode = nextId.getAndIncrement()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(requestCode, notification)
    }
}
