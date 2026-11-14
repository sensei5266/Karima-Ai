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
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.View
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
    private val persona: String
        get() = "তুমি কারিমা, ব্যবহারকারীর ব্যক্তিগত ভয়েস অ্যাসিস্ট্যান্ট। " +
            "ব্যবহারকারী যে ভাষায় কথা বলে সেই ভাষায় ছোট ও সহজ উত্তর দাও। " +
            "উত্তরে মার্কডাউন চিহ্ন (*, #) ব্যবহার করবে না। এখনকার তারিখ ও সময়: " +
            SimpleDateFormat("EEEE, d MMMM yyyy, hh:mm a", Locale.ENGLISH).format(Date())

    private lateinit var chat: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var avatar: TextView
    private lateinit var status: TextView
    private lateinit var micBtn: Button
    private lateinit var keyPanel: LinearLayout
    private lateinit var keyInput: EditText
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var pulse: ObjectAnimator? = null
    private val history = JSONArray()
    private var models: List<String>? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun roundBg(color: String, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = radius
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0B0F1A")
        window.navigationBarColor = Color.parseColor("#0B0F1A")

        val savedKey = getSharedPreferences("k", MODE_PRIVATE).getString("key", "") ?: ""

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(36), dp(16), dp(16))
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
            text = "ট্যাপ করে কথা বলুন"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#94A3B8"))
        }

        chat = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(this).apply { addView(chat) }

        micBtn = Button(this).apply {
            text = "🎤"
            textSize = 30f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#2563EB"))
            }
            setOnClickListener { listen() }
        }

        root.addView(header)
        root.addView(keyPanel)
        root.addView(avatar, LinearLayout.LayoutParams(dp(96), dp(96)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(12)
            bottomMargin = dp(6)
        })
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        root.addView(micBtn, LinearLayout.LayoutParams(dp(72), dp(72)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(8)
        })
        setContentView(root)

        tts = TextToSpeech(this, this)
        add("কারিমা", "হ্যালো! নিচের মাইক বাটন চেপে কথা বলুন।")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts?.setLanguage(Locale("bn", "BD"))
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }
        }
    }

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
        status.text = "ট্যাপ করে কথা বলুন"
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

    private fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            add("সিস্টেম", "এই ফোনে স্পিচ রিকগনিশন পাওয়া যায়নি।")
            return
        }
        tts?.stop()
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
        }
        recognizer?.startListening(i)
    }

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
                            add("কারিমা", reply)
                            tts?.speak(reply.replace(Regex("[*#`]"), ""), TextToSpeech.QUEUE_FLUSH, null, "k")
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
