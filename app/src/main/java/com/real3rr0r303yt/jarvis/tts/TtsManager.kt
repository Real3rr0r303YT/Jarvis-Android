package com.real3rr0r303yt.jarvis.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import com.real3rr0r303yt.jarvis.core.JarvisLog
import java.util.Locale

/**
 * Process-wide Text-to-Speech output. Initialised once from the application and
 * used to read agent/LLM answers aloud. Speaking is gated by [enabled] so the
 * user can toggle voice output from settings without tearing down the engine.
 *
 * All calls are safe before init completes (they no-op) and after shutdown.
 */
object TtsManager {

    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ready = false

    /** Mirrors the user's settings toggle; when false, [speak] does nothing. */
    @Volatile var enabled: Boolean = false

    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.getDefault().takeIf { localeSupported(it) } ?: Locale.ENGLISH
                JarvisLog.info("TTS engine ready")
            } else {
                JarvisLog.warn("TTS engine init failed (status $status)")
            }
        }
    }

    private fun localeSupported(locale: Locale): Boolean {
        val res = tts?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
        return res == TextToSpeech.LANG_AVAILABLE ||
            res == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
            res == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
    }

    /** Reads [text] aloud if TTS is enabled and ready. Long text is trimmed. */
    fun speak(text: String) {
        if (!enabled || !ready) return
        val clean = text.trim().take(600)
        if (clean.isEmpty()) return
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "jarvis-${System.currentTimeMillis()}")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
