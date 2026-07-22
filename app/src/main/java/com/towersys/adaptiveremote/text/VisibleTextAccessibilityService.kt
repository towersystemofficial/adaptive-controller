package com.towersys.adaptiveremote.text

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow

object VisibleTextState {
    val latestText = MutableStateFlow("")
    val sourceApp = MutableStateFlow("")
    val isServiceConnected = MutableStateFlow(false)
}

class VisibleTextAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        VisibleTextState.isServiceConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        val packageName = root.packageName?.toString()
            ?: event?.packageName?.toString().orEmpty()
        if (packageName.isBlank() || IGNORED_PACKAGE_PREFIXES.any(packageName::startsWith)) return
        val text = buildList { collectText(root, this) }
            .distinct()
            .joinToString("\n")
            .take(MAX_CAPTURE_CHARS)
        if (text.isNotBlank()) {
            VisibleTextState.latestText.value = text
            VisibleTextState.sourceApp.value = packageName
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        VisibleTextState.isServiceConnected.value = false
        super.onDestroy()
    }

    private fun collectText(node: AccessibilityNodeInfo, output: MutableList<String>) {
        node.text?.toString()?.trim()?.takeIf(String::isNotBlank)?.let(output::add)
        node.contentDescription?.toString()?.trim()?.takeIf(String::isNotBlank)?.let(output::add)
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child ->
                collectText(child, output)
                child.recycle()
            }
        }
    }

    companion object {
        private const val MAX_CAPTURE_CHARS = 12_000
        private val IGNORED_PACKAGE_PREFIXES = listOf(
            "com.towersys.adaptiveremote",
            "com.android.systemui",
            "com.google.android.inputmethod",
        )
    }
}
