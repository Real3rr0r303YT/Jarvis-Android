package com.real3rr0r303yt.jarvis.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.real3rr0r303yt.jarvis.core.JarvisLog
import com.real3rr0r303yt.jarvis.trigger.AppTriggerManager

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
        // We act only on foreground-app changes, feeding the package name to the
        // proactive app-launch trigger. We do not retain any screen content.
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (pkg != null && pkg != packageName) {
                AppTriggerManager.onForegroundApp(this, pkg)
            }
        }
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

    /**
     * Types [text] into an editable field. Prefers the focused input, else the
     * first editable node in the tree; focuses it first so `ACTION_SET_TEXT`
     * lands reliably. Returns false (without logging an error) when no editable
     * field exists yet — the caller may poll and retry while the app settles.
     */
    fun typeText(text: String): Boolean {
        val target = rootInActiveWindow
            ?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findFirstEditable(rootInActiveWindow)
        if (target == null) return false

        // Make sure the field is focused before writing (chat inputs often
        // aren't focused immediately after the chat opens).
        if (!target.isFocused) target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text,
            )
        }
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
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
        val isEditText = node.className?.toString()?.contains("EditText", ignoreCase = true) == true
        if (node.isEditable || isEditText) return node
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
