package com.real3rr0r303yt.jarvis

import android.app.Application
import com.real3rr0r303yt.jarvis.core.JarvisLog

class JarvisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        JarvisLog.info("JARVIS-AI boot sequence complete — standing by")
    }
}
