package com.real3rr0r303yt.jarvis.agent.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** Supported LLM back-ends for the agent planner and the LLM brain tool. */
enum class LlmProvider(val label: String, val defaultModel: String) {
    OPENAI("OpenAI", "gpt-4o-mini"),
    GROQ("Groq", "llama-3.3-70b-versatile"),
    GEMINI("Gemini", "gemini-3.8-flash"), // v1beta generateContent; 2.5-flash was deprecated
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
 */
class LlmClient(private val config: LlmConfig) {

    /** Sends a single system+user turn and returns the assistant text. */
    suspend fun complete(systemPrompt: String, userPrompt: String): String =
        withContext(Dispatchers.IO) {
            when (config.provider) {
                LlmProvider.OPENAI -> openAiStyle("https://api.openai.com/v1/chat/completions", systemPrompt, userPrompt)
                LlmProvider.GROQ -> openAiStyle("https://api.groq.com/openai/v1/chat/completions", systemPrompt, userPrompt)
                LlmProvider.GEMINI -> gemini(systemPrompt, userPrompt)
            }
        }

    private fun openAiStyle(endpoint: String, system: String, user: String): String {
        val body = JSONObject()
            .put("model", config.model)
            .put("temperature", 0.2)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user)),
            )
        val json = post(endpoint, body, mapOf("Authorization" to "Bearer ${config.apiKey}"))
        return json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    }

    private fun gemini(system: String, user: String): String {
        val endpoint =
            "https://generativelanguage.googleapis.com/v1beta/models/${config.model}:generateContent?key=${config.apiKey}"
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
        return json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()
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
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (code !in 200..299) {
                throw LlmException("HTTP $code: ${text.take(300)}")
            }
            JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }
}

class LlmException(message: String) : Exception(message)
