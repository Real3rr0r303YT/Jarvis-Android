package com.real3rr0r303yt.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.real3rr0r303yt.jarvis.agent.llm.LlmProvider
import com.real3rr0r303yt.jarvis.brain.CommandProcessor
import com.real3rr0r303yt.jarvis.brain.VoiceInput
import com.real3rr0r303yt.jarvis.core.JarvisLog
import com.real3rr0r303yt.jarvis.core.JarvisPrefs
import com.real3rr0r303yt.jarvis.core.SystemStats
import com.real3rr0r303yt.jarvis.service.JarvisAccessibilityService
import com.real3rr0r303yt.jarvis.service.JarvisForegroundService
import com.real3rr0r303yt.jarvis.service.JarvisNotificationListenerService
import com.real3rr0r303yt.jarvis.overlay.OverlayHudService
import com.real3rr0r303yt.jarvis.system.ElevatedShell
import com.real3rr0r303yt.jarvis.trigger.AppTriggerManager
import com.real3rr0r303yt.jarvis.tts.TtsManager
import com.real3rr0r303yt.jarvis.ui.dashboard.DashboardScreen
import com.real3rr0r303yt.jarvis.ui.settings.PermissionStatus
import com.real3rr0r303yt.jarvis.ui.settings.SettingsScreen
import com.real3rr0r303yt.jarvis.ui.theme.JarvisTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Which HUD screen is showing. */
private enum class Screen { DASHBOARD, SETTINGS }

/**
 * Single-activity host for the Jarvis HUD. Owns the [CommandProcessor], the
 * telemetry refresh loop, voice input and simple screen navigation; the
 * composable UI is otherwise stateless.
 */
class MainActivity : ComponentActivity() {

    private lateinit var processor: CommandProcessor
    private lateinit var prefs: JarvisPrefs
    private var voice: VoiceInput? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        processor = CommandProcessor(applicationContext)
        prefs = JarvisPrefs.get(applicationContext)
        requestBaselinePermissions()

        // Re-attach the overlay HUD on launch if the user left it enabled and
        // the permission is still granted.
        if (prefs.overlayEnabled && OverlayHudService.canDraw(this)) {
            OverlayHudService.start(this)
        }

