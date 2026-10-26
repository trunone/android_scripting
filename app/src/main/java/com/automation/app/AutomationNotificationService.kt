package com.automation.app

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject

class AutomationNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName ?: ""
        val extras = sbn.notification?.extras

        val title = extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = extras?.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        val json = JSONObject().apply {
            put("packageName", packageName)
            put("title", title)
            put("text", text)
            put("subText", subText)
            put("postTime", sbn.postTime)
        }.toString()

        AutomationCoreService.instance?.scriptEngineManager?.emitJsEvent("NOTIFICATION_RECEIVED", json)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Option to emit onNotificationRemoved if needed in future
    }
}
