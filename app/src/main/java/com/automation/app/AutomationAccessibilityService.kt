package com.automation.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

class AutomationAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: ""
        val textItems = mutableListOf<String>()

        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            collectTextNodes(rootNode, textItems)
        } else {
            // Fallback to event texts if root node is not available
            event.text.forEach { charSeq ->
                if (!charSeq.isNullOrBlank()) {
                    textItems.add(charSeq.toString())
                }
            }
        }

        val json = JSONObject().apply {
            put("packageName", packageName)
            put("eventType", event.eventType)
            val jsonArray = JSONArray()
            textItems.forEach { jsonArray.put(it) }
            put("textItems", jsonArray)
        }.toString()

        AutomationCoreService.instance?.scriptEngineManager?.emitJsEvent("UI_CHANGED", json)
    }

    private fun collectTextNodes(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return

        val text = node.text
        if (!text.isNullOrBlank()) {
            list.add(text.toString())
        }
        val contentDesc = node.contentDescription
        if (!contentDesc.isNullOrBlank() && contentDesc.toString() != text?.toString()) {
            list.add(contentDesc.toString())
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectTextNodes(child, list)
                @Suppress("DEPRECATION")
                child.recycle()
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    companion object {
        @Volatile
        var instance: AutomationAccessibilityService? = null
            private set
    }
}
