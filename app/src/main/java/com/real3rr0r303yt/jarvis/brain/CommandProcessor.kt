package com.real3rr0r303yt.jarvis.brain

import android.content.Context
import com.real3rr0r303yt.jarvis.agent.AppAutomationTool
import com.real3rr0r303yt.jarvis.agent.JarvisAgentEngine
import com.real3rr0r303yt.jarvis.agent.LlmBrainTool
import com.real3rr0r303yt.jarvis.agent.SystemCheckTool
import com.real3rr0r303yt.jarvis.agent.SystemControlTool
import com.real3rr0r303yt.jarvis.agent.WebSearchTool
import com.real3rr0r303yt.jarvis.agent.llm.LlmClient
import com.real3rr0r303yt.jarvis.agent.llm.LlmConfig
import com.real3rr0r303yt.jarvis.agent.llm.LlmProvider
import com.real3rr0r303yt.jarvis.brain.model.JarvisAction
import com.real3rr0r303yt.jarvis.core.JarvisLog
import com.real3rr0r303yt.jarvis.core.JarvisPrefs
import com.real3rr0r303yt.jarvis.service.JarvisAccessibilityService
import com.real3rr0r303yt.jarvis.system.ElevatedShell
import com.real3rr0r303yt.jarvis.system.SystemController
import com.real3rr0r303yt.jarvis.tts.TtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Jarvis' "brain": takes a natural-language line, parses it into actions, and
 * executes each one against the right subsystem. Returns a short, human-readable
 * summary for the HUD.
 *
 * When the autonomous agent is enabled in settings and an LLM API key is
 * configured, [process] routes the command through the [JarvisAgentEngine]
 * (ReAct planner + tools). Otherwise it uses the offline rule-based pipeline.
 */
class CommandProcessor(appContext: Context) {

    private val appContext = appContext.applicationContext
    private val parser = IntentParser()
    private val system = SystemController(appContext)
    private val prefs = JarvisPrefs.get(appContext)

    private val CHAT_OPEN_ATTEMPTS = 5
    private val TYPE_ATTEMPTS = 6
    private val POLL_INTERVAL_MS = 600L

    private val CONVERSATION_PERSONA =
        "You are Jarvis, a concise and friendly Android voice assistant for Real3rr0r303YT. " +
            "Reply in the user's language in one or two short, natural sentences suitable for being read aloud. " +
            "No markdown, no lists."

    /** Builds the current LLM config from settings, or null if unconfigured. */
    private fun llmConfig(): LlmConfig? {
        val key = prefs.llmApiKey
        if (key.isBlank()) return null
        val provider = runCatching { LlmProvider.valueOf(prefs.llmProvider) }.getOrDefault(LlmProvider.OPENAI)
        return LlmConfig(provider = provider, apiKey = key)
    }

    /**
     * Proactive content trigger: when a watched app comes to the foreground,
     * generate three fresh short-video hook ideas tailored to that app. Uses the
     * configured LLM when available; otherwise returns an offline template so the
     * trigger still produces something useful without a key.
     */
    suspend fun runContentTrigger(appLabel: String): String {
        val config = llmConfig()
        val system = "You are Jarvis, a short-form content assistant for the creator Real3rr0r303YT. " +
            "Be punchy, specific and original."
        val user = "The creator just opened $appLabel. Generate exactly 3 fresh, scroll-stopping " +
            "short-video hook ideas tailored to $appLabel. Give one hook per line, no preamble."
        return if (config != null) {
            JarvisLog.action("CONTENT AGENT ($appLabel) via ${config.provider.label}")
            try {
                LlmClient(config).complete(system, user)
            } catch (e: Exception) {
                JarvisLog.error("CONTENT AGENT failed: ${e.message}")
                fallbackHooks(appLabel)
            }
        } else {
            JarvisLog.warn("CONTENT AGENT — no API key set; using offline template")
            fallbackHooks(appLabel)
        }
    }

