package com.karima.assistant

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private val model = "gemini-3.8-flash"

    private val actionHelp =
        " তুমি ফোনে কাজও করতে পারো। ব্যবহারকারী স্পষ্টভাবে কোনো কাজ করতে বললে উত্তরের একদম শেষে " +
        "ঠিক এই ফরম্যাটে ট্যাগ লিখবে: " +
        "[[torch:on]] বা [[torch:off]] (টর্চ); " +
        "[[alarm:HH:MM]] ২৪ ঘণ্টার সময়ে অ্যালার্ম, যেমন [[alarm:06:30]]; " +
        "[[timer:সেকেন্ড]] টাইমার, যেমন ৫ মিনিট হলে [[timer:300]]; " +
        "[[open:অ্যাপের ইংরেজি নাম]] অ্যাপ খোলা, যেমন [[open:YouTube]]; " +
        "[[call:নাম বা নম্বর]] কল; " +
        "[[sms:নাম বা নম্বর|বার্তা]] SMS; " +
        "[[whatsapp:নাম বা নম্বর|বার্তা]] WhatsApp মেসেজ; " +
        "[[volume:up]] বা [[volume:down]] ভলিউম। " +
        "কাজের আগে এক বাক্যে বলো কী করছো। শুধু প্রশ্ন বা গল্প করলে কোনো ট্যাগ দেবে না। " +
        "ইংরেজি নাম বা শব্দ সবসময় ইংরেজি অক্ষরে লিখবে, যেমন YouTube, Rahim, WhatsApp।"

    private val persona: String
        get() = "তুমি কারিমা, ব্যবহারকারীর ব্যক্তিগত ভয়েস অ্যাসিস্ট্যান্ট। " +
            "ব্যবহারকারী যে ভাষায় কথা বলে সেই ভাষায় ছোট ও সহজ উত্তর দাও। " +
            "উত্তরে মার্কডাউন চিহ্ন (*, #) ব্যবহার করবে না।" + actionHelp +
            " এখনকার তারিখ ও সময়: " +
            SimpleDateFormat("EEEE, d MMMM yyyy, hh:mm a", Locale.ENGLISH).format(Date())

    private lateinit var chat: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var avatar: TextView
    private lateinit var status: TextView
    private lateinit var micBtn: Button
    private lateinit var keyPanel: LinearLayout
    private lateinit var keyInput: EditText
    private lateinit var msgInput: EditText
    private lateinit var langChip: TextView
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var pulse: ObjectAnimator? = null
    private val history = JSONArray()
    private var models: List<String>? = null
    private var voiceLang = "bn-BD"

    private var gen = 0
    private val speakList = mutableListOf<Pair<String, Boolean>>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun roundBg(color: String, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = radius
        }

    private fun circleBg(color: String): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(color))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0B0F1A")
        window.navigationBarColor = Color.parseColor("#0B0F1A")

        val savedKey = getSharedPreferences("k", MODE_PRIVATE).getString("key", "") ?: ""

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(36), dp(16), dp(12))
            setBackgroundColor(Color.parseColor("#0B0F1A"))
        }

        keyInput = EditText(this).apply {
            hint = "Gemini API key"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(savedKey)
        }
        val saveBtn = Button(this).apply {
            text = "Key সেভ করুন"
            setTextColor(Color.WHITE)
            background = roundBg("#334155", dp(12).toFloat())
            setOnClickListener {
                getSharedPreferences("k", MODE_PRIVATE).edit()
                    .putString("key", keyInput.text.toString().trim()).apply()
                models = null
                Toast.makeText(this@MainActivity, "সেভ হয়েছে", Toast.LENGTH_SHORT).show()
                keyPanel.visibility = View.GONE
            }
        }
        keyPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (savedKey.isBlank()) View.VISIBLE else View.GONE
            addView(keyInput)
            addView(saveBtn)
        }

        val title = TextView(this).apply {
            text = "Karima"
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        langChip = TextView(this).apply {
            text = "🎙 বাংলা"
            textSize = 14f
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = roundBg("#1E293B", dp(20).toFloat())
            setOnClickListener {
                if (voiceLang == "bn-BD") {
                    voiceLang = "en-US"
                    text = "🎙 English"
                } else {
                    voiceLang = "bn-BD"
                    text = "🎙 বাংলা"
                }
            }
        }
        val gear = TextView(this).apply {
            text = "⚙"
            textSize = 26f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(dp(12), dp(4), dp(4), dp(4))
            setOnClickListener {
                keyPanel.visibility =
                    if (keyPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, -2, 1f))
            addView(langChip)
            addView(gear)
        }

        avatar = TextView(this).apply {
            text = "K"
            textSize = 40f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#38BDF8"), Color.parseColor("#7C3AED"))
            ).apply { shape = GradientDrawable.OVAL }
        }
        status = TextView(this).apply {
            text = "ট্যাপ করে কথা বলুন বা লিখুন"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#94A3B8"))
        }

        chat = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(this).apply { addView(chat) }

        msgInput = EditText(this).apply {
            hint = "মেসেজ লিখুন..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEND
            maxLines = 1
            background = roundBg("#1E293B", dp(26).toFloat())
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendTyped()
                    true
                } else false
            }
        }
        val sendBtn = Button(this).apply {
            text = "➤"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 0)
            minimumWidth = 0
            minimumHeight = 0
            background = circleBg("#7C3AED")
            setOnClickListener { sendTyped() }
        }
        micBtn = Button(this).apply {
            text = "🎤"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 0)
            minimumWidth = 0
            minimumHeight = 0
            background = circleBg("#2563EB")
            setOnClickListener { listen() }
        }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(msgInput, LinearLayout.LayoutParams(0, -2, 1f))
            addView(sendBtn, LinearLayout.LayoutParams(dp(50), dp(50)).apply { leftMargin = dp(8) })
            addView(micBtn, LinearLayout.LayoutParams(dp(56), dp(56)).apply { leftMargin = dp(8) })
        }

        root.addView(header)
        root.addView(keyPanel)
        root.addView(avatar, LinearLayout.LayoutParams(dp(96), dp(96)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(12)
            bottomMargin = dp(6)
        })
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(8)
            bottomMargin = dp(8)
        })
        root.addView(bottom, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)

        tts = TextToSpeech(this, this)
        add("কারিমা", "হ্যালো! নিচের মাইক চেপে কথা বলুন, অথবা লিখে ➤ চাপুন।")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    if (id == "g$gen") runOnUiThread { speakNext() }
                }
                override fun onError(id: String?) {
                    if (id == "g$gen") runOnUiThread { speakNext() }
                }
            })
        }
    }

    // ---------- মিশ্র ভাষায় পড়া (বাংলা + ইংরেজি) ----------

    private fun segments(text: String): List<Pair<String, Boolean>> {
        val out = mutableListOf<Pair<String, Boolean>>()
        val sb = StringBuilder()
        var curEn: Boolean? = null
        for (ch in text) {
            val isEn = ch in 'A'..'Z' || ch in 'a'..'z'
            val isBn = ch.code in 0x0980..0x09FF
            if ((isEn || isBn) && curEn != null && curEn != isEn) {
                if (sb.isNotBlank()) out.add(sb.toString().trim() to (curEn ?: false))
                sb.clear()
            }
            if (isEn || isBn) curEn = isEn
            sb.append(ch)
        }
        if (sb.isNotBlank()) out.add(sb.toString().trim() to (curEn ?: false))
        return out
    }

    private fun stopSpeaking() {
        gen++
        speakList.clear()
        tts?.stop()
    }

    private fun speakMixed(raw: String) {
        stopSpeaking()
        val text = raw.replace(Regex("[*#`_]"), "")
        speakList.addAll(segments(text).filter { seg -> seg.first.any { it.isLetterOrDigit() } })
        speakNext()
    }

    private fun speakNext() {
        if (speakList.isEmpty()) return
        val seg = speakList.removeAt(0)
        val en = seg.second
        val r = tts?.setLanguage(if (en) Locale.US else Locale("bn", "BD"))
        if (!en && (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED)) {
            tts?.setLanguage(Locale.getDefault())
        }
        tts?.speak(seg.first, TextToSpeech.QUEUE_FLUSH, null, "g$gen")
    }

    // ---------- স্ক্রিন ----------

    private fun startPulse() {
        pulse?.cancel()
        pulse = ObjectAnimator.ofPropertyValuesHolder(
            avatar,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 1.15f),
            PropertyValuesHolder.ofFloat("scaleY", 1f, 1.15f)
        ).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun idle() {
        pulse?.cancel()
        avatar.scaleX = 1f
        avatar.scaleY = 1f
        status.text = "ট্যাপ করে কথা বলুন বা লিখুন"
    }

    private fun add(who: String, text: String) {
        val isUser = who == "আপনি"
        val isSys = who == "সিস্টেম" || who == "ত্রুটি"
        val color = when {
            isUser -> "#2563EB"
            isSys -> "#7F1D1D"
            else -> "#1E293B"
        }
        val tv = TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundBg(color, dp(16).toFloat())
        }
        val lp = LinearLayout.LayoutParams(-2, -2).apply {
            topMargin = dp(5)
            bottomMargin = dp(5)
            gravity = if (isUser) Gravity.END else Gravity.START
        }
        chat.addView(tv, lp)
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun sendTyped() {
        val t = msgInput.text.toString().trim()
        if (t.isEmpty()) return
        msgInput.setText("")
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(msgInput.windowToken, 0)
        stopSpeaking()
        add("আপনি", t)
        status.text = "ভাবছি..."
        ask(t)
    }

    // ---------- ফোন কন্ট্রোল ----------

    private val tagRegex = Regex("""\[\[(\w+):([^\]]*)\]\]""")

    private fun handleReply(reply: String) {
        val tags = tagRegex.findAll(reply)
            .map { it.groupValues[1] to it.groupValues[2].trim() }
            .toList()
        val clean = tagRegex.replace(reply, "").trim()
        val shown = if (clean.isBlank()) "ঠিক আছে।" else clean
        add("কারিমা", shown)
        speakMixed(shown)
        for ((name, value) in tags) {
            val err = act(name, value)
            if (err != null) add("সিস্টেম", err)
        }
    }

    private fun act(name: String, v: String): String? {
        return try {
            when (name) {
                "torch" -> torch(v == "on")
                "alarm" -> setAlarm(v)
                "timer" -> setTimer(v)
                "open" -> openApp(v)
                "call" -> dial(v)
                "sms" -> sendSms(v)
                "whatsapp" -> sendWhatsapp(v)
                "volume" -> volume(v == "up")
                else -> "অজানা কাজ: $name"
            }
        } catch (e: Exception) {
            "কাজটি করা যায়নি: ${e.message}"
        }
    }

    private fun torch(on: Boolean): String? {
        val cm = getSystemService(CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "এই ফোনে ফ্ল্যাশলাইট পাওয়া যায়নি।"
        cm.setTorchMode(id, on)
        return null
    }

    private fun setAlarm(v: String): String? {
        val p = v.split(":")
        val h = p[0].trim().toInt()
        val m = p[1].trim().toInt()
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, m)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Karima")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        startActivity(i)
        return null
    }

    private fun setTimer(v: String): String? {
        val sec = v.trim().toInt()
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, sec)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Karima")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        startActivity(i)
        return null
    }

    private fun openApp(nameWanted: String): String? {
        val pm = packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        val q = nameWanted.trim().lowercase()
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == q }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
            ?: return "\"$nameWanted\" নামে কোনো অ্যাপ খুঁজে পাইনি।"
        val launch = pm.getLaunchIntentForPackage(hit.activityInfo.packageName)
            ?: return "অ্যাপটি খোলা যায়নি।"
        startActivity(launch)
        return null
    }

    private fun volume(up: Boolean): String? {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        repeat(3) {
            am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
        }
        return null
    }

    private fun contactPermissionMsg(q: String): String? {
        val isName = q.any { it.isLetter() }
        if (isName && checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 2)
            return "কন্ট্যাক্ট দেখার অনুমতি দিন, তারপর আবার বলুন।"
        }
        return null
    }

    private fun numberFor(q: String): String? {
        val query = q.trim()
        if (query.none { it.isLetter() }) {
            val d = query.filter { it.isDigit() || it == '+' }
            return if (d.length >= 5) d else null
        }
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
            arrayOf("%$query%"),
            null
        )?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return null
    }

    private fun dial(target: String): String? {
        contactPermissionMsg(target)?.let { return it }
        val n = numberFor(target) ?: return "\"$target\" নামে কোনো নম্বর পাইনি।"
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(n))))
        return null
    }

    private fun sendSms(v: String): String? {
        val parts = v.split("|", limit = 2)
        val target = parts[0].trim()
        val msg = parts.getOrElse(1) { "" }.trim()
        contactPermissionMsg(target)?.let { return it }
        val n = numberFor(target) ?: return "\"$target\" নামে কোনো নম্বর পাইনি।"
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(n)))
            .putExtra("sms_body", msg)
        startActivity(i)
        return null
    }

    private fun waNumber(n: String): String {
        val d = n.filter { it.isDigit() || it == '+' }
        if (d.startsWith("+")) return d.drop(1)
        if (d.startsWith("01") && d.length == 11) return "88$d"
        return d
    }

    private fun sendWhatsapp(v: String): String? {
        val parts = v.split("|", limit = 2)
        val target = parts[0].trim()
        val msg = parts.getOrElse(1) { "" }.trim()
        contactPermissionMsg(target)?.let { return it }
        val n = numberFor(target) ?: return "\"$target\" নামে কোনো নম্বর পাইনি।"
        val url = "https://wa.me/" + waNumber(n) + "?text=" + Uri.encode(msg)
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        return null
    }

    // ---------- শোনা ----------

    private fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            add("সিস্টেম", "এই ফোনে স্পিচ রিকগনিশন পাওয়া যায়নি।")
            return
        }
        stopSpeaking()
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {
                status.text = "শুনছি..."
                startPulse()
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "ভাবছি..." }
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onError(e: Int) {
                idle()
                add("সিস্টেম", "শুনতে পাইনি (কোড $e), আবার চেষ্টা করুন।")
            }
            override fun onResults(r: Bundle?) {
                val said = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (said.isNullOrBlank()) {
                    idle()
                    return
                }
                add("আপনি", said)
                ask(said)
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceLang)
        }
        recognizer?.startListening(i)
    }

    // ---------- AI ----------

    private fun pickModels(key: String): List<String> {
        val found = mutableListOf<String>()
        try {
            val c = URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=100")
                .openConnection() as HttpURLConnection
            c.setRequestProperty("x-goog-api-key", key)
            c.connectTimeout = 8000
            c.readTimeout = 8000
            if (c.responseCode in 200..299) {
                val arr = JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("models")
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    val name = m.getString("name").removePrefix("models/")
                    val methods = m.optJSONArray("supportedGenerationMethods")?.toString() ?: ""
                    val bad = listOf("image", "tts", "live", "embed", "audio", "robotics", "computer")
                    if (name.contains("flash") && methods.contains("generateContent") &&
                        bad.none { name.contains(it) }
                    ) found.add(name)
                }
            }
        } catch (e: Exception) {
        }
        val list = mutableListOf(model)
        list.addAll(found.sortedDescending().filter { it != model }.take(4))
        return list
    }

    private fun ask(userText: String) {
        val key = getSharedPreferences("k", MODE_PRIVATE).getString("key", "") ?: ""
        if (key.isBlank()) {
            idle()
            add("সিস্টেম", "আগে ⚙ চেপে API key বসিয়ে সেভ করুন।")
            return
        }
        history.put(JSONObject().put("role", "user")
            .put("parts", JSONArray().put(JSONObject().put("text", userText))))

        thread {
            var lastErr = ""
            val list = models ?: pickModels(key).also { models = it }
            for (m in list) {
                try {
                    val body = JSONObject()
                        .put("system_instruction", JSONObject()
                            .put("parts", JSONArray().put(JSONObject().put("text", persona))))
                        .put("contents", history)

                    val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent")
                    val c = url.openConnection() as HttpURLConnection
                    c.requestMethod = "POST"
                    c.setRequestProperty("Content-Type", "application/json")
                    c.setRequestProperty("x-goog-api-key", key)
                    c.doOutput = true
                    c.connectTimeout = 10000
                    c.readTimeout = 20000
                    c.outputStream.use { it.write(body.toString().toByteArray()) }

                    val code = c.responseCode
                    val stream = if (code in 200..299) c.inputStream else c.errorStream
                    val resp = stream.bufferedReader().readText()

                    if (code in 200..299) {
                        val reply = JSONObject(resp).getJSONArray("candidates").getJSONObject(0)
                            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                        history.put(JSONObject().put("role", "model")
                            .put("parts", JSONArray().put(JSONObject().put("text", reply))))
                        runOnUiThread {
                            idle()
                            handleReply(reply)
                        }
                        return@thread
                    }
                    val msg = try {
                        JSONObject(resp).getJSONObject("error").getString("message").take(100)
                    } catch (e: Exception) { "" }
                    lastErr = "$m: HTTP $code $msg"
                    if (code != 503 && code != 429 && code != 500 && code != 404) break
                } catch (e: Exception) {
                    lastErr = "$m: ${e.message ?: "অজানা সমস্যা"}"
                }
                Thread.sleep(1000)
            }
            history.remove(history.length() - 1)
            runOnUiThread {
                idle()
                add("ত্রুটি", "$lastErr — একটু পরে আবার বলুন।")
            }
        }
    }

    override fun onDestroy() {
        pulse?.cancel()
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
