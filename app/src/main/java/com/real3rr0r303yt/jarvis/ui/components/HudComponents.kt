package com.real3rr0r303yt.jarvis.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.real3rr0r303yt.jarvis.core.LogLevel
import com.real3rr0r303yt.jarvis.core.LogLine
import com.real3rr0r303yt.jarvis.ui.theme.JarvisAmber
import com.real3rr0r303yt.jarvis.ui.theme.JarvisNeon
import com.real3rr0r303yt.jarvis.ui.theme.JarvisNeonDim
import com.real3rr0r303yt.jarvis.ui.theme.JarvisRed
import com.real3rr0r303yt.jarvis.ui.theme.JarvisSurface

/** A labelled status tile, e.g. "BATTERY  82%". */
@Composable
fun StatusTile(
    label: String,
    value: String,
    accent: Color = JarvisNeon,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(JarvisSurface)
            .border(1.dp, JarvisNeonDim, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, color = JarvisNeonDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(
            value,
            color = accent,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/** A horizontal meter bar (0..100). */
@Composable
fun MeterBar(percent: Int, modifier: Modifier = Modifier, accent: Color = JarvisNeon) {
    val p = percent.coerceIn(0, 100)
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF0E1A0E)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(p / 100f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accent),
        )
    }
}

/** A pill showing a service as ONLINE / OFFLINE. */
@Composable
fun ServicePill(name: String, online: Boolean, modifier: Modifier = Modifier) {
    val color = if (online) JarvisNeon else JarvisRed
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, color, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(if (online) "●" else "○", color = color, fontSize = 12.sp)
        Text(
            "$name ${if (online) "ONLINE" else "OFF"}",
            color = color,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/** The live terminal log view; auto-scrolls to the newest line. */
@Composable
fun TerminalLogView(lines: List<LogLine>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF030703))
            .border(1.dp, JarvisNeonDim, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(lines) { line ->
                Text(
                    text = line.render(),
                    color = when (line.level) {
                        LogLevel.ACTION -> JarvisNeon
                        LogLevel.WARN -> JarvisAmber
                        LogLevel.ERROR -> JarvisRed
                        LogLevel.INFO -> JarvisNeonDim
                    },
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
