package com.karima.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private val model = "gemini-3.8-flash"
    private val persona = "তুমি কারিমা, ব্যবহারকারীর ব্যক্তিগত ভয়েস অ্যাসিস্ট্যান্ট। " +
        "ব্যবহারকারী যে ভাষায় কথা বলে সেই ভাষায় ছোট ও সহজ উত্তর দাও। " +
        "উত্তরে মার্কডাউন চিহ্ন (*, #) ব্যবহার করবে না।"

    private lateinit var log: TextView
    private lateinit var scroll: ScrollView
    private lateinit var micBtn: Button
    private lateinit var keyInput: EditText
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private val history = JSONArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            setBackgroundColor(Color.parseColor("#0B0F1A"))
        }
        val title = TextView(this).apply {
            text = "Karima"
            textSize = 26f
            setTextColor(Color.WHITE)
        }
        log = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#E0E6F0"))
        }
        scroll = ScrollView(this).apply { addView(log) }

        keyInput = EditText(this).apply {
            hint = "Gemini API key"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(getSharedPreferences("k", MODE_PRIVATE).getString("key", ""))
        }
        val saveBtn = Button(this).apply {
            text = "Key সেভ করুন"
            setOnClickListener {
                getSharedPreferences("k", MODE_PRIVATE).edit()
                    .putString("key", keyInput.text.toString().trim()).apply()
                Toast.makeText(this@MainActivity, "সেভ হয়েছে", Toast.LENGTH_SHORT).show()
            }
        }
        micBtn = Button(this).apply {
            text = "🎤 কথা বলুন"
            textSize = 18f
            setOnClickListener { listen() }
        }

        root.addView(title)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(keyInput)
        root.addView(saveBtn)
        root.addView(micBtn)
        setContentView(root)

        tts = TextToSpeech(this, this)
        add("কারিমা", "হ্যালো! মাইক বাটন চেপে কথা বলুন।")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts?.setLanguage(Locale("bn", "BD"))
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }
        }
    }

    private fun add(who: String, text: String) {
        log.append("\n$who: $text\n")
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
            override fun onReadyForSpeech(p: Bundle?) { micBtn.text = "🎧 শুনছি..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { micBtn.text = "🎤 কথা বলুন" }
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onError(e: Int) {
                micBtn.text = "🎤 কথা বলুন"
                add("সিস্টেম", "শুনতে পাইনি (কোড $e), আবার চেষ্টা করুন।")
            }
            override fun onResults(r: Bundle?) {
                micBtn.text = "🎤 কথা বলুন"
                val said = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (said.isNullOrBlank()) return
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

    private fun ask(userText: String) {
        val key = getSharedPreferences("k", MODE_PRIVATE).getString("key", "") ?: ""
        if (key.isBlank()) {
            add("সিস্টেম", "আগে API key বসিয়ে সেভ করুন।")
            return
        }
        history.put(JSONObject().put("role", "user")
            .put("parts", JSONArray().put(JSONObject().put("text", userText))))

        thread {
            var lastErr = ""
            val tries = listOf(model, model, "gemini-3.8-flash-lite", model)
            for (m in tries) {
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
                    c.connectTimeout = 15000
                    c.readTimeout = 30000
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
                            add("কারিমা", reply)
                            tts?.speak(reply.replace(Regex("[*#`]"), ""), TextToSpeech.QUEUE_FLUSH, null, "k")
                        }
                        return@thread
                    }
                    lastErr = "HTTP $code"
                    if (code != 503 && code != 429 && code != 500 && code != 404) break
                } catch (e: Exception) {
                    lastErr = e.message ?: "অজানা সমস্যা"
                }
                Thread.sleep(2500)
            }
            history.remove(history.length() - 1)
            runOnUiThread { add("ত্রুটি", "$lastErr — একটু পরে আবার বলুন।") }
        }
    }
        val key = getSharedPreferences("k", MODE_PRIVATE).getString("key", "") ?: ""
        if (key.isBlank()) {
            add("সিস্টেম", "আগে API key বসিয়ে সেভ করুন।")
            return
        }
        history.put(JSONObject().put("role", "user")
            .put("parts", JSONArray().put(JSONObject().put("text", userText))))

        thread {
            try {
                val body = JSONObject()
                    .put("system_instruction", JSONObject()
                        .put("parts", JSONArray().put(JSONObject().put("text", persona))))
                    .put("contents", history)

                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                val c = url.openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("x-goog-api-key", key)
                c.doOutput = true
                c.connectTimeout = 15000
                c.readTimeout = 30000
                c.outputStream.use { it.write(body.toString().toByteArray()) }

                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val resp = stream.bufferedReader().readText()
                if (code !in 200..299) throw Exception("HTTP $code: ${resp.take(200)}")

                val reply = JSONObject(resp).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")

                history.put(JSONObject().put("role", "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", reply))))

                runOnUiThread {
                    add("কারিমা", reply)
                    tts?.speak(reply.replace(Regex("[*#`]"), ""), TextToSpeech.QUEUE_FLUSH, null, "k")
                }
            } catch (e: Exception) {
                history.remove(history.length() - 1)
                runOnUiThread { add("ত্রুটি", e.message ?: "অজানা সমস্যা") }
            }
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
