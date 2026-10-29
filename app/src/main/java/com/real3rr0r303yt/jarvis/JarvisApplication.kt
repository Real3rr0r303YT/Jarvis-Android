package com.real3rr0r303yt.jarvis

import android.app.Application
import com.real3rr0r303yt.jarvis.brain.CommandProcessor
import com.real3rr0r303yt.jarvis.core.JarvisLog
import com.real3rr0r303yt.jarvis.trigger.AppTriggerManager
import com.real3rr0r303yt.jarvis.trigger.TriggerNotifier

class JarvisApplication : Application() {

    /** App-scoped command brain, shared by the UI and the background services. */
    val processor: CommandProcessor by lazy { CommandProcessor(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Install the proactive app-launch handler: when a watched app opens,
        // generate content ideas and surface them as a notification.
        AppTriggerManager.handler = { pkg ->
            val label = AppTriggerManager.label(pkg)
            val ideas = processor.runContentTrigger(label)
            TriggerNotifier.show(this, "Jarvis · $label ideas", ideas)
        }

        JarvisLog.info("JARVIS-AI boot sequence complete — standing by")
    }

    companion object {
        @Volatile
        var instance: JarvisApplication? = null
            private set
    }
}
