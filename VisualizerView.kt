package com.abridor.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import java.util.ArrayDeque
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Visualizador de áudio em tempo real, inspirado nos efeitos clássicos do
 * Windows Media Player. Recebe dados de FFT do android.media.audiofx.Visualizer
 * e desenha formas que "batem no compasso" da música. Toque na tela alterna
 * entre 5 efeitos: Barras, Círculo pulsante, Corredor 3D, Alquimia e Ondas.
 */
class VisualizerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var magnitudes = FloatArray(32)
    private var smoothed = FloatArray(32)
    private var peaks = FloatArray(32)

    private val modeCount = 5
    private var mode = 0
    private var pulseLevel = 0f
    private var bassLevel = 0f
    private var rotationAngle = 0f
    private var hueBase = 0f
    private var frameCounter = 0

    /** Cor de destaque do tema atual — usada nos modos Barras, Círculo e 3D. */
    var accentColor: Int = Color.parseColor("#E0A640")
        set(value) {
            field = value
            val hsv = FloatArray(3)
            Color.colorToHSV(value, hsv)
            accentHue = hsv[0]
            accentSat = hsv[1]
        }
    private var accentHue = 38f
    private var accentSat = 0.71f

    /** Cores secundárias do tema (marcador de pico, ponto central, linha do corredor 3D). */
    var paperColor: Int = Color.parseColor("#E9E4D4")
        set(value) { field = value; peakPaint.color = value }
    var cardColor: Int = Color.parseColor("#22231B")
        set(value) { field = value; bgDotPaint.color = value }
    var lineColor: Int = Color.parseColor("#2E2F24")
        set(value) { field = value; gridPaint.color = value }

    private val historySize = 14
    private val history = ArrayDeque<FloatArray>()

    private val camera = Camera()
    private val matrix = Matrix()

    private var orbs: MutableList<Orb> = mutableListOf()
    private var orbsInited = false

    private data class Orb(
        var angle: Float,
        var orbitR: Float,
        val speed: Float,
        val band: Int,
        val baseRadius: Float,
        val hueOffset: Float
    )

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E9E4D4")
    }
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val bgDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22231B")
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#2E2F24")
    }
    private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    init {
        // necessário pra shaders radiais + blend de "brilho" (modo Alquimia) renderizarem
        // corretamente em qualquer aparelho
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            mode = (mode + 1) % modeCount
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }

    /** Recebe os bytes de FFT (formato do Visualizer.OnDataCaptureListener) */
    fun updateFft(fft: ByteArray) {
        val bands = magnitudes.size
        val usableBins = fft.size / 2 // pares (real, imaginário)
        val binsPerBand = max1(usableBins / bands)

        for (b in 0 until bands) {
            var sum = 0f
            val start = b * binsPerBand
            val end = min(start + binsPerBand, usableBins)
            var count = 0
            for (i in start until end) {
                val idx = i * 2
                if (idx + 1 >= fft.size) continue
                val re = fft[idx].toInt()
                val im = fft[idx + 1].toInt()
                sum += sqrt((re * re + im * im).toFloat())
                count++
            }
            magnitudes[b] = if (count > 0) sum / count else 0f
        }
        // suavização + decaimento dos picos, pra ficar com aquele efeito "elástico"
        for (i in magnitudes.indices) {
            val target = (magnitudes[i] / 40f).coerceIn(0f, 1f)
            smoothed[i] = smoothed[i] * 0.55f + target * 0.45f
            if (smoothed[i] > peaks[i]) peaks[i] = smoothed[i] else peaks[i] = (peaks[i] - 0.03f).coerceAtLeast(smoothed[i])
        }
        pulseLevel = smoothed.average().toFloat()
        bassLevel = (smoothed[0] + smoothed[1] + smoothed[2] + smoothed[3]) / 4f

        // guarda histórico pra alimentar o corredor 3D (uma "fatia" nova a cada poucos quadros)
        frameCounter++
        if (frameCounter % 2 == 0) {
            history.addFirst(smoothed.copyOf())
            while (history.size > historySize) history.removeLast()
        }
        rotationAngle += 0.15f + pulseLevel * 0.6f
        hueBase = (hueBase + 0.6f + bassLevel * 2.2f) % 360f

        postInvalidate()
    }

    private fun max1(v: Int) = if (v < 1) 1 else v

    private fun ensureOrbs() {
        if (orbsInited || width == 0) return
        val rnd = Random(42)
        val count = 22
        orbs = MutableList(count) { i ->
            Orb(
                angle = rnd.nextFloat() * 360f,
                orbitR = min(width, height) * (0.12f + rnd.nextFloat() * 0.32f),
                speed = 0.4f + rnd.nextFloat() * 1.3f,
                band = i % smoothed.size,
                baseRadius = min(width, height) * (0.035f + rnd.nextFloat() * 0.05f),
                hueOffset = rnd.nextFloat() * 360f
            )
        }
        orbsInited = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (mode) {
            0 -> drawBars(canvas)
            1 -> drawCircle(canvas)
            2 -> draw3DCorridor(canvas)
            3 -> drawAlchemy(canvas)
            else -> drawWaves(canvas)
        }
    }

    private fun drawBars(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bands = smoothed.size
        val gap = 6f
        val barWidth = (w - gap * (bands - 1)) / bands

        for (i in 0 until bands) {
            val x = i * (barWidth + gap)
            val barH = smoothed[i] * h * 0.92f
            barPaint.color = shadedAccent(smoothed[i])
            val rect = RectF(x, h - barH, x + barWidth, h)
            canvas.drawRoundRect(rect, barWidth / 3, barWidth / 3, barPaint)

            // marcador de pico (efeito "segura o compasso" do WMP clássico)
            val peakY = h - peaks[i] * h * 0.92f
            canvas.drawRect(x, peakY - 4, x + barWidth, peakY, peakPaint)
        }
    }

    /** Deriva uma cor a partir da cor de destaque do tema, mais clara/saturada
     *  quanto mais forte o nível de áudio naquele instante. */
    private fun shadedAccent(level: Float): Int {
        val hsv = floatArrayOf(accentHue, (accentSat * 0.75f).coerceIn(0f, 1f), (0.55f + level * 0.45f).coerceIn(0f, 1f))
        return Color.HSVToColor(hsv)
    }

    private fun drawCircle(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val baseR = min(width, height) / 5f
        val pulseR = baseR + pulseLevel * baseR * 1.4f

        bgDotPaint.alpha = 255
        canvas.drawCircle(cx, cy, baseR * 0.6f, bgDotPaint)

        val bands = smoothed.size
        for (i in 0 until bands) {
            val angle = (2 * Math.PI * i / bands).toFloat()
            val r = pulseR + smoothed[i] * baseR * 1.2f
            val x = cx + r * cos(angle)
            val y = cy + r * sin(angle)
            val dotR = 5f + smoothed[i] * 10f
            circlePaint.color = accentColor
            circlePaint.alpha = (120 + smoothed[i] * 135).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y, dotR, circlePaint)
        }
        circlePaint.color = accentColor
        circlePaint.alpha = 255
        canvas.drawCircle(cx, cy, pulseR, circlePaint)
    }

    /**
     * Corredor de espectro em 3D: a linha da frente é o som de agora, e as
     * linhas anteriores vão encolhendo e sumindo ao fundo, como se a música
     * estivesse "correndo" pra dentro da tela — usa android.graphics.Camera
     * pra aplicar rotação e perspectiva real de verdade sobre o Canvas 2D.
     */
    private fun draw3DCorridor(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val centerX = w / 2f
        val horizonY = h * 0.32f

        // leve balanço giratório no eixo Y, mais rápido quando o som "pulsa" mais forte
        camera.save()
        camera.rotateX(14f)
        camera.rotateY(sin(Math.toRadians(rotationAngle.toDouble())).toFloat() * 6f)
        camera.getMatrix(matrix)
        camera.restore()
        matrix.preTranslate(-centerX, -horizonY)
        matrix.postTranslate(centerX, horizonY)

        canvas.save()
        canvas.concat(matrix)

        val rows = history.toList()
        val bands = smoothed.size
        val maxBarH = h * 0.5f

        // desenha do fundo pra frente (mais distante primeiro), efeito de profundidade
        for (r in rows.indices.reversed()) {
            val depth = r.toFloat() / historySize // 0 = perto, 1 = longe
            val scale = 1f - depth * 0.72f
            val rowW = w * 0.92f * scale
            val rowLeft = centerX - rowW / 2f
            val rowY = horizonY + depth * h * 0.9f * (1f - depth * 0.2f)
            val alpha = (255 * (1f - depth * 0.85f)).toInt().coerceIn(30, 255)

            val gap = 4f * scale
            val barW = (rowW - gap * (bands - 1)) / bands
            val row = rows[r]

            for (i in 0 until bands) {
                val x = rowLeft + i * (barW + gap)
                val barH = row[i] * maxBarH * scale
                barPaint.color = shadedAccent(row[i])
                barPaint.alpha = alpha
                canvas.drawRect(x, rowY - barH, x + barW, rowY, barPaint)
            }

            // linha de "trilho" do corredor, reforça a sensação de profundidade
            gridPaint.alpha = alpha / 3
            canvas.drawLine(rowLeft, rowY, rowLeft + rowW, rowY, gridPaint)
        }

        canvas.restore()
    }

    /**
     * "Alquimia": bolhas orgânicas coloridas que orbitam o centro, mudando de
     * tamanho e cor de acordo com a frequência que cada uma representa —
     * quando o grave bate forte, elas se afastam do centro numa explosão suave.
     * As cores vão passeando pelo arco-íris ao longo do tempo (hueBase).
     */
    private fun drawAlchemy(canvas: Canvas) {
        ensureOrbs()
        if (orbs.isEmpty()) return
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawColor(Color.argb(28, 0, 0, 0)) // rastro suave (efeito de trilha luminosa)

        for (orb in orbs) {
            val level = smoothed[orb.band]
            orb.angle += orb.speed * (0.4f + level * 1.6f)
            val radius = orb.orbitR * (1f + bassLevel * 0.9f)
            val x = cx + radius * cos(Math.toRadians(orb.angle.toDouble())).toFloat()
            val y = cy + radius * sin(Math.toRadians(orb.angle.toDouble())).toFloat()
            val r = orb.baseRadius * (1f + level * 2.2f)

            val hue = (hueBase + orb.hueOffset) % 360f
            val color = Color.HSVToColor(floatArrayOf(hue, 0.65f, 1f))
            val transparent = Color.HSVToColor(0, floatArrayOf(hue, 0.65f, 1f))

            orbPaint.shader = RadialGradient(x, y, r, color, transparent, Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, r, orbPaint)
        }
        orbPaint.shader = null
    }

    /**
     * "Ondas": faixas senoidais fluidas (efeito "Ambience"/"Bars and Waves"),
     * cada uma reagindo a uma faixa de frequência diferente, com cor que
     * também percorre o espectro de cores ao longo do tempo.
     */
    private fun drawWaves(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cy = h / 2f
        val waveCount = 4

        for (wv in 0 until waveCount) {
            val bandStart = wv * (smoothed.size / waveCount)
            val level = smoothed.slice(bandStart until bandStart + smoothed.size / waveCount).average().toFloat()
            val amplitude = h * 0.09f * (0.5f + level * 2.2f)
            val hue = (hueBase + wv * 70f) % 360f
            wavePaint.color = Color.HSVToColor((140 + level * 110).toInt().coerceIn(0, 255), floatArrayOf(hue, 0.6f, 1f))
            wavePaint.strokeWidth = 5f + level * 6f

            val path = Path()
            val step = 12
            var x = 0f
            var first = true
            while (x <= w) {
                val phase = rotationAngle * 0.03f + wv * 1.3f
                val y = cy + (wv - waveCount / 2f) * h * 0.09f +
                        amplitude * sin(x * 0.012f + phase.toDouble()).toFloat()
                if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                x += step
            }
            canvas.drawPath(path, wavePaint)
        }
    }
}


