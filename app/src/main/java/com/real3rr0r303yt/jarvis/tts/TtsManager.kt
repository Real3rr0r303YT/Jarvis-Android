package com.real3rr0r303yt.jarvis.tts

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.real3rr0r303yt.jarvis.core.JarvisLog
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Process-wide Text-to-Speech output with two back-ends:
 *
 *  - **Local engine** (default): Android TextToSpeech tuned for a darker,
 *    precise "Jarvis" vibe — lower [pitch], slightly slower rate, and the
 *    highest-quality offline voice for the user's locale.
 *  - **Online HQ voice** (optional): when an ElevenLabs API key + voice id are
 *    set, synthesis is streamed from ElevenLabs for a natural, human voice and
 *    played via MediaPlayer. Any failure falls back to the local engine, so
 *    speech never silently breaks.
 *
 * Speaking is gated by [enabled]; all calls are safe before init and after
 * shutdown.
 */
object TtsManager {

    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var appContext: Context? = null
    @Volatile private var player: MediaPlayer? = null

    /** Mirrors the user's settings toggle; when false, [speak] does nothing. */
    @Volatile var enabled: Boolean = false

    /** Local-engine voice tuning (exposed so settings can fine-tune the vibe). */
    @Volatile var pitch: Float = 0.85f
    @Volatile var speechRate: Float = 0.98f

    /** Optional ElevenLabs online voice. When both are set, it's preferred. */
    @Volatile var onlineApiKey: String = ""
    @Volatile var onlineVoiceId: String = ""

    private const val ELEVEN_MODEL = "eleven_multilingual_v2"

    fun init(context: Context) {
        appContext = context.applicationContext
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                applyVoiceProfile()
                JarvisLog.info("TTS engine ready (Jarvis voice profile)")
            } else {
                JarvisLog.warn("TTS engine init failed (status $status)")
            }
        }
    }

    private fun applyVoiceProfile() {
        val engine = tts ?: return
        runCatching {
            engine.setPitch(pitch)
            engine.setSpeechRate(speechRate)
            val locale = Locale.getDefault().takeIf { localeSupported(engine, it) } ?: Locale.US
            engine.language = locale
            selectBestVoice(engine, locale)?.let { engine.voice = it }
        }.onFailure { JarvisLog.warn("TTS voice profile not fully applied: ${it.message}") }
    }

    private fun selectBestVoice(engine: TextToSpeech, locale: Locale): Voice? {
        val voices = runCatching { engine.voices }.getOrNull()?.filterNotNull().orEmpty()
        if (voices.isEmpty()) return null
        val lang = locale.language
        return voices.filter { it.locale.language == lang && !it.isNetworkConnectionRequired }
            .maxByOrNull { it.quality }
            ?: voices.filter { it.locale.language == lang }.maxByOrNull { it.quality }
    }

    private fun localeSupported(engine: TextToSpeech, locale: Locale): Boolean {
        val res = runCatching { engine.isLanguageAvailable(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        return res == TextToSpeech.LANG_AVAILABLE ||
            res == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
            res == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
    }

    /** Reads [text] aloud if enabled. Prefers the online HQ voice when configured. */
    fun speak(text: String) {
        if (!enabled) return
        val clean = text.trim().take(800)
        if (clean.isEmpty()) return
        if (onlineApiKey.isNotBlank() && onlineVoiceId.isNotBlank()) {
            Thread({ speakOnline(clean) }, "jarvis-tts-online").start()
        } else {
            speakLocal(clean)
        }
    }

    private fun speakLocal(text: String) {
        if (!ready) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-${System.currentTimeMillis()}")
    }

    /** Synthesises via ElevenLabs and plays the audio; falls back to local on error. */
    private fun speakOnline(text: String) {
        val ctx = appContext ?: return speakLocal(text)
        val conn = try {
            (URL("https://api.elevenlabs.io/v1/text-to-speech/$onlineVoiceId").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "audio/mpeg")
                setRequestProperty("xi-api-key", onlineApiKey)
            }
        } catch (e: Exception) {
            JarvisLog.warn("TTS online setup failed (${e.message}); using local voice")
            return speakLocal(text)
        }
        try {
            val body = JSONObject().put("text", text).put("model_id", ELEVEN_MODEL)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(200).orEmpty()
                JarvisLog.warn("TTS online HTTP $code ($err); using local voice")
                return speakLocal(text)
            }
            val out = File(ctx.cacheDir, "jarvis-tts.mp3")
            conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
            playFile(out)
        } catch (e: Exception) {
            JarvisLog.warn("TTS online failed (${e.message}); using local voice")
            speakLocal(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun playFile(file: File) {
        try {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { it.release(); if (player === it) player = null }
                prepare()
                start()
            }
        } catch (e: Exception) {
            JarvisLog.warn("TTS playback failed: ${e.message}")
        }
    }

    /** Re-reads tuning and applies it to the local engine (call after settings change). */
    fun refreshProfile() {
        if (ready) applyVoiceProfile()
    }

    fun stop() {
        runCatching { tts?.stop() }
        runCatching { player?.release() }
        player = null
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
