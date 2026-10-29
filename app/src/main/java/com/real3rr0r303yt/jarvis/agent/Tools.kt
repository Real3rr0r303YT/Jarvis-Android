package com.real3rr0r303yt.jarvis.agent

import com.real3rr0r303yt.jarvis.agent.llm.LlmClient
import com.real3rr0r303yt.jarvis.core.JarvisLog
import com.real3rr0r303yt.jarvis.system.ElevatedShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/** Runs a natural-language command through the existing rule-based pipeline. */
typealias NlExecutor = suspend (String) -> String

/**
 * Searches the web and extracts a short, text-only summary.
 *
 * Uses DuckDuckGo's Instant Answer API — a keyless, JSON endpoint — so no
 * search-provider credentials are required. Returns the abstract plus the top
 * related topics; the agent can refine or re-query from the observation.
 */
class WebSearchTool : JarvisTool {
    override val name = "web_search"
    override val description =
        "Search the internet for facts, definitions or current info. Input: a search query string."

    override suspend fun run(input: String): String = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(input.trim(), "UTF-8")
        val url = "https://api.duckduckgo.com/?q=$q&format=json&no_html=1&skip_disambig=1"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "Jarvis-AI/1.0")
        }
        try {
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            val abstract = json.optString("AbstractText").ifBlank { json.optString("Answer") }
            val related = buildList {
                val topics = json.optJSONArray("RelatedTopics") ?: return@buildList
                for (i in 0 until minOf(topics.length(), 3)) {
                    topics.optJSONObject(i)?.optString("Text")?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
            when {
                abstract.isNotBlank() && related.isNotEmpty() ->
                    "$abstract\nRelated:\n- ${related.joinToString("\n- ")}"
                abstract.isNotBlank() -> abstract
                related.isNotEmpty() -> "Top results:\n- ${related.joinToString("\n- ")}"
                else -> "No direct answer found for \"$input\"."
            }.take(1200)
        } catch (e: Exception) {
            "web_search failed: ${e.message}"
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * Drives on-device UI automation (open app, type, tap, navigate) by routing a
 * natural-language instruction through the existing command pipeline, which in
 * turn uses [JarvisAccessibilityService]. Input is a plain instruction such as
 * `open WhatsApp and type "on my way"`.
 */
class AppAutomationTool(private val execute: NlExecutor) : JarvisTool {
    override val name = "app_automation"
    override val description =
        "Control apps on the phone: open an app, type text, tap a labelled button, go back/home. " +
            "Input: a plain instruction, e.g. 'open Spotify' or 'tap Send'."

    override suspend fun run(input: String): String = execute(input)
}

/**
 * Executes system/connectivity actions. A `shell:` prefix runs the remainder as
 * an elevated shell command (only when Shizuku/root is available); anything else
 * is treated as a natural-language system instruction (Wi-Fi, Bluetooth, volume,
 * brightness, battery saver).
 */
class SystemControlTool(private val execute: NlExecutor) : JarvisTool {
    override val name = "system_control"
    override val description =
        "Change system state: Wi-Fi, Bluetooth, volume, brightness, battery saver, or run a raw " +
            "shell command. Input: an instruction, or 'shell: <command>' for a raw elevated command."

    override suspend fun run(input: String): String {
        val trimmed = input.trim()
        return if (trimmed.startsWith("shell:", ignoreCase = true)) {
            val cmd = trimmed.substringAfter(':').trim()
            if (!ElevatedShell.isAvailable()) {
                "No elevated channel — enable Shizuku/root to run shell commands."
            } else {
                JarvisLog.action("AGENT SHELL $ $cmd")
                withContext(Dispatchers.Default) { ElevatedShell.exec(cmd) }
                    .ifBlank { "(command produced no output)" }
                    .take(1000)
            }
        } else {
            execute(trimmed)
        }
    }
}

/**
 * Pure-text reasoning tool: summaries, rewrites, translations, and questions
 * that need no device or web access. Delegates to the configured [LlmClient].
 */
class LlmBrainTool(private val llm: LlmClient) : JarvisTool {
    override val name = "llm_brain"
    override val description =
        "Process free text: summarise, translate, rewrite, or answer a general question. " +
            "Input: the text/question to process."

    override suspend fun run(input: String): String =
        try {
            llm.complete(
                systemPrompt = "You are Jarvis' language brain. Answer concisely and directly.",
                userPrompt = input,
            )
        } catch (e: Exception) {
            "llm_brain failed: ${e.message}"
        }
}
