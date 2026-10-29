package com.real3rr0r303yt.jarvis

import android.content.Intent
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
import androidx.lifecycle.lifecycleScope
import com.real3rr0r303yt.jarvis.brain.CommandProcessor
import com.real3rr0r303yt.jarvis.core.JarvisLog
import com.real3rr0r303yt.jarvis.core.SystemSnapshot
import com.real3rr0r303yt.jarvis.core.SystemStats
import com.real3rr0r303yt.jarvis.ui.dashboard.DashboardScreen
import com.real3rr0r303yt.jarvis.ui.theme.JarvisTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Single-activity host for the Jarvis HUD. Owns the [CommandProcessor] and the
 * telemetry refresh loop; the composable UI is otherwise stateless.
 */
class MainActivity : ComponentActivity() {

    private lateinit var processor: CommandProcessor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        processor = CommandProcessor(applicationContext)
        requestBaselinePermissions()

        setContent {
            JarvisTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                    JarvisHud(
                        modifier = Modifier.padding(inner),
                        onCommand = ::runCommand,
                        onEnableAccessibility = {
                            openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        },
                        onEnableNotifications = {
                            openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        },
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

    private fun openSettings(action: String) {
        runCatching {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { JarvisLog.error("Could not open settings: ${it.message}") }
    }

    private fun requestBaselinePermissions() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(android.Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), 1001)
        }
    }
}

@Composable
private fun JarvisHud(
    modifier: Modifier,
    onCommand: (String) -> Unit,
    onEnableAccessibility: () -> Unit,
    onEnableNotifications: () -> Unit,
) {
    val context = LocalContext.current
    val log by JarvisLog.lines.collectAsState()
    var snapshot by remember {
        mutableStateOf(SystemStats.snapshot(context))
    }

    // Refresh telemetry roughly once a second while on screen.
    LaunchedEffect(Unit) {
        while (true) {
            snapshot = SystemStats.snapshot(context)
            delay(1000)
        }
    }

    DashboardScreen(
        snapshot = snapshot,
        log = log,
        onCommand = onCommand,
        onEnableAccessibility = onEnableAccessibility,
        onEnableNotifications = onEnableNotifications,
        modifier = modifier,
    )
}
