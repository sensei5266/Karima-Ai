package com.karima.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class KarimaAccessibilityService : AccessibilityService() {

    companion object {
        var instance: KarimaAccessibilityService? = null
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    fun readScreen(): String {
        val root = rootInActiveWindow ?: return "স্ক্রিন পড়া যায়নি।"
        val sb = StringBuilder()
        collect(root, sb)
        val s = sb.toString().trim()
        return if (s.isEmpty()) "স্ক্রিনে কোনো লেখা পাওয়া যায়নি।" else s.take(3500)
    }

    private fun collect(n: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (n == null || sb.length > 3500) return
        val t = n.text?.toString() ?: n.contentDescription?.toString()
        if (!t.isNullOrBlank()) {
            val tag = if (n.isEditable) "[ইনপুট] " else if (n.isClickable) "[বোতাম] " else ""
            sb.append(tag).append(t.take(90)).append('\n')
        } else if (n.isEditable) {
            sb.append("[ইনপুট ঘর খালি]\n")
        }
        for (i in 0 until n.childCount) collect(n.getChild(i), sb)
    }

    fun clickText(q: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val list = root.findAccessibilityNodeInfosByText(q)
        for (n in list) {
            var cur: AccessibilityNodeInfo? = n
            while (cur != null) {
                if (cur.isClickable) {
                    if (cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                    break
                }
                cur = cur.parent
            }
        }
        return false
    }

    private fun findEditable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isEditable) return n
        for (i in 0 until n.childCount) {
            val r = findEditable(n.getChild(i))
            if (r != null) return r
        }
        return null
    }

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditable(root) ?: return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findScrollable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isScrollable) return n
        for (i in 0 until n.childCount) {
            val r = findScrollable(n.getChild(i))
            if (r != null) return r
        }
        return null
    }

    fun scroll(forward: Boolean): Boolean {
        val node = findScrollable(rootInActiveWindow) ?: return false
        val a = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return node.performAction(a)
    }
}
