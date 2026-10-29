package com.real3rr0r303yt.jarvis.trigger

import android.content.Context
import com.real3rr0r303yt.jarvis.core.JarvisLog
import com.real3rr0r303yt.jarvis.core.JarvisPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Watches which app is in the foreground (fed by the accessibility service's
 * TYPE_WINDOW_STATE_CHANGED events) and fires a proactive [handler] when a
 * user-configured package appears.
 *
 * Re-entry is debounced per package so re-opening or in-app navigation does not
 * re-fire within [DEBOUNCE_MS]. The [handler] is installed by JarvisApplication
 * and typically runs the content agent and shows a notification.
 */
object AppTriggerManager {

    private const val DEBOUNCE_MS = 60_000L

    /** Friendly labels for the common presets; falls back to the package name. */
    val PRESETS = linkedMapOf(
        "com.zhiliaoapp.musically" to "TikTok",
        "com.instagram.android" to "Instagram",
        "com.google.android.youtube" to "YouTube",
    )

    /** Installed by the app graph: given a package name, do the proactive work. */
    @Volatile
    var handler: (suspend (pkg: String) -> Unit)? = null

    private val scope = CoroutineScope(SupervisorJob())
    private val lastFired = HashMap<String, Long>()
    private var lastPackage: String? = null

    fun label(pkg: String): String = PRESETS[pkg] ?: pkg

    /** Called for every foreground window change; cheap and side-effect-free unless a trigger matches. */
    fun onForegroundApp(context: Context, pkg: String?) {
        if (pkg.isNullOrBlank() || pkg == lastPackage) return
        lastPackage = pkg

        val triggers = JarvisPrefs.get(context).appTriggers.value
        if (pkg !in triggers) return

        val now = System.currentTimeMillis()
        val last = lastFired[pkg] ?: 0L
        if (now - last < DEBOUNCE_MS) return
        lastFired[pkg] = now

        val fire = handler
        if (fire == null) {
            JarvisLog.warn("APP TRIGGER ${label(pkg)} matched but no handler is set")
            return
        }
        JarvisLog.action("APP TRIGGER fired: ${label(pkg)} ($pkg)")
        scope.launch { runCatching { fire(pkg) }.onFailure { JarvisLog.error("APP TRIGGER failed: ${it.message}") } }
    }
}