    private fun fallbackHooks(app: String): String = listOf(
        "POV: the one $app setting nobody told you about (but changes everything).",
        "I tried $app for 24h doing only what the algorithm wanted — here's what broke.",
        "3 things I'd tell my younger self before posting my first $app video.",
    ).joinToString("\n")

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

    /**
     * Entry point for a user command. Routes to the autonomous agent when it is
     * enabled and configured; otherwise runs the offline rule-based pipeline.
     */
    suspend fun process(input: String): String {
        val config = llmConfig()
        if (prefs.agentEnabled && config != null) {
            return runAgent(input, config)
        }
        if (prefs.agentEnabled && config == null) {
            JarvisLog.warn("AGENT enabled but no API key set — using rule-based mode")
        }

        // Rule-based path with voice feedback. If the parser recognises nothing
        // (e.g. smalltalk like "hey Jarvis"), hand off to the LLM for a spoken
        // reply instead of just warning.
        val parsed = parser.parse(input)
        JarvisLog.info("PARSE \"$input\" -> ${parsed.actions.size} action(s)")
        if (parsed.actions.all { it is JarvisAction.Unknown }) {
            return handleConversational(input, config)
        }
        TtsManager.speak(spokenConfirmation(parsed.actions))
        return executeParsed(parsed.actions)
    }

    /**
     * Handles input the rule parser didn't recognise: routes it to the selected
     * LLM for a normal conversational answer and reads it aloud. Falls back to a
     * short spoken hint when no API key is configured.
     */
    private suspend fun handleConversational(input: String, config: LlmConfig?): String {
        if (config == null) {
            JarvisLog.warn("UNRECOGNISED \"$input\" — no LLM configured for a reply")
            TtsManager.speak("Ich brauche einen API-Schlüssel in den Einstellungen, um das zu beantworten.")
            return "Not recognised and no LLM configured. Set an API key in Settings."
        }
        JarvisLog.action("SMALLTALK -> ${config.provider.label}: \"$input\"")
        val answer = try {
            LlmClient(config).complete(CONVERSATION_PERSONA, input)
        } catch (e: Exception) {
            JarvisLog.error("LLM reply failed: ${e.message}")
            val msg = "Sorry, I couldn't get a reply (${e.message})."
            TtsManager.speak("Entschuldige, ich konnte gerade keine Antwort abrufen.")
            return msg
        }
        JarvisLog.info("JARVIS: ${answer.take(200)}")
        TtsManager.speak(answer)
        return answer
    }

    /** A short, speakable confirmation for the recognised actions. */
    private fun spokenConfirmation(actions: List<JarvisAction>): String {
        val phrases = actions.mapNotNull { action ->
            when (action) {
                is JarvisAction.LaunchApp -> "Öffne ${action.query}"
                is JarvisAction.ComposeMessage -> when {
                    action.recipient.isNotBlank() -> "Schreibe an ${action.recipient}"
                    action.appQuery.isNotBlank() -> "Öffne ${action.appQuery}"
                    else -> "Text wird eingegeben"
                }
                is JarvisAction.SetWifi -> "WLAN ${onOff(action.enabled)}"
                is JarvisAction.SetBluetooth -> "Bluetooth ${onOff(action.enabled)}"
                is JarvisAction.SetVolume -> "Lautstärke ${action.percent} Prozent"
                is JarvisAction.SetBrightness -> "Helligkeit ${action.percent} Prozent"
                is JarvisAction.SetPowerSaver -> "Energiesparmodus ${onOff(action.enabled)}"
                is JarvisAction.TapText -> "Tippe auf ${action.label}"
                JarvisAction.Back -> "Zurück"
                JarvisAction.Home -> "Startbildschirm"
                is JarvisAction.Unknown -> null
            }
        }
        return phrases.joinToString(", ").ifBlank { "Erledigt" }
    }

    private fun onOff(enabled: Boolean) = if (enabled) "an" else "aus"

