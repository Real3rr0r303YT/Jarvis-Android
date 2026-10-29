package com.real3rr0r303yt.jarvis.agent.llm

import com.real3rr0r303yt.jarvis.core.JarvisLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Supported LLM back-ends. [defaultModel] is the primary model; [fallbacks] are
 * tried in order when the primary (or a user-set model) is rejected with a
 * model-not-found (HTTP 404) — provider catalogues change, and a given key may
 * lack access to the newest model, so we degrade gracefully instead of failing.
 */
enum class LlmProvider(
    val label: String,
    val defaultModel: String,
    val fallbacks: List<String>,
) {
    OPENAI("OpenAI", "gpt-4o-mini", listOf("gpt-4o-mini")),
    // Groq retired the Llama 3.1/3.3 chat models on free/dev tiers (2026-08-16);
    // openai/gpt-oss-* are the current production text models.
    GROQ("Groq", "openai/gpt-oss-20b", listOf("openai/gpt-oss-20b", "openai/gpt-oss-120b", "qwen/qwen3.8-27b")),
    GEMINI("Gemini", "gemini-3.8-flash", listOf("gemini-3.8-flash", "gemini-3.6-flash")),
}

/** Immutable config read from settings. */
data class LlmConfig(
    val provider: LlmProvider,
    val apiKey: String,
    val model: String = provider.defaultModel,
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()
}

/**
 * Minimal, dependency-free chat client. OpenAI and Groq share the OpenAI
 * chat-completions schema; Gemini uses its generateContent endpoint. All calls
 * run on [Dispatchers.IO]. The API key is read from on-device settings and sent
 * only to the provider the user selected.
 *
 * Resilience:
 *  - Transient failures (HTTP 429/5xx, network) are retried with exponential
 *    backoff (up to [MAX_ATTEMPTS]).
 *  - A model-not-found (HTTP 404) falls back to the next model in the chain
 *    rather than aborting the agent; if every model is exhausted, a clear error
 *    is surfaced to the action log.
 *  - Other permanent failures (400/401) fail fast with a descriptive message.
 */
class LlmClient(private val config: LlmConfig) {

    /** Ordered, de-duplicated model chain: the configured model first, then fallbacks. */
    private val modelChain: List<String> =
        (listOf(config.model) + config.provider.fallbacks).distinct()

    /** Sends a single system+user turn and returns the assistant text. */
    suspend fun complete(systemPrompt: String, userPrompt: String): String =
        withContext(Dispatchers.IO) {
            var lastError: LlmException? = null
            for ((index, model) in modelChain.withIndex()) {
                try {
                    return@withContext withRetry { callModel(model, systemPrompt, userPrompt) }
                } catch (e: LlmException) {
                    if (e.modelNotFound && index < modelChain.lastIndex) {
                        val next = modelChain[index + 1]
                        JarvisLog.warn("LLM ${config.provider.label}: model '$model' unavailable (${e.message}); falling back to '$next'")
                        lastError = e
                    } else {
                        throw e
                    }
                }
            }
            throw lastError ?: LlmException("${config.provider.label}: no usable model configured")
        }

    private fun callModel(model: String, system: String, user: String): String =
        when (config.provider) {
            LlmProvider.OPENAI -> openAiStyle("https://api.openai.com/v1/chat/completions", model, system, user)
            LlmProvider.GROQ -> openAiStyle("https://api.groq.com/openai/v1/chat/completions", model, system, user)
            LlmProvider.GEMINI -> gemini(model, system, user)
        }

    /** Runs [block], retrying transient failures with exponential backoff. */
    private suspend fun <T> withRetry(block: () -> T): T {
        var attempt = 0
        while (true) {
            attempt++
            try {
                return block()
            } catch (e: LlmException) {
                if (e.retryable && attempt < MAX_ATTEMPTS) {
                    val backoff = BASE_BACKOFF_MS shl (attempt - 1) // 1s, 2s, 4s
                    JarvisLog.warn("LLM ${config.provider.label} transient error (${e.message}); retry $attempt/$MAX_ATTEMPTS in ${backoff}ms")
                    delay(backoff)
                } else {
                    throw e
                }
            } catch (e: IOException) {
                if (attempt < MAX_ATTEMPTS) {
                    val backoff = BASE_BACKOFF_MS shl (attempt - 1)
                    JarvisLog.warn("LLM ${config.provider.label} network error (${e.message}); retry $attempt/$MAX_ATTEMPTS in ${backoff}ms")
                    delay(backoff)
                } else {
                    throw LlmException("network error after $MAX_ATTEMPTS attempts: ${e.message}")
                }
            }
        }
    }

    private fun openAiStyle(endpoint: String, model: String, system: String, user: String): String {
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.2)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user)),
            )
        val json = post(endpoint, body, mapOf("Authorization" to "Bearer ${config.apiKey}"))
        return try {
            json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        } catch (e: JSONException) {
            throw LlmException("unexpected ${config.provider.label} response: ${json.toString().take(200)}")
        }
    }

    private fun gemini(model: String, system: String, user: String): String {
        val endpoint =
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${config.apiKey}"
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", user))),
                ),
            )
        val json = post(endpoint, body, emptyMap())
        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            val reason = json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            throw LlmException("Gemini returned no answer${if (reason.isNotBlank()) " (blocked: $reason)" else ""}")
        }
        return try {
            candidates.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
        } catch (e: JSONException) {
            throw LlmException("unexpected Gemini response: ${json.toString().take(200)}")
        }
    }

    private fun post(endpoint: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        return try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (code !in 200..299) {
                throw LlmException(
                    "HTTP $code: ${text.take(300)}",
                    retryable = code in RETRYABLE_CODES,
                    modelNotFound = code == 404,
                )
            }
            try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw LlmException("invalid JSON from ${config.provider.label}: ${text.take(200)}")
            }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val BASE_BACKOFF_MS = 1000L
        val RETRYABLE_CODES = setOf(429, 500, 502, 503, 504)
    }
}

/**
 * Error from an LLM call.
 * @param retryable transient server/overload failure worth a backoff retry.
 * @param modelNotFound the model id was rejected (HTTP 404) — try the next model.
 */
class LlmException(
    message: String,
    val retryable: Boolean = false,
    val modelNotFound: Boolean = false,
) : Exception(message)
