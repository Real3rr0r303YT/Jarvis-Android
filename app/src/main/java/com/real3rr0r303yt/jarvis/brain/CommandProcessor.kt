package com.real3rr0r303yt.jarvis.brain

import android.content.Context
import com.real3rr0r303yt.jarvis.agent.AppAutomationTool
import com.real3rr0r303yt.jarvis.agent.JarvisAgentEngine
import com.real3rr0r303yt.jarvis.agent.LlmBrainTool
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

    private val parser = IntentParser()
    private val system = SystemController(appContext)
    private val prefs = JarvisPrefs.get(appContext)

    /** Builds the current LLM config from settings, or null if unconfigured. */
    private fun llmConfig(): LlmConfig? {
        val key = prefs.llmApiKey
        if (key.isBlank()) return null
        val provider = runCatching { LlmProvider.valueOf(prefs.llmProvider) }.getOrDefault(LlmProvider.OPENAI)
        return LlmConfig(provider = provider, apiKey = key)
    }

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
        return if (prefs.agentEnabled && config != null) {
            runAgent(input, config)
        } else {
            if (prefs.agentEnabled && config == null) {
                JarvisLog.warn("AGENT enabled but no API key set — using rule-based mode")
            }
            runRuleBased(input)
        }
    }

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
                LlmBrainTool(llm),
            ),
        )
        return engine.run(input)
    }

    /** Offline rule-based path; also the executor the agent's device tools reuse. */
    suspend fun runRuleBased(input: String): String {
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
            if (action.appQuery.isNotBlank() && !allowed(action.appQuery)) {
                blocked(action.appQuery)
            } else {
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
