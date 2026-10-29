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

    /** Package names that, when brought to the foreground, fire a proactive trigger. */
    private val _appTriggers = MutableStateFlow(readSet(KEY_APP_TRIGGERS))
    val appTriggers: StateFlow<Set<String>> = _appTriggers

    fun addAppTrigger(pkg: String) = mutate(KEY_APP_TRIGGERS, _appTriggers) { it + pkg.trim() }
    fun removeAppTrigger(pkg: String) = mutate(KEY_APP_TRIGGERS, _appTriggers) { it - pkg }

    /** True when at least one app-launch trigger is configured. */
    val appTriggersActive: Boolean get() = _appTriggers.value.isNotEmpty()

    /** Whether the background audio-activity trigger is enabled. Default off. */
    var audioTriggerEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUDIO_TRIGGER, false)
        set(value) = prefs.edit().putBoolean(KEY_AUDIO_TRIGGER, value).apply()

    /** When true, launches/automation are restricted to the whitelist. */
    var whitelistEnforced: Boolean
        get() = prefs.getBoolean(KEY_ENFORCE, false)
        set(value) = prefs.edit().putBoolean(KEY_ENFORCE, value).apply()

    /** When true and an API key is set, complex commands route to the agent engine. */
    var agentEnabled: Boolean
        get() = prefs.getBoolean(KEY_AGENT, false)
        set(value) = prefs.edit().putBoolean(KEY_AGENT, value).apply()

    /** Selected LLM provider name (one of LlmProvider.name); defaults to OPENAI. */
    var llmProvider: String
        get() = prefs.getString(KEY_PROVIDER, "OPENAI") ?: "OPENAI"
        set(value) = prefs.edit().putString(KEY_PROVIDER, value).apply()

    /** API key for the selected provider. Stored only on this device. */
    var llmApiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

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
        private const val KEY_AGENT = "agent_enabled"
        private const val KEY_PROVIDER = "llm_provider"
        private const val KEY_API_KEY = "llm_api_key"
        private const val KEY_APP_TRIGGERS = "app_triggers"
        private const val KEY_AUDIO_TRIGGER = "audio_trigger_enabled"

        @Volatile
        private var INSTANCE: JarvisPrefs? = null

        fun get(context: Context): JarvisPrefs =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: JarvisPrefs(context).also { INSTANCE = it }
            }
    }
}
