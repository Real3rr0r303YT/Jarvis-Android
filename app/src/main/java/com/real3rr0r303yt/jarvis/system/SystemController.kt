package com.real3rr0r303yt.jarvis.system

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresPermission
import com.real3rr0r303yt.jarvis.brain.model.VolumeStream
import com.real3rr0r303yt.jarvis.core.JarvisLog

/**
 * Thin, honest wrapper over Android's system services.
 *
 * Modern Android deliberately removes silent programmatic toggles for some
 * radios (an app can no longer flip Wi-Fi on Android 10+). Where that is the
 * case we open the relevant Settings panel so the user confirms with one tap,
 * rather than pretending we changed it. The [ElevatedShell] (Shizuku/root)
 * path, when available, can perform the toggle directly.
 */
class SystemController(private val appContext: Context) {

    private val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val power = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val elevated = ElevatedShell

    // ------------------------------------------------------------------ Wi-Fi
    fun setWifi(enabled: Boolean) {
        if (elevated.isAvailable()) {
            elevated.exec("svc wifi ${if (enabled) "enable" else "disable"}")
            JarvisLog.action("WIFI -> ${state(enabled)} (shizuku)")
            return
        }
        // No elevated path: hand off to the system Wi-Fi panel for user confirm.
        val panel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            @Suppress("DEPRECATION")
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(panel)
        JarvisLog.warn("WIFI toggle requires user confirmation -> opened Wi-Fi panel")
    }

    // -------------------------------------------------------------- Bluetooth
    @SuppressLint("MissingPermission")
    @RequiresPermission(anyOf = ["android.permission.BLUETOOTH_CONNECT"])
    fun setBluetooth(enabled: Boolean) {
        val adapter = (appContext.getSystemService(Context.BLUETOOTH_SERVICE)
                as? BluetoothManager)?.adapter
        if (adapter == null) {
            JarvisLog.error("BLUETOOTH adapter unavailable on this device")
            return
        }
        if (elevated.isAvailable()) {
            elevated.exec("svc bluetooth ${if (enabled) "enable" else "disable"}")
            JarvisLog.action("BLUETOOTH -> ${state(enabled)} (shizuku)")
            return
        }
        // Direct enable/disable was removed on Android 13+; route through the
        // system request intent so the user approves the change.
        val action = if (enabled) BluetoothAdapter.ACTION_REQUEST_ENABLE
        else Settings.ACTION_BLUETOOTH_SETTINGS
        appContext.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        JarvisLog.warn("BLUETOOTH ${state(enabled)} requested -> awaiting user confirmation")
    }

    // ------------------------------------------------------------------ Audio
    fun setVolume(stream: VolumeStream, percent: Int) {
        val androidStream = when (stream) {
            VolumeStream.MEDIA -> AudioManager.STREAM_MUSIC
            VolumeStream.RING -> AudioManager.STREAM_RING
            VolumeStream.ALARM -> AudioManager.STREAM_ALARM
        }
        val max = audio.getStreamMaxVolume(androidStream)
        val target = (percent.coerceIn(0, 100) * max) / 100
        audio.setStreamVolume(androidStream, target, 0)
        JarvisLog.action("VOLUME ${stream.name} -> $percent% ($target/$max)")
    }

    // --------------------------------------------------------------- Display
    fun setBrightness(percent: Int) {
        val value = (percent.coerceIn(0, 100) * 255) / 100
        if (Settings.System.canWrite(appContext)) {
            Settings.System.putInt(
                appContext.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            Settings.System.putInt(
                appContext.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                value,
            )
            JarvisLog.action("BRIGHTNESS -> $percent% ($value/255)")
        } else {
            // WRITE_SETTINGS not yet granted — send the user to grant it.
            appContext.startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                    .setData(android.net.Uri.parse("package:${appContext.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            JarvisLog.warn("BRIGHTNESS needs WRITE_SETTINGS -> opened permission screen")
        }
    }

    // ---------------------------------------------------------- Power saver
    fun isPowerSaveMode(): Boolean = power.isPowerSaveMode

    fun setPowerSaver(enabled: Boolean) {
        if (elevated.isAvailable()) {
            elevated.exec("settings put global low_power ${if (enabled) 1 else 0}")
            JarvisLog.action("POWER_SAVER -> ${state(enabled)} (shizuku)")
            return
        }
        // No public API to toggle battery saver; open the battery settings.
        appContext.startActivity(
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        JarvisLog.warn("POWER_SAVER toggle -> opened battery saver settings")
    }

    // -------------------------------------------------------------- App launch
    fun launchApp(query: String): Boolean {
        val pm = appContext.packageManager
        val match = pm.getInstalledApplications(0).firstOrNull { info ->
            val label = pm.getApplicationLabel(info).toString()
            label.contains(query, ignoreCase = true) ||
                info.packageName.contains(query, ignoreCase = true)
        }
        val launch = match?.let { pm.getLaunchIntentForPackage(it.packageName) }
        return if (launch != null) {
            appContext.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            JarvisLog.action("LAUNCH ${pm.getApplicationLabel(match)} [${match.packageName}]")
            true
        } else {
            JarvisLog.error("LAUNCH failed — no app matching \"$query\"")
            false
        }
    }

    private fun state(enabled: Boolean) = if (enabled) "ON" else "OFF"
}
