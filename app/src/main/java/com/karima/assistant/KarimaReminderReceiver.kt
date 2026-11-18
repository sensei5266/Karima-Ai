package com.karima.assistant

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object Reminders {

    private fun sp(ctx: Context) = ctx.getSharedPreferences("k", Context.MODE_PRIVATE)

    private fun raw(ctx: Context): JSONArray = try {
        JSONArray(sp(ctx).getString("rem", "[]"))
    } catch (e: Exception) {
        JSONArray()
    }

    private fun save(ctx: Context, a: JSONArray) {
        sp(ctx).edit().putString("rem", a.toString()).apply()
    }

    private fun pending(ctx: Context, id: Int, ms: Long, text: String, daily: Boolean): PendingIntent {
        val i = Intent(ctx, KarimaReminderReceiver::class.java)
            .putExtra("id", id)
            .putExtra("ms", ms)
            .putExtra("text", text)
            .putExtra("daily", daily)
        return PendingIntent.getBroadcast(
            ctx, id, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun setAlarm(ctx: Context, id: Int, ms: Long, text: String, daily: Boolean): Boolean {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(ctx, id, ms, text, daily)
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            return false
        }
        return exact
    }

    fun add(ctx: Context, ms: Long, text: String, daily: Boolean): Boolean {
        val id = (System.currentTimeMillis() % 1000000000L).toInt()
        val exact = setAlarm(ctx, id, ms, text, daily)
        val a = raw(ctx)
        a.put(JSONObject().put("id", id).put("ms", ms).put("text", text).put("daily", daily))
        save(ctx, a)
        return exact
    }

    fun list(ctx: Context): List<JSONObject> {
        val now = System.currentTimeMillis()
        val out = mutableListOf<JSONObject>()
        val keep = JSONArray()
        val a = raw(ctx)
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            if (o.getBoolean("daily") || o.getLong("ms") > now - 60000L) {
                out.add(o)
                keep.put(o)
            }
        }
        save(ctx, keep)
        return out
    }

    fun remove(ctx: Context, id: Int) {
        val a = raw(ctx)
        val keep = JSONArray()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            if (o.getInt("id") != id) keep.put(o)
        }
        save(ctx, keep)
    }

    fun cancelAll(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val a = raw(ctx)
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            am.cancel(
                pending(ctx, o.getInt("id"), o.getLong("ms"), o.getString("text"), o.getBoolean("daily"))
            )
        }
        save(ctx, JSONArray())
    }

    fun nextDaily(ctx: Context, id: Int, ms: Long, text: String) {
        var next = ms + 86400000L
        val now = System.currentTimeMillis()
        while (next <= now) next += 86400000L
        setAlarm(ctx, id, next, text, true)
        val a = raw(ctx)
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            if (o.getInt("id") == id) o.put("ms", next)
        }
        save(ctx, a)
    }

    fun rescheduleAll(ctx: Context) {
        val now = System.currentTimeMillis()
        val a = raw(ctx)
        val keep = JSONArray()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val daily = o.getBoolean("daily")
            var t = o.getLong("ms")
            if (t <= now) {
                if (!daily) continue
                while (t <= now) t += 86400000L
            }
            o.put("ms", t)
            keep.put(o)
            setAlarm(ctx, o.getInt("id"), t, o.getString("text"), daily)
        }
        save(ctx, keep)
    }
}

class KarimaReminderReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 0)
        val ms = intent.getLongExtra("ms", System.currentTimeMillis())
        val text = intent.getStringExtra("text") ?: "রিমাইন্ডার"
        val daily = intent.getBooleanExtra("daily", false)

        if (daily) Reminders.nextDaily(ctx, id, ms, text) else Reminders.remove(ctx, id)

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("karima_remind", "Karima রিমাইন্ডার", NotificationManager.IMPORTANCE_HIGH)
        )
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, "karima_remind")
            .setContentTitle("কারিমার রিমাইন্ডার")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)

        speak(ctx, text)
    }

    private fun speak(ctx: Context, text: String) {
        val pr = goAsync()
        val h = Handler(Looper.getMainLooper())
        var tts: TextToSpeech? = null
        var done = false

        fun finish() {
            if (done) return
            done = true
            try {
                tts?.shutdown()
            } catch (e: Exception) {
            }
            try {
                pr.finish()
            } catch (e: Exception) {
            }
        }

        h.postDelayed({ finish() }, 9000L)

        tts = TextToSpeech(ctx.applicationContext) { st ->
            if (st == TextToSpeech.SUCCESS) {
                val bn = text.any { it.code in 0x0980..0x09FF }
                val r = tts?.setLanguage(if (bn) Locale("bn", "BD") else Locale.US)
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(u: String?) {}
                    override fun onDone(u: String?) {
                        h.post { finish() }
                    }
                    override fun onError(u: String?) {
                        h.post { finish() }
                    }
                })
                val say = (if (bn) "রিমাইন্ডার: " else "Reminder: ") + text
                tts?.speak(say, TextToSpeech.QUEUE_FLUSH, null, "r")
            } else {
                finish()
            }
        }
    }
}

class KarimaBootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Reminders.rescheduleAll(ctx)
    }
}
