package com.real3rr0r303yt.jarvis.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.real3rr0r303yt.jarvis.ui.theme.JarvisBlack
import com.real3rr0r303yt.jarvis.ui.theme.JarvisNeon
import com.real3rr0r303yt.jarvis.ui.theme.JarvisNeonDim
import com.real3rr0r303yt.jarvis.ui.theme.JarvisRed
import com.real3rr0r303yt.jarvis.ui.theme.JarvisSurface

/** Immutable permission/status flags shown at the top of settings. */
data class PermissionStatus(
    val accessibility: Boolean,
    val notifications: Boolean,
    val elevated: Boolean,
    val foregroundRunning: Boolean,
)

@Composable
fun SettingsScreen(
    status: PermissionStatus,
    whitelist: Set<String>,
    whitelistEnforced: Boolean,
    customCommands: Set<String>,
    agentEnabled: Boolean,
    llmProvider: String,
    apiKeySet: Boolean,
    providerOptions: List<String>,
    appTriggers: Set<String>,
    triggerPresets: List<Pair<String, String>>,
    audioTrigger: Boolean,
    ttsEnabled: Boolean,
    overlayEnabled: Boolean,
    appLabel: (String) -> String,
    onBack: () -> Unit,
    onToggleEnforce: (Boolean) -> Unit,
    onAddApp: (String) -> Unit,
    onRemoveApp: (String) -> Unit,
    onAddCommand: (String) -> Unit,
    onRemoveCommand: (String) -> Unit,
    onRunCommand: (String) -> Unit,
    onToggleForeground: (Boolean) -> Unit,
    onToggleAgent: (Boolean) -> Unit,
    onSelectProvider: (String) -> Unit,
    onSetApiKey: (String) -> Unit,
    onAddAppTrigger: (String) -> Unit,
    onRemoveAppTrigger: (String) -> Unit,
    onToggleAudioTrigger: (Boolean) -> Unit,
    onToggleTts: (Boolean) -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "// SETTINGS",
                color = JarvisNeon,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            NeonButton("< BACK", onBack)
        }

        // --- Permission status ---
        Section("PERMISSION STATUS") {
            StatusRow("Accessibility Service", status.accessibility)
            StatusRow("Notification Access", status.notifications)
            StatusRow("Root / Shizuku", status.elevated)
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Background Monitoring",
                    color = JarvisNeonDim,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
                NeonSwitch(status.foregroundRunning, onToggleForeground)
            }
        }

        // --- Autonomous agent ---
        Section("AUTONOMOUS AGENT") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Enable agent (ReAct + tools)",
                    color = JarvisNeonDim,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
                NeonSwitch(agentEnabled, onToggleAgent)
            }
            Hint("When on, complex commands are planned and executed autonomously using the LLM + tools (web, apps, system). Needs an API key below.")

            Text("LLM PROVIDER", color = JarvisNeon, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                providerOptions.forEach { option ->
                    val selected = option.equals(llmProvider, ignoreCase = true)
                    NeonButton(if (selected) "[$option]" else option) { onSelectProvider(option) }
                }
            }

            StatusRow("API key", apiKeySet)
            AddRow("paste API key (stored on device only)", onSetApiKey)
            Hint("The key is saved locally and sent only to the selected provider.")
        }

        // --- Proactive triggers ---
        Section("PROACTIVE TRIGGERS") {
            Text("APP-LAUNCH TRIGGERS", color = JarvisNeon, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Hint("When a watched app opens, Jarvis generates fresh content ideas and shows them as a notification. Needs the Accessibility service on.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                triggerPresets.forEach { (pkg, name) ->
                    val on = pkg in appTriggers
                    NeonButton(if (on) "[$name]" else "+ $name") {
                        if (on) onRemoveAppTrigger(pkg) else onAddAppTrigger(pkg)
                    }
                }
            }
            AddRow("custom package, e.g. com.example.app", onAddAppTrigger)
            if (appTriggers.isEmpty()) {
                Hint("No app-launch triggers set.")
            } else {
                appTriggers.forEach { pkg -> Chip(appLabel(pkg)) { onRemoveAppTrigger(pkg) } }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Audio-activity trigger",
                    color = JarvisNeonDim,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
                NeonSwitch(audioTrigger, onToggleAudioTrigger)
            }
            Hint("Background mic listens for sound activity (amplitude-based) while monitoring is on. Requires mic permission; toggle background monitoring to apply.")
        }

        // --- Voice output & overlay ---
        Section("VOICE OUTPUT & OVERLAY") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Read answers aloud (TTS)",
                    color = JarvisNeonDim,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
                NeonSwitch(ttsEnabled, onToggleTts)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Floating overlay HUD",
                    color = JarvisNeonDim,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
                NeonSwitch(overlayEnabled, onToggleOverlay)
            }
            Hint("Overlay needs the \"draw over other apps\" permission; you'll be sent to grant it. Tap the floating chip for a quick voice command.")
        }

        // --- App whitelist ---
        Section("APP WHITELIST") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Restrict to whitelist",
                    color = JarvisNeonDim,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
                NeonSwitch(whitelistEnforced, onToggleEnforce)
            }
            AddRow("app name / package", onAddApp)
            if (whitelist.isEmpty()) {
                Hint("No apps whitelisted — all apps allowed.")
            } else {
                whitelist.forEach { app -> Chip(app) { onRemoveApp(app) } }
            }
        }

        // --- Custom shell commands ---
        Section("CUSTOM SHELL COMMANDS") {
            Hint("Run via the elevated channel you enabled (Shizuku/root).")
            AddRow("e.g. settings put global low_power 1", onAddCommand)
            if (customCommands.isEmpty()) {
                Hint("No saved commands.")
            } else {
                customCommands.forEach { cmd ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            cmd,
                            color = JarvisNeon,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                        )
                        NeonButton("RUN") { onRunCommand(cmd) }
                        NeonButton("X") { onRemoveCommand(cmd) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(JarvisSurface)
            .border(1.dp, JarvisNeonDim, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = JarvisNeon, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        content()
    }
}

@Composable
private fun StatusRow(label: String, on: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = JarvisNeonDim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Text(
            if (on) "● GRANTED" else "○ MISSING",
            color = if (on) JarvisNeon else JarvisRed,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun AddRow(placeholder: String, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(placeholder, fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisNeon,
                unfocusedBorderColor = JarvisNeonDim,
                focusedTextColor = JarvisNeon,
                unfocusedTextColor = JarvisNeon,
                cursorColor = JarvisNeon,
            ),
        )
        NeonButton("ADD") {
            if (text.isNotBlank()) { onAdd(text); text = "" }
        }
    }
}

@Composable
private fun Chip(label: String, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("• $label", color = JarvisNeon, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        NeonButton("X", onRemove)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = JarvisNeonDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
}

@Composable
private fun NeonButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = JarvisNeon, contentColor = JarvisBlack),
    ) {
        Text(text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun NeonSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = JarvisBlack,
            checkedTrackColor = JarvisNeon,
            uncheckedThumbColor = JarvisNeonDim,
            uncheckedTrackColor = JarvisSurface,
        ),
    )
}
