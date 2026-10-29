package com.real3rr0r303yt.jarvis.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lightweight, on-device settings store backed by SharedPreferences.
 *
 * Holds the user's customisation: an app whitelist (apps Jarvis is allowed to
 * launch/automate) and a set of saved custom shell commands (only ever run
 * through the elevated channel the user themselves enabled). Everything stays
 * local; nothing is synced anywhere.
 */
class JarvisPrefs private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)

    private val _whitelist = MutableStateFlow(readSet(KEY_WHITELIST))
    val whitelist: StateFlow<Set<String>> = _whitelist

    private val _customCommands = MutableStateFlow(readSet(KEY_COMMANDS))
    val customCommands: StateFlow<Set<String>> = _customCommands

    /** When true, launches/automation are restricted to the whitelist. */
    var whitelistEnforced: Boolean
        get() = prefs.getBoolean(KEY_ENFORCE, false)
        set(value) = prefs.edit().putBoolean(KEY_ENFORCE, value).apply()

    fun addToWhitelist(app: String) = mutate(KEY_WHITELIST, _whitelist) { it + app.trim() }
    fun removeFromWhitelist(app: String) = mutate(KEY_WHITELIST, _whitelist) { it - app }

    fun addCustomCommand(cmd: String) = mutate(KEY_COMMANDS, _customCommands) { it + cmd.trim() }
    fun removeCustomCommand(cmd: String) = mutate(KEY_COMMANDS, _customCommands) { it - cmd }

    /** True if [app] is permitted when the whitelist is enforced. */
    fun isAllowed(app: String): Boolean {
        if (!whitelistEnforced) return true
        val list = _whitelist.value
        return list.isEmpty() || list.any { app.contains(it, ignoreCase = true) }
    }

    private fun mutate(
        key: String,
        flow: MutableStateFlow<Set<String>>,
        op: (Set<String>) -> Set<String>,
    ) {
        val next = op(flow.value).filter { it.isNotBlank() }.toSet()
        prefs.edit().putStringSet(key, next).apply()
        flow.value = next
    }

    private fun readSet(key: String): Set<String> =
        prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()

    companion object {
        private const val KEY_WHITELIST = "app_whitelist"
        private const val KEY_COMMANDS = "custom_commands"
        private const val KEY_ENFORCE = "whitelist_enforced"

        @Volatile
        private var INSTANCE: JarvisPrefs? = null

        fun get(context: Context): JarvisPrefs =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: JarvisPrefs(context).also { INSTANCE = it }
            }
    }
}
