package com.real3rr0r303yt.jarvis.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.real3rr0r303yt.jarvis.service.JarvisAccessibilityService
import com.real3rr0r303yt.jarvis.service.JarvisNotificationListenerService
import com.real3rr0r303yt.jarvis.system.ElevatedShell

/** Snapshot of device telemetry rendered on the HUD dashboard. */
data class SystemSnapshot(
    val batteryPercent: Int,
    val charging: Boolean,
    val ramUsedMb: Long,
    val ramTotalMb: Long,
    val cpuCores: Int,
    val accessibilityOn: Boolean,
    val notificationsOn: Boolean,
    val elevatedOn: Boolean,
) {
    val ramPercent: Int
        get() = if (ramTotalMb == 0L) 0 else ((ramUsedMb * 100) / ramTotalMb).toInt()
}

/** Collects a [SystemSnapshot] from public Android APIs. */
object SystemStats {

    fun snapshot(context: Context): SystemSnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val totalMb = mem.totalMem / (1024 * 1024)
        val availMb = mem.availMem / (1024 * 1024)

        val battery = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        return SystemSnapshot(
            batteryPercent = pct,
            charging = charging,
            ramUsedMb = totalMb - availMb,
            ramTotalMb = totalMb,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            accessibilityOn = JarvisAccessibilityService.isConnected,
            notificationsOn = JarvisNotificationListenerService.isConnected,
            elevatedOn = ElevatedShell.isAvailable(),
        )
    }
}