    /** ReAct agent path: plans and executes sub-steps via tools. */
    private suspend fun runAgent(input: String, config: LlmConfig): String {
        JarvisLog.action("AGENT ENGAGED (${config.provider.label}) :: \"$input\"")
        val llm = LlmClient(config)
        val engine = JarvisAgentEngine(
            llm = llm,
            toolList = listOf(
                WebSearchTool(),
                AppAutomationTool(::runRuleBased),
                SystemControlTool(::runRuleBased),
                SystemCheckTool(appContext),
                LlmBrainTool(llm),
            ),
        )
        val answer = engine.run(input)
        // Show in the HUD (already logged by the engine) and read it aloud.
        TtsManager.speak(answer)
        return answer
    }

    /**
     * Offline rule-based executor used by the agent's device tools. Parses and
     * runs the actions without voice feedback (the agent speaks its own final
     * answer), so tool calls don't double-narrate.
     */
    suspend fun runRuleBased(input: String): String {
        val parsed = parser.parse(input)
        JarvisLog.info("PARSE \"$input\" -> ${parsed.actions.size} action(s)")
        return executeParsed(parsed.actions)
    }

    /** Executes a parsed action list; returns a short summary for the HUD. */
    private suspend fun executeParsed(actions: List<JarvisAction>): String {
        var ok = 0
        for (action in actions) {
            if (execute(action)) ok++
            delay(300) // let the UI/screen settle between chained actions
        }
        return "Executed $ok/${actions.size} action(s)"
    }

    private suspend fun execute(action: JarvisAction): Boolean = when (action) {
        is JarvisAction.SetWifi -> run { system.setWifi(action.enabled); true }
        is JarvisAction.SetBluetooth -> runCatchingPermission { system.setBluetooth(action.enabled) }
        is JarvisAction.SetVolume -> run { system.setVolume(action.stream, action.percent); true }
        is JarvisAction.SetBrightness -> run { system.setBrightness(action.percent); true }
        is JarvisAction.SetPowerSaver -> run { system.setPowerSaver(action.enabled); true }
        is JarvisAction.LaunchApp ->
            if (allowed(action.query)) system.launchApp(action.query) else blocked(action.query)

        is JarvisAction.ComposeMessage ->
            if (action.appQuery.isNotBlank() && !allowed(action.appQuery)) blocked(action.appQuery)
            else composeMessage(action)

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

    /**
     * Opens the target app, optionally taps the named chat/contact, then types
     * the text. After launching, WhatsApp (and similar apps) take a moment and
     * the input field isn't focused yet — so we poll the accessibility service
     * instead of giving up on the first attempt. We type but never auto-send.
     */
    private suspend fun composeMessage(action: JarvisAction.ComposeMessage): Boolean {
        val a11y = JarvisAccessibilityService.instance ?: return notEnabled()

        val launched = if (action.appQuery.isNotBlank()) system.launchApp(action.appQuery) else true
        if (launched && action.appQuery.isNotBlank()) delay(1500) // let the app come up

        // If a recipient was named, open their chat first (poll for the row).
        if (action.recipient.isNotBlank()) {
            var opened = false
            repeat(CHAT_OPEN_ATTEMPTS) {
                if (!opened) {
                    if (a11y.tapByText(action.recipient)) {
                        opened = true
                    } else {
                        delay(POLL_INTERVAL_MS)
                    }
                }
            }
            if (opened) delay(1000) else JarvisLog.warn("COMPOSE — chat \"${action.recipient}\" not found; typing into current screen")
        }

        // Poll for the editable field — it may not be focused immediately.
        repeat(TYPE_ATTEMPTS) { attempt ->
            if (a11y.typeText(action.text)) return true
            if (attempt < TYPE_ATTEMPTS - 1) delay(POLL_INTERVAL_MS)
        }
        JarvisLog.error("COMPOSE — no editable field appeared after ${TYPE_ATTEMPTS} attempts")
        return false
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
