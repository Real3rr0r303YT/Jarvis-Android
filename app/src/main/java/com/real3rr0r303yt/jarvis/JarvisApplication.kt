package com.real3rr0r303yt.jarvis

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.real3rr0r303yt.jarvis.brain.CommandProcessor
import com.real3rr0r303yt.jarvis.core.JarvisLog
import com.real3rr0r303yt.jarvis.core.JarvisPrefs
import com.real3rr0r303yt.jarvis.trigger.AppTriggerManager
import com.real3rr0r303yt.jarvis.trigger.TriggerNotifier
import com.real3rr0r303yt.jarvis.tts.TtsManager

class JarvisApplication : Application() {

    /** App-scoped command brain, shared by the UI and the background services. */
    val processor: CommandProcessor by lazy { CommandProcessor(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Text-to-speech, gated by the user's settings toggle.
        TtsManager.init(this)
        TtsManager.enabled = JarvisPrefs.get(this).ttsEnabled

        // Proactive app-launch handler: when a watched app opens, generate a
        // content draft, copy it to the clipboard (auto-drafting) and show it.
        AppTriggerManager.handler = { pkg ->
            val label = AppTriggerManager.label(pkg)
            val draft = processor.runContentTrigger(label)
            copyToClipboard("Jarvis draft · $label", draft)
            TriggerNotifier.show(this, "Jarvis · $label draft (copied)", draft)
        }

        JarvisLog.info("JARVIS-AI boot sequence complete — standing by")
    }

    /** Copies [text] to the system clipboard on the main thread. */
    private fun copyToClipboard(label: String, text: String) {
        Handler(Looper.getMainLooper()).post {
            runCatching {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText(label, text))
                JarvisLog.action("CONTENT DRAFT copied to clipboard (${text.length} chars)")
            }.onFailure { JarvisLog.error("Clipboard copy failed: ${it.message}") }
        }
    }

    companion object {
        @Volatile
        var instance: JarvisApplication? = null
            private set
    }
}
