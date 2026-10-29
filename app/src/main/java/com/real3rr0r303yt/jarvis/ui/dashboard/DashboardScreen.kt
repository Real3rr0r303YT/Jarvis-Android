package com.real3rr0r303yt.jarvis.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.real3rr0r303yt.jarvis.core.LogLine
import com.real3rr0r303yt.jarvis.core.SystemSnapshot
import com.real3rr0r303yt.jarvis.ui.components.MeterBar
import com.real3rr0r303yt.jarvis.ui.components.ServicePill
import com.real3rr0r303yt.jarvis.ui.components.StatusTile
import com.real3rr0r303yt.jarvis.ui.components.TerminalLogView
import com.real3rr0r303yt.jarvis.ui.theme.JarvisAmber
import com.real3rr0r303yt.jarvis.ui.theme.JarvisBlack
import com.real3rr0r303yt.jarvis.ui.theme.JarvisNeon
import com.real3rr0r303yt.jarvis.ui.theme.JarvisNeonDim

/**
 * Stateless HUD. All inputs come from [MainActivity]; it owns no logic beyond
 * the command text field, so it is trivially previewable and testable.
 */
@Composable
fun DashboardScreen(
    snapshot: SystemSnapshot,
    log: List<LogLine>,
    listening: Boolean,
    onCommand: (String) -> Unit,
    onVoice: () -> Unit,
    onOpenSettings: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onEnableNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var command by remember { mutableStateOf("") }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "JARVIS-AI",
                    color = JarvisNeon,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    "SYSTEM AGENT // by Real3rr0r303YT",
                    color = JarvisNeonDim,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            HudButton("⚙ CFG", onOpenSettings)
        }

        // Service status pills
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ServicePill("A11Y", snapshot.accessibilityOn)
            ServicePill("NOTIF", snapshot.notificationsOn)
            ServicePill("ROOT/SHIZUKU", snapshot.elevatedOn)
        }

        // Telemetry tiles
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusTile(
                "BATTERY",
                "${snapshot.batteryPercent}%${if (snapshot.charging) " ⚡" else ""}",
                accent = if (snapshot.batteryPercent < 20) JarvisAmber else JarvisNeon,
                modifier = Modifier.weight(1f),
            )
            StatusTile("CPU", "${snapshot.cpuCores} cores", modifier = Modifier.weight(1f))
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "RAM  ${snapshot.ramUsedMb} / ${snapshot.ramTotalMb} MB  (${snapshot.ramPercent}%)",
                color = JarvisNeonDim,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            MeterBar(snapshot.ramPercent)
        }

        // Permission enablers (only act via system settings screens)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HudButton("ENABLE A11Y", onEnableAccessibility, Modifier.weight(1f))
            HudButton("ENABLE NOTIF", onEnableNotifications, Modifier.weight(1f))
        }

        // Command input
        OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            label = { Text("JARVIS@root:~#", fontFamily = FontFamily.Monospace) },
            placeholder = { Text("Öffne WhatsApp und schreib \"Hi\"") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardActions = KeyboardActions(onDone = {
                onCommand(command); command = ""
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisNeon,
                unfocusedBorderColor = JarvisNeonDim,
                focusedTextColor = JarvisNeon,
                unfocusedTextColor = JarvisNeon,
                cursorColor = JarvisNeon,
                focusedLabelColor = JarvisNeon,
                unfocusedLabelColor = JarvisNeonDim,
            ),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HudButton(
                "EXECUTE",
                { if (command.isNotBlank()) { onCommand(command); command = "" } },
                Modifier.weight(1f),
            )
            HudButton(if (listening) "● REC" else "🎙 VOICE", onVoice, Modifier.weight(1f))
        }
        if (listening) {
            Text(
                "JARVIS@root:~# listening...",
                color = JarvisNeon,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }

        // Terminal log
        Text(
            "// ACTION LOG",
            color = JarvisNeonDim,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
        TerminalLogView(log, Modifier.fillMaxWidth().weight(1f, fill = false).padding(bottom = 8.dp))
    }
}

@Composable
private fun HudButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = JarvisNeon,
            contentColor = JarvisBlack,
        ),
    ) {
        Text(text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}