        setContent {
            JarvisTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                    JarvisApp(
                        modifier = Modifier.padding(inner),
                        onCommand = ::runCommand,
                        onVoice = ::toggleVoice,
                        onRunRaw = ::runRaw,
                        onEnableAccessibility = { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                        onEnableNotifications = { openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
                        onToggleForeground = ::toggleForeground,
                    )
                }
            }
        }
    }

    /** Runs a user command on the lifecycle scope so it survives recomposition. */
    private fun runCommand(input: String) {
        lifecycleScope.launch {
            val summary = processor.process(input)
            JarvisLog.info(summary)
        }
    }

    private fun runRaw(command: String) {
        lifecycleScope.launch { processor.runRawCommand(command) }
    }

    // ------------------------------------------------------------- voice input
    private var listeningState = mutableStateOf(false)

    private fun toggleVoice() {
        if (listeningState.value) {
            voice?.stop()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1002)
            return
        }
        if (voice == null) {
            voice = VoiceInput(
                context = this,
                onResult = { spoken -> runCommand(spoken) },
                onState = { active -> listeningState.value = active },
            )
        }
        voice?.start()
    }

    // --------------------------------------------------------- foreground svc
    private fun toggleForeground(enable: Boolean) {
        if (enable) JarvisForegroundService.start(this)
        else JarvisForegroundService.stop(this)
    }

    // --------------------------------------------------------- overlay HUD
    /** Returns the effective overlay-enabled state after handling the toggle. */
    private fun setOverlay(enable: Boolean): Boolean {
        if (!enable) {
            OverlayHudService.stop(this)
            prefs.overlayEnabled = false
            return false
        }
        if (!OverlayHudService.canDraw(this)) {
            // Send the user to grant the draw-over-apps permission; leave it off
            // until they come back and toggle again.
            runCatching {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            JarvisLog.warn("OVERLAY needs the draw-over-apps permission — re-enable after granting")
            return false
        }
        OverlayHudService.start(this)
        prefs.overlayEnabled = true
        return true
    }

    private fun openSettings(action: String) {
        runCatching {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { JarvisLog.error("Could not open settings: ${it.message}") }
    }

    private fun requestBaselinePermissions() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), 1001)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voice?.destroy()
    }

    @Composable
    private fun JarvisApp(
        modifier: Modifier,
        onCommand: (String) -> Unit,
        onVoice: () -> Unit,
        onRunRaw: (String) -> Unit,
        onEnableAccessibility: () -> Unit,
        onEnableNotifications: () -> Unit,
        onToggleForeground: (Boolean) -> Unit,
    ) {
        val context = LocalContext.current
        val log by JarvisLog.lines.collectAsState()
        val whitelist by prefs.whitelist.collectAsState()
        val customCommands by prefs.customCommands.collectAsState()
        val appTriggers by prefs.appTriggers.collectAsState()
        val listening by listeningState
        var screen by remember { mutableStateOf(Screen.DASHBOARD) }
        var foregroundOn by remember { mutableStateOf(false) }
        var enforce by remember { mutableStateOf(prefs.whitelistEnforced) }
        var agentOn by remember { mutableStateOf(prefs.agentEnabled) }
        var provider by remember { mutableStateOf(prefs.llmProvider) }
        var apiKeySet by remember { mutableStateOf(prefs.llmApiKey.isNotBlank()) }
        var audioTrig by remember { mutableStateOf(prefs.audioTriggerEnabled) }
        var ttsOn by remember { mutableStateOf(prefs.ttsEnabled) }
        var overlayOn by remember { mutableStateOf(prefs.overlayEnabled) }
        var ttsKeySet by remember { mutableStateOf(prefs.ttsOnlineKey.isNotBlank()) }
        var ttsVoiceId by remember { mutableStateOf(prefs.ttsVoiceId) }

        var snapshot by remember { mutableStateOf(SystemStats.snapshot(context)) }
        LaunchedEffect(Unit) {
            while (true) {
                snapshot = SystemStats.snapshot(context)
                delay(1000)
            }
        }

        when (screen) {
            Screen.DASHBOARD -> DashboardScreen(
                snapshot = snapshot,
                log = log,
                listening = listening,
                onCommand = onCommand,
                onVoice = onVoice,
                onOpenSettings = { screen = Screen.SETTINGS },
                onEnableAccessibility = onEnableAccessibility,
                onEnableNotifications = onEnableNotifications,
                modifier = modifier,
            )

            Screen.SETTINGS -> SettingsScreen(
                status = PermissionStatus(
                    accessibility = JarvisAccessibilityService.isConnected,
                    notifications = JarvisNotificationListenerService.isConnected,
                    elevated = ElevatedShell.isAvailable(),
                    foregroundRunning = foregroundOn,
                ),
                whitelist = whitelist,
                whitelistEnforced = enforce,
                customCommands = customCommands,
                agentEnabled = agentOn,
                llmProvider = provider,
                apiKeySet = apiKeySet,
                providerOptions = LlmProvider.entries.map { it.name },
                appTriggers = appTriggers,
                triggerPresets = AppTriggerManager.PRESETS.map { it.key to it.value },
                audioTrigger = audioTrig,
                ttsEnabled = ttsOn,
                overlayEnabled = overlayOn,
                ttsOnlineKeySet = ttsKeySet,
                ttsVoiceId = ttsVoiceId,
                appLabel = { pkg -> AppTriggerManager.label(pkg) },
                onBack = { screen = Screen.DASHBOARD },
                onToggleEnforce = { prefs.whitelistEnforced = it; enforce = it },
                onAddApp = prefs::addToWhitelist,
                onRemoveApp = prefs::removeFromWhitelist,
                onAddCommand = prefs::addCustomCommand,
                onRemoveCommand = prefs::removeCustomCommand,
                onRunCommand = onRunRaw,
                onToggleForeground = { foregroundOn = it; onToggleForeground(it) },
                onToggleAgent = { prefs.agentEnabled = it; agentOn = it },
                onSelectProvider = { prefs.llmProvider = it; provider = it },
                onSetApiKey = { prefs.llmApiKey = it; apiKeySet = it.isNotBlank() },
                onAddAppTrigger = prefs::addAppTrigger,
                onRemoveAppTrigger = prefs::removeAppTrigger,
                onToggleAudioTrigger = { prefs.audioTriggerEnabled = it; audioTrig = it },
                onToggleTts = { prefs.ttsEnabled = it; TtsManager.enabled = it; ttsOn = it },
                onToggleOverlay = { overlayOn = setOverlay(it) },
                onSetTtsKey = { prefs.ttsOnlineKey = it; TtsManager.onlineApiKey = it; ttsKeySet = it.isNotBlank() },
                onSetTtsVoiceId = { prefs.ttsVoiceId = it; TtsManager.onlineVoiceId = it; ttsVoiceId = it },
                modifier = modifier,
            )
        }
    }
}
