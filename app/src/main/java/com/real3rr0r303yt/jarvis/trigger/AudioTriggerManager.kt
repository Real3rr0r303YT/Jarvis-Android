package com.real3rr0r303yt.jarvis.trigger

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.real3rr0r303yt.jarvis.core.JarvisLog
import kotlin.math.sqrt

/**
 * Lightweight background acoustic trigger.
 *
 * Samples the microphone at a low rate and computes short-window RMS loudness.
 * When sustained sound rises above [threshold] (after a quiet period), it fires
 * [onActivity] once, debounced by [cooldownMs]. This is amplitude-based
 * activity detection — intentionally simple and model-free; it is the hook
 * point where a wake-word / keyphrase model could later be plugged in.
 *
 * Requires RECORD_AUDIO. It only runs while [start] has been called (driven by
 * the foreground service), and stops cleanly on [stop].
 */
class AudioTriggerManager(
    private val context: Context,
    private val threshold: Double = 1500.0,
    private val cooldownMs: Long = 8_000L,
    private val onActivity: () -> Unit,
) {
    @Volatile private var running = false
    private var thread: Thread? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun start() {
        if (running) return
        if (!hasPermission()) {
            JarvisLog.error("AUDIO TRIGGER — RECORD_AUDIO not granted")
            return
        }
        running = true
        thread = Thread({ loop() }, "jarvis-audio-trigger").also { it.start() }
        JarvisLog.info("AUDIO TRIGGER listening (amplitude-based)")
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        JarvisLog.info("AUDIO TRIGGER stopped")
    }

    @SuppressLint("MissingPermission") // guarded by hasPermission() before start()
    private fun loop() {
        val sampleRate = 16_000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) {
            JarvisLog.error("AUDIO TRIGGER — unsupported audio config")
            running = false
            return
        }
        val bufSize = maxOf(minBuf, sampleRate / 4 * 2) // ~0.25s window
        var lastFired = 0L

        // Continuous listening: keep a recording session open and, if it fails,
        // re-open it after a short backoff — the loop only ends when stop() is
        // called, so Jarvis keeps listening indefinitely in the background.
        while (running) {
            var record: AudioRecord? = null
            try {
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufSize,
                )
                // Guard against an uninitialised recorder (mic busy / denied):
                // startRecording() would otherwise throw IllegalStateException.
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException("AudioRecord not initialised (mic unavailable)")
                }
                val buffer = ShortArray(bufSize / 2)
                record.startRecording()
                while (running) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read <= 0) continue
                    val rms = rms(buffer, read)
                    val now = System.currentTimeMillis()
                    if (rms >= threshold && now - lastFired >= cooldownMs) {
                        lastFired = now
                        JarvisLog.action("AUDIO EVENT detected (level ${rms.toInt()})")
                        runCatching { onActivity() }
                            .onFailure { JarvisLog.error("AUDIO TRIGGER handler failed: ${it.message}") }
                    }
                }
            } catch (e: Exception) {
                JarvisLog.error("AUDIO TRIGGER session error: ${e.message}")
            } finally {
                runCatching { record?.stop() }
                runCatching { record?.release() }
            }
            // Transient failure while still enabled: back off, then re-open.
            if (running) {
                runCatching { Thread.sleep(1000) }
            }
        }
    }

    private fun rms(buffer: ShortArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length) {
            val s = buffer[i].toDouble()
            sum += s * s
        }
        return sqrt(sum / length)
    }
}
