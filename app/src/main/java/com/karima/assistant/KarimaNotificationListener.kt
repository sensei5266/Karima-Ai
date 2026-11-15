package com.karima.assistant

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.CopyOnWriteArrayList

class KarimaNotificationListener : NotificationListenerService() {

    companion object {
        val recent = CopyOnWriteArrayList<String>()
        var connected = false
    }

    override fun onListenerConnected() {
        connected = true
    }

    override fun onListenerDisconnected() {
        connected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName == packageName) return
        val e = sbn.notification.extras
        val title = e.getCharSequence("android.title")?.toString() ?: ""
        val text = e.getCharSequence("android.text")?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return
        val app = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        } catch (ex: Exception) {
            sbn.packageName
        }
        recent.add("$app: $title - $text".take(200))
        while (recent.size > 30) recent.removeAt(0)
    }
}
