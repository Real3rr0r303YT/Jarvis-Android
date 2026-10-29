package com.real3rr0r303yt.jarvis.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.real3rr0r303yt.jarvis.core.JarvisLog

/**
 * The automation core. Once the user enables it in
 * Settings > Accessibility, Jarvis can:
 *   - read the current screen's node tree to find elements a command refers to,
 *   - tap elements by their visible text,
 *   - type text into the focused input,
 *   - perform global gestures (back, home).
 *
 * A single live instance is exposed via [instance] so the command processor can
 * drive it. All actions are logged to the HUD terminal.
 */
class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: JarvisAccessibilityService? = null
            private set

        val isConnected: Boolean get() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        JarvisLog.info("AccessibilityService connected — automation core online")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Event-driven observation is available here if needed; the command
        // processor works on demand against the live window, so we keep this
        // lightweight and do not retain any screen content.
    }

    override fun onInterrupt() {
        JarvisLog.warn("AccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
        JarvisLog.info("AccessibilityService disconnected")
    }

    // ---------------------------------------------------------- global actions
    fun goBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
        JarvisLog.action("GESTURE back")
    }

    fun goHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        JarvisLog.action("GESTURE home")
    }

    // ------------------------------------------------------------- find & tap
    /** Finds a node whose visible text contains [label] and clicks it. */
    fun tapByText(label: String): Boolean {
        val root = rootInActiveWindow ?: run {
            JarvisLog.error("TAP \"$label\" — no active window")
            return false
        }
        val node = findClickableByText(root, label)
        return if (node != null) {
            val clicked = clickNodeOrCenter(node)
            JarvisLog.action("TAP \"$label\" -> ${if (clicked) "ok" else "gesture-fallback"}")
            clicked
        } else {
            JarvisLog.error("TAP \"$label\" — element not found on screen")
            false
        }
    }

    /** Types [text] into the currently focused editable field. */
    fun typeText(text: String): Boolean {
        val focused = rootInActiveWindow
            ?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findFirstEditable(rootInActiveWindow)
        if (focused == null) {
            JarvisLog.error("TYPE — no editable field focused")
            return false
        }
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text,
            )
        }
        val ok = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        JarvisLog.action("TYPE \"${text.take(40)}\" -> ${if (ok) "ok" else "failed"}")
        return ok
    }

    // --------------------------------------------------------------- helpers
    private fun findClickableByText(
        node: AccessibilityNodeInfo,
        label: String,
    ): AccessibilityNodeInfo? {
        node.findAccessibilityNodeInfosByText(label)?.forEach { candidate ->
            var n: AccessibilityNodeInfo? = candidate
            while (n != null) {
                if (n.isClickable) return n
                n = n.parent
            }
            if (candidate.isVisibleToUser) return candidate
        }
        return null
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            findFirstEditable(node.getChild(i))?.let { return it }
        }
        return null
    }

    private fun clickNodeOrCenter(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        // Fall back to a tap gesture at the node's centre.
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        if (bounds.isEmpty) return false
        val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        return dispatchGesture(gesture, null, null)
    }
}
