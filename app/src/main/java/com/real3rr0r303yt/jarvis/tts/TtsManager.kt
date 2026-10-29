package com.real3rr0r303yt.jarvis.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.real3rr0r303yt.jarvis.core.JarvisLog
import java.util.Locale

/**
 * Process-wide Text-to-Speech output, tuned for a darker, precise "Jarvis"
 * system-AI vibe rather than the default robotic voice:
 *  - slightly lowered [PITCH] and marginally slower [SPEECH_RATE];
 *  - a preference for a high-quality, on-device voice in the user's locale
 *    (falling back to en-US, then the engine default).
 *
 * Speaking is gated by [enabled]. All calls are safe before init completes
 * (they no-op) and after shutdown.
 */
object TtsManager {

    private const val PITCH = 0.85f        // lower = deeper
    private const val SPEECH_RATE = 0.98f  // a touch slower = more deliberate

    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ready = false

    /** Mirrors the user's settings toggle; when false, [speak] does nothing. */
    @Volatile var enabled: Boolean = false

    fun init(context: Context) {
        if (tts != null) return
        val engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                applyVoiceProfile()
                JarvisLog.info("TTS engine ready (Jarvis voice profile)")
            } else {
                JarvisLog.warn("TTS engine init failed (status $status)")
            }
        }
        tts = engine
    }

    /** Applies pitch/rate and selects the best matching voice. */
    private fun applyVoiceProfile() {
        val engine = tts ?: return
        runCatching {
            engine.setPitch(PITCH)
            engine.setSpeechRate(SPEECH_RATE)

            val locale = Locale.getDefault().takeIf { localeSupported(engine, it) } ?: Locale.US
            engine.language = locale

            selectBestVoice(engine, locale)?.let { voice ->
                engine.voice = voice
                JarvisLog.info("TTS voice: ${voice.name} (${voice.locale})")
            }
        }.onFailure { JarvisLog.warn("TTS voice profile could not be fully applied: ${it.message}") }
    }

    /** Prefers a high-quality, offline voice for [locale]; else any for the language. */
    private fun selectBestVoice(engine: TextToSpeech, locale: Locale): Voice? {
        val voices = runCatching { engine.voices }.getOrNull()?.filterNotNull().orEmpty()
        if (voices.isEmpty()) return null
        val lang = locale.language
        return voices
            .filter { it.locale.language == lang && !it.isNetworkConnectionRequired && !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .maxByOrNull { it.quality }
            ?: voices.filter { it.locale.language == lang }.maxByOrNull { it.quality }
    }

    private fun localeSupported(engine: TextToSpeech, locale: Locale): Boolean {
        val res = runCatching { engine.isLanguageAvailable(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
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
