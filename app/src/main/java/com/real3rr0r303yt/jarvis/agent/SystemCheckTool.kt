package com.real3rr0r303yt.jarvis.agent

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.real3rr0r303yt.jarvis.core.SystemStats

/**
 * Diagnostics tool for the ReAct agent: reports battery, RAM, CPU, network
 * transport and the state of Jarvis' own services. Read-only; the input is
 * ignored. The agent can reason over the returned summary (e.g. suggest
 * enabling battery saver when the battery is low).
 */
class SystemCheckTool(private val context: Context) : JarvisTool {
    override val name = "system_check"
    override val description =
        "Report device diagnostics: battery %, RAM usage, CPU cores, network transport, and which " +
            "Jarvis services are online. Input is ignored."

    override suspend fun run(input: String): String {
        val s = SystemStats.snapshot(context)
        return buildString {
            appendLine("Battery: ${s.batteryPercent}%${if (s.charging) " (charging)" else ""}")
            appendLine("RAM: ${s.ramUsedMb}/${s.ramTotalMb} MB (${s.ramPercent}%)")
            appendLine("CPU cores: ${s.cpuCores}")
            appendLine("Network: ${networkTransport()}")
            appendLine("Accessibility service: ${onOff(s.accessibilityOn)}")
            appendLine("Notification access: ${onOff(s.notificationsOn)}")
            append("Elevated (root/Shizuku): ${onOff(s.elevatedOn)}")
        }
    }

    private fun onOff(b: Boolean) = if (b) "online" else "off"

    private fun networkTransport(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "unknown"
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "offline"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "connected (other)"
        }
    }
}
