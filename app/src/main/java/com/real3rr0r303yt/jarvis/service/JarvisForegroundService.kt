package com.real3rr0r303yt.jarvis.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.real3rr0r303yt.jarvis.MainActivity
import com.real3rr0r303yt.jarvis.R
import com.real3rr0r303yt.jarvis.core.JarvisLog
import com.real3rr0r303yt.jarvis.core.JarvisPrefs
import com.real3rr0r303yt.jarvis.trigger.AudioTriggerManager
import com.real3rr0r303yt.jarvis.trigger.TriggerNotifier

/**
 * Keeps Jarvis alive in the background by running as a foreground service with
 * a persistent status-bar notification ("JARVIS Active — System Monitoring").
 * Android will not silently kill the process while this runs.
 *
 * The notification is ongoing and low-importance so it does not nag; tapping it
 * reopens the HUD. Start/stop is driven by the user from the dashboard.
 */
class JarvisForegroundService : Service() {

    private var audio: AudioTriggerManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startInForeground()
        }
        return START_STICKY
    }

    private fun startInForeground() {
        createChannel()
        val notification = buildNotification()
        val audioOn = JarvisPrefs.get(this).audioTriggerEnabled

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            if (audioOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        JarvisLog.info("Foreground service started — background monitoring active")

        if (audioOn) startAudioTrigger()
    }

    private fun startAudioTrigger() {
        val manager = AudioTriggerManager(
            context = this,
            onActivity = {
                TriggerNotifier.show(this, "Jarvis · audio event", "Detected audio activity nearby.")
            },
        )
        if (manager.hasPermission()) {
            manager.start()
            audio = manager
        } else {
            JarvisLog.warn("AUDIO TRIGGER enabled but RECORD_AUDIO not granted — skipping")
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, JarvisForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS Active")
            .setContentText("System Monitoring")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(0, "STOP", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Jarvis Monitoring",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Keeps Jarvis-AI running in the background." }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audio?.stop()
        audio = null
        JarvisLog.info("Foreground service stopped")
    }

    companion object {
        private const val CHANNEL_ID = "jarvis_monitoring"
        private const val NOTIFICATION_ID = 7001
        const val ACTION_STOP = "com.real3rr0r303yt.jarvis.STOP_FOREGROUND"

        fun start(context: Context) {
            val intent = Intent(context, JarvisForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, JarvisForegroundService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
