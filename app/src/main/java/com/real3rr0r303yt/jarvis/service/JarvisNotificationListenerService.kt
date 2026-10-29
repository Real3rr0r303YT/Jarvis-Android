package com.real3rr0r303yt.jarvis.service

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.real3rr0r303yt.jarvis.core.JarvisLog

/**
 * Reads posted notifications and, when asked, sends an inline reply using the
 * notification's own RemoteInput action (the same mechanism the system uses for
 * "Reply" from the shade). Enabled only via Settings > Notification access.
 *
 * It does not forward notification content anywhere off-device; it simply makes
 * the latest notifications available to Jarvis and can reply on the user's
 * explicit instruction.
 */
class JarvisNotificationListenerService : NotificationListenerService() {

    companion object {
        @Volatile
        var instance: JarvisNotificationListenerService? = null
            private set

        val isConnected: Boolean get() = instance != null
    }

    /** Most recent notification per package, for quick lookup / reply. */
    private val latest = LinkedHashMap<String, StatusBarNotification>()

    override fun onListenerConnected() {
        instance = this
        JarvisLog.info("NotificationListener connected")
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        JarvisLog.info("NotificationListener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        latest[sbn.packageName] = sbn
        val title = sbn.notification?.extras?.getCharSequence(Notification.EXTRA_TITLE)
        JarvisLog.info("NOTIFY ${sbn.packageName} :: ${title ?: "(no title)"}")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let { latest.remove(it.packageName) }
    }

    /**
     * Sends [reply] to the most recent notification from a package matching
     * [packageQuery], using that notification's inline-reply action.
     * Returns true if a reply action was found and fired.
     */
    fun reply(packageQuery: String, reply: String): Boolean {
        val sbn = latest.entries
            .firstOrNull { it.key.contains(packageQuery, ignoreCase = true) }
            ?.value
            ?: run {
                JarvisLog.error("REPLY — no active notification for \"$packageQuery\"")
                return false
            }

        val action = sbn.notification?.actions?.firstOrNull { act ->
            act.remoteInputs?.isNotEmpty() == true
        } ?: run {
            JarvisLog.error("REPLY — \"$packageQuery\" notification has no reply action")
            return false
        }

        val remoteInputs = action.remoteInputs ?: return false
        val intent = Intent()
        val bundle = android.os.Bundle()
        remoteInputs.forEach { ri -> bundle.putCharSequence(ri.resultKey, reply) }
        RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)

        return try {
            action.actionIntent.send(this as Context, 0, intent)
            JarvisLog.action("REPLY ${sbn.packageName} :: \"${reply.take(40)}\"")
            true
        } catch (e: Exception) {
            JarvisLog.error("REPLY failed: ${e.message}")
            false
        }
    }
}
