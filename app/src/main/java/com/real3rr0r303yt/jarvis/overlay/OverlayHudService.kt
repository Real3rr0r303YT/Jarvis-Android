package com.real3rr0r303yt.jarvis.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.real3rr0r303yt.jarvis.JarvisApplication
import com.real3rr0r303yt.jarvis.brain.VoiceInput
import com.real3rr0r303yt.jarvis.core.JarvisLog
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Minimalist, draggable cyber overlay shown over all apps. Displays a Jarvis
 * status chip and, on tap, starts a quick voice command (the recognised text is
 * routed through the app's command processor). Requires the "draw over other
 * apps" permission, which the user grants from settings.
 *
 * Implemented as a started service that manages a single WindowManager view; it
 * adds no persistent notification of its own.
 */
class OverlayHudService : Service() {

    private var windowManager: WindowManager? = null
    private var overlay: View? = null
    private var label: TextView? = null
    private var voice: VoiceInput? = null
    private val scope = CoroutineScope(SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (overlay == null) addOverlay()
        return START_STICKY
    }

    private fun addOverlay() {
        if (!canDraw(this)) {
            JarvisLog.error("OVERLAY — draw-over-apps permission not granted")
            stopSelf()
            return
        }
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val chip = TextView(this).apply {
            text = "◉ JARVIS"
            setTextColor(Color.parseColor("#00FF66"))
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(28, 18, 28, 18)
            background = GradientDrawable().apply {
                cornerRadius = 40f
                setColor(Color.parseColor("#0A0F0A"))
                setStroke(3, Color.parseColor("#00FF66"))
            }
        }
        label = chip
        val container = LinearLayout(this).apply { addView(chip) }
        overlay = container

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 180
        }

        attachDragAndTap(container, params, wm)
        wm.addView(container, params)
        JarvisLog.info("OVERLAY HUD attached")
    }

    private fun attachDragAndTap(view: View, params: WindowManager.LayoutParams, wm: WindowManager) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    touchX = event.rawX; touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    runCatching { wm.updateViewLayout(view, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = abs(event.rawX - touchX) + abs(event.rawY - touchY)
                    if (moved < 20f) {
                        v.performClick()
                        onTap()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun onTap() {
        label?.text = "◉ LISTENING"
        val v = voice ?: VoiceInput(
            context = this,
            onResult = { spoken ->
                label?.text = "◉ JARVIS"
                val processor = JarvisApplication.instance?.processor
                if (processor != null) {
                    scope.launch { processor.process(spoken) }
                }
            },
            onState = { active -> if (!active) label?.text = "◉ JARVIS" },
        ).also { voice = it }
        if (v.isAvailable) v.start() else {
            label?.text = "◉ JARVIS"
            JarvisLog.error("OVERLAY — speech recognition unavailable")
        }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    override fun onDestroy() {
        super.onDestroy()
        voice?.destroy()
        overlay?.let { runCatching { windowManager?.removeView(it) } }
        overlay = null
        JarvisLog.info("OVERLAY HUD removed")
    }

    companion object {
        const val ACTION_STOP = "com.real3rr0r303yt.jarvis.STOP_OVERLAY"

        fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

        fun start(context: Context) {
            context.startService(Intent(context, OverlayHudService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, OverlayHudService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
