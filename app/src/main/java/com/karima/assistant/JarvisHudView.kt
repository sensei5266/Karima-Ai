package com.karima.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*
import kotlin.random.Random

/**
 * Stonic AI + J.A.R.V.I.S. Holographic Particle & Arc Reactor HUD View
 * Full-native Android Canvas Rendering for KARIMA TRINITY AI
 */
class JarvisHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class HudState {
        IDLE, LISTENING, THINKING, SPEAKING, LOVING
    }

    private data class Particle(
        var angle: Float,
        val radiusFactor: Float,
        val speed: Float,
        val sizeDp: Float,
        val alphaFactor: Float
    )

    var currentState = HudState.IDLE
        private set

    var personaTag: String = "KARIMA"
        set(value) {
            field = value
            postInvalidate()
        }

    var onCoreClickListener: (() -> Unit)? = null

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
    }

    private var rot1 = 0f
    private var rot2 = 0f
    private var rot3 = 0f
    private var pulse = 1f
    private var pulseDirection = 0.008f
    private var audioLevel = 0f
    private var animTime = 0L

    private var primaryColor = Color.parseColor("#00F2FE")
    private var secondaryColor = Color.parseColor("#0072FF")
    private var coreGlowColor = Color.parseColor("#00F2FE")
    private var accentColor = Color.parseColor("#FF2D75")

    private val dashEffect1 = DashPathEffect(floatArrayOf(24f, 14f), 0f)
    private val dashEffect3 = DashPathEffect(floatArrayOf(40f, 20f, 10f, 20f), 0f)

    private val particles = List(28) {
        Particle(
            angle = Random.nextFloat() * 360f,
            radiusFactor = 0.48f + Random.nextFloat() * 0.50f,
            speed = (if (Random.nextBoolean()) 1f else -1f) * (0.35f + Random.nextFloat() * 1.15f),
            sizeDp = 1.4f + Random.nextFloat() * 2.4f,
            alphaFactor = 0.35f + Random.nextFloat() * 0.65f
        )
    }

    init {
        ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 10000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val speedMul = when (currentState) {
                    HudState.THINKING -> 2.1f
                    HudState.LISTENING, HudState.SPEAKING -> 1.5f + audioLevel * 1.2f
                    else -> 1f
                }
                rot1 = (rot1 + 0.8f * speedMul) % 360f
                rot2 = (rot2 - 1.4f * speedMul) % 360f
                rot3 = (rot3 + 2.2f * speedMul) % 360f

                for (p in particles) {
                    p.angle = (p.angle + p.speed * speedMul) % 360f
                }

                pulse += pulseDirection
                if (pulse > 1.15f) pulseDirection = -0.006f
                if (pulse < 0.92f) pulseDirection = 0.006f
                audioLevel *= 0.88f

                postInvalidateOnAnimation()
            }
            start()
        }
    }

    fun setAudioLevel(rmsDb: Float) {
        val norm = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)
        if (norm > audioLevel) {
            audioLevel = norm
        }
    }

    fun setState(state: HudState) {
        currentState = state
        when (state) {
            HudState.IDLE -> {
                primaryColor = Color.parseColor("#00F2FE")
                secondaryColor = Color.parseColor("#0072FF")
                coreGlowColor = Color.parseColor("#00F2FE")
            }
            HudState.LISTENING -> {
                primaryColor = Color.parseColor("#00F2FE")
                secondaryColor = Color.parseColor("#FFE600")
                coreGlowColor = Color.parseColor("#00F2FE")
            }
            HudState.THINKING -> {
                primaryColor = Color.parseColor("#A855F7")
                secondaryColor = Color.parseColor("#00F2FE")
                coreGlowColor = Color.parseColor("#8E2DE2")
            }
            HudState.SPEAKING -> {
                primaryColor = Color.parseColor("#38BDF8")
                secondaryColor = Color.parseColor("#00F2FE")
                coreGlowColor = Color.parseColor("#0284C7")
            }
            HudState.LOVING -> {
                primaryColor = Color.parseColor("#FF2D75")
                secondaryColor = Color.parseColor("#00F2FE")
                coreGlowColor = Color.parseColor("#FF007F")
            }
        }
        postInvalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val cx = width / 2f
            val cy = height / 2f
            val dx = event.x - cx
            val dy = event.y - cy
            val dist = sqrt(dx * dx + dy * dy)
            if (dist <= width * 0.45f) {
                onCoreClickListener?.invoke()
                return true
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        animTime = SystemClock.uptimeMillis()

        val cx = width / 2f
        val cy = height / 2f
        val baseR = min(cx, cy) * 0.88f

        // 1. Ambient Background Core Glow
        val activeGlow = currentState == HudState.LISTENING || currentState == HudState.SPEAKING
        fillPaint.shader = RadialGradient(
            cx, cy, baseR * 1.1f,
            intArrayOf(
                adjustAlpha(coreGlowColor, if (activeGlow) 0.36f + audioLevel * 0.20f else 0.18f),
                adjustAlpha(secondaryColor, 0.05f),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, baseR * 1.1f, fillPaint)
        fillPaint.shader = null

        // 1b. Stonic AI Orbiting Particle Field
        for (p in particles) {
            val rad = Math.toRadians(p.angle.toDouble())
            val pr = baseR * (p.radiusFactor + audioLevel * 0.08f)
            val px = cx + (pr * cos(rad)).toFloat()
            val py = cy + (pr * sin(rad)).toFloat()
            fillPaint.color = adjustAlpha(
                if (currentState == HudState.LOVING) accentColor else primaryColor,
                p.alphaFactor
            )
            canvas.drawCircle(px, py, dp(p.sizeDp), fillPaint)
        }

        // 2. Outermost Rotating Degree Ring (Outer HUD Reticle)
        arcPaint.color = adjustAlpha(primaryColor, 0.45f)
        arcPaint.strokeWidth = dp(1.5f)
        arcPaint.pathEffect = null
        canvas.drawCircle(cx, cy, baseR, arcPaint)

        canvas.save()
        canvas.rotate(rot1, cx, cy)
        for (i in 0 until 36) {
            val isMajor = i % 9 == 0
            val tickLen = if (isMajor) dp(8f) else dp(4f)
            arcPaint.strokeWidth = if (isMajor) dp(2f) else dp(1f)
            arcPaint.color = if (isMajor) primaryColor else adjustAlpha(primaryColor, 0.35f)
            canvas.drawLine(cx, cy - baseR, cx, cy - baseR + tickLen, arcPaint)
            canvas.rotate(10f, cx, cy)
        }
        canvas.restore()

        // 3. Segmented Holographic Arcs (Clockwise)
        arcPaint.color = primaryColor
        arcPaint.strokeWidth = dp(3.5f)
        arcPaint.pathEffect = null
        val rectOuter = RectF(cx - baseR * 0.88f, cy - baseR * 0.88f, cx + baseR * 0.88f, cy + baseR * 0.88f)
        canvas.drawArc(rectOuter, rot1, 70f, false, arcPaint)
        canvas.drawArc(rectOuter, rot1 + 120f, 70f, false, arcPaint)
        canvas.drawArc(rectOuter, rot1 + 240f, 70f, false, arcPaint)

        // 4. Counter-rotating Dashed Ring
        arcPaint.color = adjustAlpha(secondaryColor, 0.75f)
        arcPaint.strokeWidth = dp(2f)
        arcPaint.pathEffect = dashEffect1
        val rectMid1 = RectF(cx - baseR * 0.74f, cy - baseR * 0.74f, cx + baseR * 0.74f, cy + baseR * 0.74f)
        canvas.drawArc(rectMid1, rot2, 360f, false, arcPaint)

        // 5. Technical Radar Reticle Arcs
        arcPaint.color = adjustAlpha(primaryColor, 0.5f)
        arcPaint.strokeWidth = dp(1.5f)
        arcPaint.pathEffect = dashEffect3
        val rectMid2 = RectF(cx - baseR * 0.62f, cy - baseR * 0.62f, cx + baseR * 0.62f, cy + baseR * 0.62f)
        canvas.drawArc(rectMid2, rot3, 360f, false, arcPaint)

        // 6. Crosshair target lines
        arcPaint.pathEffect = null
        arcPaint.strokeWidth = dp(1f)
        arcPaint.color = adjustAlpha(primaryColor, 0.3f)
        canvas.drawLine(cx - baseR * 0.6f, cy, cx - baseR * 0.42f, cy, arcPaint)
        canvas.drawLine(cx + baseR * 0.42f, cy, cx + baseR * 0.6f, cy, arcPaint)
        canvas.drawLine(cx, cy - baseR * 0.6f, cx, cy - baseR * 0.42f, arcPaint)
        canvas.drawLine(cx, cy + baseR * 0.42f, cx, cy + baseR * 0.6f, arcPaint)

        // 7. Central Arc Reactor Core (Breathing + Voice-Reactive Hologram)
        val audioBoost = 1f + audioLevel * 0.14f
        val coreR = baseR * 0.38f * (if (activeGlow) pulse * audioBoost else 1f)

        arcPaint.color = coreGlowColor
        arcPaint.strokeWidth = dp(3.5f)
        canvas.drawCircle(cx, cy, coreR, arcPaint)

        fillPaint.color = adjustAlpha(coreGlowColor, 0.25f)
        canvas.drawCircle(cx, cy, coreR * 0.85f, fillPaint)

        canvas.save()
        canvas.rotate(-rot2 * 0.5f, cx, cy)
        arcPaint.strokeWidth = dp(2f)
        arcPaint.color = adjustAlpha(primaryColor, 0.8f)
        for (i in 0 until 6) {
            canvas.drawLine(cx, cy - coreR * 0.4f, cx, cy - coreR * 0.82f, arcPaint)
            canvas.rotate(60f, cx, cy)
        }
        canvas.restore()

        val triPath = Path()
        val triR = coreR * 0.48f
        for (i in 0 until 3) {
            val rad = Math.toRadians((rot3 + i * 120.0))
            val px = cx + (triR * cos(rad)).toFloat()
            val py = cy + (triR * sin(rad)).toFloat()
            if (i == 0) triPath.moveTo(px, py) else triPath.lineTo(px, py)
        }
        triPath.close()

        fillPaint.color = adjustAlpha(coreGlowColor, 0.6f)
        canvas.drawPath(triPath, fillPaint)

        arcPaint.color = Color.WHITE
        arcPaint.strokeWidth = dp(1.5f)
        canvas.drawPath(triPath, arcPaint)

        // 8. Core Center Text
        textPaint.color = Color.WHITE
        textPaint.textSize = dp(13f)
        textPaint.isFakeBoldText = true
        val centerLabel = when (currentState) {
            HudState.LOVING -> "💖 জান"
            HudState.LISTENING -> "LISTENING"
            HudState.THINKING -> "COMPUTING"
            HudState.SPEAKING -> "VOICE ON"
            HudState.IDLE -> personaTag
        }
        canvas.drawText(centerLabel, cx, cy + dp(4.5f), textPaint)

        // 9. Floating Holographic Telemetry
        smallTextPaint.color = adjustAlpha(primaryColor, 0.75f)
        smallTextPaint.textSize = dp(9.5f)

        smallTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("[ TRINITY: $personaTag ]", cx - baseR * 0.95f, cy - baseR * 0.78f, smallTextPaint)
        canvas.drawText("ARC: 100% OPTIMAL", cx - baseR * 0.95f, cy - baseR * 0.65f, smallTextPaint)

        smallTextPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("STONIC+MAYA: ON", cx + baseR * 0.95f, cy - baseR * 0.78f, smallTextPaint)
        canvas.drawText("SYNC: 432 Hz", cx + baseR * 0.95f, cy - baseR * 0.65f, smallTextPaint)

        smallTextPaint.textAlign = Paint.Align.CENTER
        smallTextPaint.color = adjustAlpha(if (currentState == HudState.LOVING) accentColor else primaryColor, 0.85f)
        val hudStatus = when (currentState) {
            HudState.LOVING -> "[ AFFECTION CORE: 100% // জান আমার ]"
            HudState.LISTENING -> "[ STONIC AUDIO INPUT ACTIVE ]"
            HudState.THINKING -> "[ MAYA REACT ANALYSIS RUNNING ]"
            HudState.SPEAKING -> "[ TRANSMITTING VOCAL SYNTHESIS ]"
            HudState.IDLE -> "[ TRINITY HUD ONLINE // ট্যাপ করে বলুন ]"
        }
        canvas.drawText(hudStatus, cx, cy + baseR * 0.95f, smallTextPaint)
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).roundToInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
