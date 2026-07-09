package com.example.ui.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class DaoAccessibilityService : AccessibilityService() {

    companion object {
        var instance: DaoAccessibilityService? = null
        private val _actions = MutableSharedFlow<UiAction>()
        val actions = _actions.asSharedFlow()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    fun findAndTap(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }
        return false
    }

    fun scrollForward(): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollables = root.findAccessibilityNodeInfosByViewId("android:id/list")
        for (node in scrollables) {
            if (node.isScrollable) {
                node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                return true
            }
        }
        return false
    }

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null) {
            val args = android.os.Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            return true
        }
        return false
    }

    fun pressBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun pressHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun getScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        collectText(root, sb)
        return sb.toString()
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        if (node.text != null) sb.appendLine(node.text)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectText(child, sb)
            }
        }
    }

    fun findAndLongPress(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (node.isLongClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                return true
            }
        }
        return false
    }

    fun scrollBackward(): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollables = findScrollables(root)
        for (node in scrollables) {
            node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            return true
        }
        return false
    }

    fun swipeLeft(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun swipeRight(): Boolean {
        val root = rootInActiveWindow ?: return false
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun findScrollables(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        if (node.isScrollable) result.add(node)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                result.addAll(findScrollables(child))
            }
        }
        return result
    }
}

data class UiAction(
    val type: String, // "tap", "scroll", "type", "back", "home", "read_screen"
    val target: String = ""
)
