package com.karima.assistant

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

object AppState {
    @Volatile
    var visible = false
}

fun makeRecognizer(ctx: Context): SpeechRecognizer {
    try {
        val def = Settings.Secure.getString(ctx.contentResolver, "voice_recognition_service")
        val cn = if (def.isNullOrBlank()) null else ComponentName.unflattenFromString(def)
        if (cn != null && cn.packageName != ctx.packageName) {
            return SpeechRecognizer.createSpeechRecognizer(ctx, cn)
        }
    } catch (e: Exception) {
    }
    try {
        val list = ctx.packageManager.queryIntentServices(
            Intent(RecognitionService.SERVICE_INTERFACE), 0
        )
        val pick = list.firstOrNull { it.serviceInfo.packageName != ctx.packageName }
        if (pick != null) {
            return SpeechRecognizer.createSpeechRecognizer(
                ctx, ComponentName(pick.serviceInfo.packageName, pick.serviceInfo.name)
            )
        }
    } catch (e: Exception) {
    }
    return SpeechRecognizer.createSpeechRecognizer(ctx)
}

class KarimaListenService : Service() {

    companion object {
        @Volatile
        var current: KarimaListenService? = null
    }

    private var rec: SpeechRecognizer? = null
    private val h = Handler(Looper.getMainLooper())
    private var alive = false
    private var langIdx = 0
    private var lastTrigger = 0L
    private val langs = listOf("bn-BD", "en-US")
    private val words = listOf(
        "কারিমা", "করিমা", "কারীমা", "করীমা", "কেরিমা", "কারিমাহ",
        "karima", "kareema", "carima", "kerima", "kareemah"
    )

    override fun onBind(intent: Intent?): IBinder? = null

    private fun makeChannels() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("karima_bg", "Karima শুনছে", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel("karima_tap", "Karima ডাক", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        makeChannels()
        val n = Notification.Builder(this, "karima_bg")
            .setContentTitle("Karima শুনছে")
            .setContentText("\"কারিমা\" বলে ডাকুন")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(1, n)
            }
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        current = this
        if (!alive) {
            alive = true
            loop()
        }
        return START_STICKY
    }

    fun pauseForApp() {
        h.removeCallbacksAndMessages(null)
        rec?.destroy()
        rec = null
        h.postDelayed({ loop() }, 2500)
    }

    private fun loop() {
        if (!alive) return
        if (AppState.visible) {
            h.postDelayed({ loop() }, 2500)
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        try {
            rec?.destroy()
            rec = makeRecognizer(this)
            rec?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(t: Int, p: Bundle?) {}
                override fun onPartialResults(p: Bundle?) {
                    check(p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
                }
                override fun onError(e: Int) {
                    langIdx++
                    h.postDelayed({ loop() }, if (e == 8 || e == 12 || e == 13) 2500L else 600L)
                }
                override fun onResults(r: Bundle?) {
                    val matched = check(r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
                    if (!matched) {
                        langIdx++
                        h.postDelayed({ loop() }, 300)
                    }
                }
            })
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, langs[langIdx % langs.size])
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            rec?.startListening(i)
        } catch (e: Exception) {
            h.postDelayed({ loop() }, 4000)
        }
    }

    private fun check(list: List<String>?): Boolean {
        if (list == null) return false
        for (t in list) {
            val low = t.lowercase()
            if (words.any { low.contains(it) }) {
                h.post { trigger() }
                return true
            }
        }
        return false
    }

    private fun trigger() {
        val now = System.currentTimeMillis()
        if (now - lastTrigger < 8000) return
        lastTrigger = now
        h.removeCallbacksAndMessages(null)
        rec?.destroy()
        rec = null
        val i = Intent(this, MainActivity::class.java)
            .putExtra("wake", true)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        try {
            startActivity(i)
        } catch (e: Exception) {
        }
        if (!Settings.canDrawOverlays(this)) tapNotification(i)
        h.postDelayed({ loop() }, 6000)
    }

    private fun tapNotification(i: Intent) {
        val pi = PendingIntent.getActivity(
            this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, "karima_tap")
            .setContentTitle("কারিমা শুনেছে")
            .setContentText("কথা বলতে এখানে ট্যাপ করুন")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(2, n)
    }

    override fun onDestroy() {
        alive = false
        h.removeCallbacksAndMessages(null)
        rec?.destroy()
        rec = null
        current = null
        super.onDestroy()
    }
}
