package com.real3rr0r303yt.jarvis.system

import com.real3rr0r303yt.jarvis.core.JarvisLog
import rikka.shizuku.Shizuku

/**
 * Optional elevated command execution.
 *
 * Preference order:
 *   1. Shizuku — runs commands with ADB-level (shell) privileges, no root,
 *      as long as the user has started a Shizuku service and granted this app.
 *   2. Root (`su`) — used only if present; never required.
 *
 * When neither is available, callers fall back to standard public APIs, so the
 * app is fully functional without any elevated access. This object only runs
 * the exact command strings Jarvis constructs for the user's request.
 */
object ElevatedShell {

    fun isAvailable(): Boolean = shizukuReady() || rootAvailable()

    private fun shizukuReady(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    private fun rootAvailable(): Boolean =
        System.getenv("PATH")?.split(":")?.any { dir ->
            java.io.File(dir, "su").canExecute()
        } == true

    /** Runs [command] via the best available elevated channel. Returns stdout. */
    fun exec(command: String): String {
        return try {
            if (shizukuReady()) execShizuku(command) else execRoot(command)
        } catch (t: Throwable) {
            JarvisLog.error("ELEVATED exec failed: ${t.message}")
            ""
        }
    }

    private fun execShizuku(command: String): String {
        // Shizuku.newProcess is a hidden API surfaced via the Shizuku provider;
        // accessed reflectively so the app still compiles against the public SDK.
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        ).apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val process = method.invoke(
            null,
            arrayOf("sh", "-c", command),
            null,
            null,
        ) as Process
        return readProcess(process)
    }

    private fun execRoot(command: String): String {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        return readProcess(process)
    }

    private fun readProcess(process: Process): String {
        val out = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return out.trim()
    }
}
