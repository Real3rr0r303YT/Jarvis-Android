package com.real3rr0r303yt.jarvis.brain.model

/**
 * A concrete, executable device action. The [IntentParser] turns natural
 * language into a list of these; the [CommandProcessor] dispatches each one
 * to the [SystemController] or an accessibility routine.
 */
sealed interface JarvisAction {

    // --- Connectivity ---
    data class SetWifi(val enabled: Boolean) : JarvisAction
    data class SetBluetooth(val enabled: Boolean) : JarvisAction

    // --- Audio / display ---
    data class SetVolume(val stream: VolumeStream, val percent: Int) : JarvisAction
    data class SetBrightness(val percent: Int) : JarvisAction
    data class SetPowerSaver(val enabled: Boolean) : JarvisAction

    // --- Apps ---
    data class LaunchApp(val query: String) : JarvisAction

    /** Type/send a message in the currently focused app via accessibility. */
    data class ComposeMessage(val appQuery: String, val text: String) : JarvisAction

    // --- On-screen automation ---
    data class TapText(val label: String) : JarvisAction
    data object Back : JarvisAction
    data object Home : JarvisAction

    /** Parser could not map the input to any supported action. */
    data class Unknown(val raw: String) : JarvisAction
}

enum class VolumeStream { MEDIA, RING, ALARM }

/** Result of parsing one natural-language command line. */
data class ParsedCommand(
    val raw: String,
    val actions: List<JarvisAction>,
)
