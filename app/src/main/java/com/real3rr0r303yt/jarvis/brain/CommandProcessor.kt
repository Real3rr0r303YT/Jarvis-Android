package com.real3rr0r303yt.jarvis.brain

import android.content.Context
import com.real3rr0r303yt.jarvis.brain.model.JarvisAction
import com.real3rr0r303yt.jarvis.core.JarvisLog
import com.real3rr0r303yt.jarvis.core.JarvisPrefs
import com.real3rr0r303yt.jarvis.service.JarvisAccessibilityService
import com.real3rr0r303yt.jarvis.system.ElevatedShell
import com.real3rr0r303yt.jarvis.system.SystemController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Jarvis' "brain": takes a natural-language line, parses it into actions, and
 * executes each one against the right subsystem. Returns a short, human-readable
 * summary for the HUD.
 */
class CommandProcessor(appContext: Context) {

    private val parser = IntentParser()
    private val system = SystemController(appContext)
    private val prefs = JarvisPrefs.get(appContext)

    /** Runs a saved custom shell command via the elevated channel, if enabled. */
    suspend fun runRawCommand(command: String): String {
        if (!ElevatedShell.isAvailable()) {
            JarvisLog.error("SHELL — no elevated channel (enable Shizuku/root first)")
            return "No elevated channel available"
        }
        JarvisLog.action("SHELL $ $command")
        val out = withContext(Dispatchers.IO) { ElevatedShell.exec(command) }
        if (out.isNotBlank()) JarvisLog.info(out.take(200))
        return "Command executed"
    }

    /** Parses and runs [input]. Suspends because screen automation needs pacing. */
    suspend fun process(input: String): String {
        val parsed = parser.parse(input)
        JarvisLog.info("PARSE \"$input\" -> ${parsed.actions.size} action(s)")

        var ok = 0
        for (action in parsed.actions) {
            if (execute(action)) ok++
            delay(300) // let the UI/screen settle between chained actions
        }
        return "Executed $ok/${parsed.actions.size} action(s)"
    }

    private suspend fun execute(action: JarvisAction): Boolean = when (action) {
        is JarvisAction.SetWifi -> run { system.setWifi(action.enabled); true }
        is JarvisAction.SetBluetooth -> runCatchingPermission { system.setBluetooth(action.enabled) }
        is JarvisAction.SetVolume -> run { system.setVolume(action.stream, action.percent); true }
        is JarvisAction.SetBrightness -> run { system.setBrightness(action.percent); true }
        is JarvisAction.SetPowerSaver -> run { system.setPowerSaver(action.enabled); true }
        is JarvisAction.LaunchApp ->
            if (allowed(action.query)) system.launchApp(action.query) else blocked(action.query)

        is JarvisAction.ComposeMessage -> {
            if (action.appQuery.isNotBlank() && !allowed(action.appQuery)) return blocked(action.appQuery)
            // Open the target app, wait for it, then type and leave sending to
            // the user (we type the text; we do not auto-fire the send button
            // unless the user explicitly asked to "send").
            val launched = if (action.appQuery.isNotBlank()) system.launchApp(action.appQuery) else true
            if (launched) delay(1200)
            JarvisAccessibilityService.instance?.typeText(action.text) ?: run {
                JarvisLog.error("COMPOSE — accessibility service not enabled")
                false
            }
        }

        is JarvisAction.TapText ->
            JarvisAccessibilityService.instance?.tapByText(action.label) ?: notEnabled()

        JarvisAction.Back ->
            JarvisAccessibilityService.instance?.let { it.goBack(); true } ?: notEnabled()

        JarvisAction.Home ->
            JarvisAccessibilityService.instance?.let { it.goHome(); true } ?: notEnabled()

        is JarvisAction.Unknown -> {
            JarvisLog.warn("UNRECOGNISED command: \"${action.raw}\"")
            false
        }
    }

    private fun notEnabled(): Boolean {
        JarvisLog.error("Action needs the AccessibilityService — enable it in Settings")
        return false
    }

    private fun allowed(app: String): Boolean = prefs.isAllowed(app)

    private fun blocked(app: String): Boolean {
        JarvisLog.warn("BLOCKED \"$app\" — not in whitelist (disable enforcement in Settings)")
        return false
    }

    private inline fun runCatchingPermission(block: () -> Unit): Boolean = try {
        block(); true
    } catch (e: SecurityException) {
        JarvisLog.error("Missing runtime permission: ${e.message}")
        false
    }
}
