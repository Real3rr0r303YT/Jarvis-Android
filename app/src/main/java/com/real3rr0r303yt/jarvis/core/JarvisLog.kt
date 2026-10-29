package com.real3rr0r303yt.jarvis.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Severity used for colouring terminal lines in the HUD. */
enum class LogLevel { INFO, ACTION, WARN, ERROR }

/** A single immutable terminal line. */
data class LogLine(
    val timestamp: Long,
    val level: LogLevel,
    val text: String,
) {
    private companion object {
        val TIME_FMT = SimpleDateFormat("HH:mm:ss", Locale.US)
    }

    /** Rendered as `JARVIS@root:~# EXECUTE ACTION ...`-style terminal output. */
    fun render(): String {
        val clock = TIME_FMT.format(Date(timestamp))
        val prefix = when (level) {
            LogLevel.ACTION -> "EXECUTE"
            LogLevel.WARN -> "WARN"
            LogLevel.ERROR -> "FAIL"
            LogLevel.INFO -> "INFO"
        }
        return "[$clock] JARVIS@root:~# $prefix $text"
    }
}

/**
 * Process-wide, in-memory ring buffer of actions Jarvis has taken. The HUD
 * observes [lines]; services and the command processor append to it. Kept
 * intentionally small and ephemeral — nothing is persisted off-device.
 */
object JarvisLog {

    private const val MAX_LINES = 500

    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())
    val lines: StateFlow<List<LogLine>> = _lines

    fun info(text: String) = append(LogLevel.INFO, text)
    fun action(text: String) = append(LogLevel.ACTION, text)
    fun warn(text: String) = append(LogLevel.WARN, text)
    fun error(text: String) = append(LogLevel.ERROR, text)

    private fun append(level: LogLevel, text: String) {
        val line = LogLine(System.currentTimeMillis(), level, text)
        _lines.update { current ->
            (current + line).takeLast(MAX_LINES)
        }
    }

    fun clear() = _lines.update { emptyList() }
}
