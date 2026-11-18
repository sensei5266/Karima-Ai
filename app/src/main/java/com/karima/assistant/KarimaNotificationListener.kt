package com.karima.assistant

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

class KarimaNotificationListener : NotificationListenerService(), TextToSpeech.OnInitListener {

    companion object {
        val recent = CopyOnWriteArrayList<String>()
        var connected = false
    }

    private var tts: TextToSpeech? = null
    private var ready = false
    private var lastSpoken = ""
    private var lastAt = 0L
    private val msgPkgs = listOf(
        "whatsapp", "messaging", "mms", "telegram", "orca", "messenger", "signal", "sms"
    )

    override fun onListenerConnected() {
        connected = true
        if (tts == null) tts = TextToSpeech(applicationContext, this)
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
    }

    override fun onListenerDisconnected() {
        connected = false
    }

    override fun onDestroy() {
        try {
            tts?.shutdown()
        } catch (e: Exception) {
        }
        tts = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        val e = n.extras
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

        val on = getSharedPreferences("k", Context.MODE_PRIVATE).getBoolean("autoread", false)
        if (!on || !ready || AppState.visible) return
        if (text.isBlank()) return
        val low = sbn.packageName.lowercase()
        val isMsg = n.category == Notification.CATEGORY_MESSAGE ||
            n.category == Notification.CATEGORY_EMAIL ||
            msgPkgs.any { low.contains(it) }
        if (!isMsg) return

        val k = "$title|$text"
        val now = System.currentTimeMillis()
        if (k == lastSpoken && now - lastAt < 6000L) return
        lastSpoken = k
        lastAt = now

        val who = if (title.isBlank()) app else title
        val body = text.take(160)
        val bn = (who + body).any { it.code in 0x0980..0x09FF }
        val say = if (bn) "$who লিখেছে: $body" else "$who says: $body"
        val r = tts?.setLanguage(if (bn) Locale("bn", "BD") else Locale.US)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.getDefault())
        }
        tts?.speak(say, TextToSpeech.QUEUE_ADD, null, "n$now")
    }
}
