package com.real3rr0r303yt.jarvis.trigger

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.real3rr0r303yt.jarvis.MainActivity
import com.real3rr0r303yt.jarvis.R

/**
 * Surfaces proactive trigger output to the user as an expandable notification.
 * Tapping it opens the HUD. This is the least intrusive way to show agent
 * suggestions without requiring the draw-over-other-apps (overlay) permission.
 */
object TriggerNotifier {

    private const val CHANNEL_ID = "jarvis_triggers"
    private const val CHANNEL_NAME = "Jarvis Proactive Triggers"

    fun show(context: Context, title: String, body: String) {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body.lineSequence().firstOrNull() ?: body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompatSafe.notify(context, title.hashCode(), notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Proactive suggestions from Jarvis event triggers." }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

/** Posts a notification, swallowing the SecurityException when POST_NOTIFICATIONS is denied. */
private object NotificationManagerCompatSafe {
    fun notify(context: Context, id: Int, notification: android.app.Notification) {
        try {
            androidx.core.app.NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Notifications permission not granted; nothing to show.
        }
    }
}
