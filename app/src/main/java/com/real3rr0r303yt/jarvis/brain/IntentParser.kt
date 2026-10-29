package com.real3rr0r303yt.jarvis.brain

import com.real3rr0r303yt.jarvis.brain.model.JarvisAction
import com.real3rr0r303yt.jarvis.brain.model.ParsedCommand
import com.real3rr0r303yt.jarvis.brain.model.VolumeStream

/**
 * Rule-based intent parser: turns a natural-language line (German or English)
 * into a list of concrete [JarvisAction]s.
 *
 * It is deliberately transparent and offline — no text leaves the device. A
 * cloud LLM could be slotted in behind the same interface later by replacing
 * [parse], but the rule engine already covers the common command vocabulary and
 * keeps behaviour auditable.
 */
class IntentParser {

    fun parse(input: String): ParsedCommand {
        val clauses = splitClauses(input)
        val actions = clauses.mapNotNull { toAction(it.trim()) }
        val resolved = actions.ifEmpty { listOf(JarvisAction.Unknown(input)) }
        return ParsedCommand(raw = input, actions = resolved)
    }

    /** Splits a compound command on conjunctions/commas ("... und ... and ..."). */
    private fun splitClauses(input: String): List<String> =
        input.split(Regex("\\s+und\\s+|\\s+and\\s+|\\s*,\\s*|\\s*;\\s*", RegexOption.IGNORE_CASE))
            .filter { it.isNotBlank() }

    private fun toAction(clause: String): JarvisAction? {
        val c = clause.lowercase()

        // --- enable/disable intent for the clause ---
        val wantsOff = ANY_OFF.any { c.contains(it) }
        val wantsOn = ANY_ON.any { c.contains(it) }

        return when {
            // Wi-Fi
            matches(c, "wlan", "wifi", "wi-fi") ->
                JarvisAction.SetWifi(enabled = resolveOnOff(wantsOn, wantsOff, default = true))

            // Bluetooth
            matches(c, "bluetooth", "bt") ->
                JarvisAction.SetBluetooth(enabled = resolveOnOff(wantsOn, wantsOff, default = true))

            // Power saver / battery saver
            matches(c, "energiespar", "stromspar", "power sav", "battery sav") ->
                JarvisAction.SetPowerSaver(enabled = resolveOnOff(wantsOn, wantsOff, default = true))

            // Volume
            matches(c, "lautstärke", "lautstarke", "volume", "laut", "leise") -> {
                val pct = extractPercent(c)
                    ?: if (c.contains("leise")) 20 else if (c.contains("laut")) 80 else 50
                JarvisAction.SetVolume(VolumeStream.MEDIA, pct)
            }

            // Brightness
            matches(c, "helligkeit", "brightness", "display") -> {
                val pct = extractPercent(c) ?: if (wantsOff) 10 else 80
                JarvisAction.SetBrightness(pct)
            }

            // Compose/send a message ("schreib ...", "sende ... an whatsapp")
            matches(c, "schreib", "schreibe", "nachricht", "message", "text ", "send", "sende") -> {
                val app = extractApp(clause)
                val text = extractQuotedOrTail(clause)
                JarvisAction.ComposeMessage(appQuery = app ?: "", text = text)
            }

            // Launch an app ("öffne ...", "starte ...", "open ...")
            matches(c, "öffne", "offne", "starte", "open", "launch", "start") -> {
                val app = extractApp(clause) ?: clause.substringAfter(' ').trim()
                JarvisAction.LaunchApp(app)
            }

            // Navigation
            matches(c, "zurück", "zuruck", "back") -> JarvisAction.Back
            matches(c, "home", "startbildschirm") -> JarvisAction.Home

            // Tap a labelled element
            matches(c, "tippe", "klick", "tap", "click", "drücke", "drucke") ->
                JarvisAction.TapText(extractQuotedOrTail(clause))

            else -> JarvisAction.Unknown(clause)
        }
    }

    private fun matches(c: String, vararg keys: String) = keys.any { c.contains(it) }

    private fun resolveOnOff(on: Boolean, off: Boolean, default: Boolean) =
        when {
            off && !on -> false
            on && !off -> true
            else -> default
        }

    private fun extractPercent(c: String): Int? =
        Regex("(\\d{1,3})\\s*%?").find(c)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 100)

    /** Pulls an app name out of common phrasings ("... app whatsapp", "in whatsapp"). */
    private fun extractApp(clause: String): String? {
        val lower = clause.lowercase()
        for (app in KNOWN_APPS) {
            if (lower.contains(app)) return app
        }
        // "öffne <X>" / "open <X>" — take the first token after the verb.
        return Regex("(?:öffne|offne|starte|open|launch|start)\\s+([\\p{L}0-9._-]+)", RegexOption.IGNORE_CASE)
            .find(clause)?.groupValues?.get(1)
    }

    /** Text in quotes, else everything after a "schreib/write/..." verb. */
    private fun extractQuotedOrTail(clause: String): String {
        Regex("[\"„»](.+?)[\"“«]").find(clause)?.let { return it.groupValues[1] }
        return clause.substringAfter(':', clause)
            .replace(Regex("^(schreib(e)?|sende|send|text|message|tippe|klicke?|tap|click)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private companion object {
        val ANY_OFF = listOf("aus", "off", "deaktivier", "disable", "stop")
        val ANY_ON = listOf("an", "ein", "on", "aktivier", "enable", "start")
        val KNOWN_APPS = listOf(
            "whatsapp", "telegram", "signal", "chrome", "firefox", "spotify",
            "youtube", "gmail", "maps", "kamera", "camera", "einstellungen", "settings",
        )
    }
}
