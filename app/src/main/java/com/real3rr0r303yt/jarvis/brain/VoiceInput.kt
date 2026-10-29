package com.real3rr0r303yt.jarvis.brain

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.real3rr0r303yt.jarvis.core.JarvisLog
import java.util.Locale

/**
 * Offline speech-to-text wrapper around Android's on-device [SpeechRecognizer].
 *
 * It requests `EXTRA_PREFER_OFFLINE` so recognition runs locally when the
 * device's speech model supports it (no audio leaves the phone). Requires the
 * RECORD_AUDIO runtime permission, which the user grants on first use.
 *
 * Lifecycle: create once, call [start] to listen, [destroy] when done.
 */
class VoiceInput(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onState: (Boolean) -> Unit = {},
) {

    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(locale: Locale = Locale.getDefault()) {
        if (!isAvailable) {
            JarvisLog.error("VOICE — no speech recognition service on this device")
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(listener)
            }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Prefer fully on-device recognition (API 23+ honours this).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }
        onState(true)
        JarvisLog.info("VOICE — listening (offline-preferred)...")
        recognizer?.startListening(intent)
    }

    fun stop() {
        recognizer?.stopListening()
        onState(false)
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() = onState(false)

        override fun onError(error: Int) {
            onState(false)
            JarvisLog.error("VOICE error: ${errorText(error)}")
        }

        override fun onResults(results: Bundle?) {
            onState(false)
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (text.isNotEmpty()) {
                JarvisLog.action("VOICE -> \"$text\"")
                onResult(text)
            } else {
                JarvisLog.warn("VOICE — nothing recognised")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun errorText(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "audio"
        SpeechRecognizer.ERROR_CLIENT -> "client"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "missing RECORD_AUDIO permission"
        SpeechRecognizer.ERROR_NETWORK -> "network"
        SpeechRecognizer.ERROR_NO_MATCH -> "no match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "busy"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "speech timeout"
        else -> "code $code"
    }
}
