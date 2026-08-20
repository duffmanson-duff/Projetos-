package com.abridor.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

class ImageEditorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Mode { NONE, DRAW, CROP }

    var mode: Mode = Mode.NONE
        set(value) {
            field = value
            if (value == Mode.CROP) resetCropRect()
            invalidate()
        }

    var brushColor: Int = Color.RED
    var brushWidth: Float = 14f
    var themeAccent: Int = Color.parseColor("#E0A640")
        set(value) {
            field = value
            cropLinePaint.color = value
            cropHandlePaint.color = value
        }

    private var bitmap: Bitmap? = null
    private var drawCanvas: Canvas? = null

    private val displayMatrix = Matrix()
    private val inverseMatrix = Matrix()

    private val undoStack = ArrayDeque<Bitmap>()
    private val maxUndo = 12

    // ---- desenho ----
    private var lastX = 0f
    private var lastY = 0f
    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    // ---- corte ----
    private var cropRect: RectF? = null
    private var draggingHandle = -1 // 0=TL,1=TR,2=BL,3=BR,4=corpo,-1=nenhum
    private var dragStartX = 0f
    private var dragStartY = 0f
    private val handleRadius = 40f
    private val cropOverlayPaint = Paint().apply { color = Color.argb(150, 0, 0, 0) }
    private val cropLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#E0A640")
    }
    private val cropHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E0A640")
    }

    fun setBitmap(bmp: Bitmap, pushToUndo: Boolean) {
        if (pushToUndo) pushUndo()
        bitmap = bmp
        drawCanvas = Canvas(bmp)
        recomputeMatrix()
        invalidate()
    }

    fun currentBitmap(): Bitmap? = bitmap

    private fun pushUndo() {
        val bmp = bitmap ?: return
        undoStack.addLast(bmp.copy(Bitmap.Config.ARGB_8888, true))
        while (undoStack.size > maxUndo) undoStack.removeFirst()
    }

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val prev = undoStack.removeLast()
        bitmap = prev
        drawCanvas = Canvas(prev)
        recomputeMatrix()
        invalidate()
        return true
    }

    fun canUndo() = undoStack.isNotEmpty()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recomputeMatrix()
    }

    private fun recomputeMatrix() {
        val bmp = bitmap ?: return
        if (width == 0 || height == 0) return
        val scale = min(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        val dx = (width - bmp.width * scale) / 2f
        val dy = (height - bmp.height * scale) / 2f
        displayMatrix.reset()
        displayMatrix.postScale(scale, scale)
        displayMatrix.postTranslate(dx, dy)
        displayMatrix.invert(inverseMatrix)
    }

    private fun resetCropRect() {
        val bmp = bitmap ?: return
        val pts = floatArrayOf(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        displayMatrix.mapPoints(pts)
        val margin = (pts[2] - pts[0]) * 0.1f
        cropRect = RectF(pts[0] + margin, pts[1] + margin, pts[2] - margin, pts[3] - margin)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        canvas.drawBitmap(bmp, displayMatrix, null)

        if (mode == Mode.CROP) {
            val r = cropRect ?: return
            // escurece fora da área de corte (clipRect com Region.Op.DIFFERENCE funciona desde API 1,
            // diferente de clipOutRect que só existe a partir do Android 8)
            canvas.save()
            @Suppress("DEPRECATION")
            canvas.clipRect(r, Region.Op.DIFFERENCE)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), cropOverlayPaint)
            canvas.restore()
            canvas.drawRect(r, cropLinePaint)
            canvas.drawCircle(r.left, r.top, 14f, cropHandlePaint)
            canvas.drawCircle(r.right, r.top, 14f, cropHandlePaint)
            canvas.drawCircle(r.left, r.bottom, 14f, cropHandlePaint)
            canvas.drawCircle(r.right, r.bottom, 14f, cropHandlePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (mode) {
            Mode.DRAW -> handleDrawTouch(event)
            Mode.CROP -> handleCropTouch(event)
            Mode.NONE -> {}
        }
        return true
    }

    private fun handleDrawTouch(event: MotionEvent) {
        val pt = floatArrayOf(event.x, event.y)
        inverseMatrix.mapPoints(pt)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pushUndo()
                lastX = pt[0]
                lastY = pt[1]
            }
            MotionEvent.ACTION_MOVE -> {
                drawPaint.color = brushColor
                drawPaint.strokeWidth = brushWidth
                drawCanvas?.drawLine(lastX, lastY, pt[0], pt[1], drawPaint)
                lastX = pt[0]
                lastY = pt[1]
                invalidate()
            }
        }
    }

    private fun handleCropTouch(event: MotionEvent) {
        val r = cropRect ?: return
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                draggingHandle = hitTestHandle(r, event.x, event.y)
                dragStartX = event.x
                dragStartY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - dragStartX
                val dy = event.y - dragStartY
                dragStartX = event.x
                dragStartY = event.y
                when (draggingHandle) {
                    0 -> { r.left += dx; r.top += dy }
                    1 -> { r.right += dx; r.top += dy }
                    2 -> { r.left += dx; r.bottom += dy }
                    3 -> { r.right += dx; r.bottom += dy }
                    4 -> { r.offset(dx, dy) }
                }
                invalidate()
            }
        }
    }

    private fun hitTestHandle(r: RectF, x: Float, y: Float): Int {
        fun near(hx: Float, hy: Float) = (x - hx) * (x - hx) + (y - hy) * (y - hy) < handleRadius * handleRadius
        return when {
            near(r.left, r.top) -> 0
            near(r.right, r.top) -> 1
            near(r.left, r.bottom) -> 2
            near(r.right, r.bottom) -> 3
            r.contains(x, y) -> 4
            else -> -1
        }
    }

    /** Converte o retângulo de corte (coordenadas de tela) para coordenadas do bitmap e aplica. */
    fun applyCrop(): Boolean {
        val bmp = bitmap ?: return false
        val r = cropRect ?: return false
        val pts = floatArrayOf(r.left, r.top, r.right, r.bottom)
        inverseMatrix.mapPoints(pts)
        val x = min(pts[0], pts[2]).toInt()
        val y = min(pts[1], pts[3]).toInt()
        val w = kotlin.math.abs(pts[2] - pts[0]).toInt()
        val h = kotlin.math.abs(pts[3] - pts[1]).toInt()
        if (w < 10 || h < 10) return false
        pushUndo()
        val cropped = ImageEditUtils.crop(bmp, x, y, w, h)
        bitmap = cropped
        drawCanvas = Canvas(cropped)
        recomputeMatrix()
        mode = Mode.NONE
        invalidate()
        return true
    }

    fun cancelCrop() {
        mode = Mode.NONE
        invalidate()
    }

    fun addText(text: String, color: Int, sizeSp: Float) {
        val bmp = bitmap ?: return
        pushUndo()
        val canvas = drawCanvas ?: return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = sizeSp * resources.displayMetrics.scaledDensity
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(6f, 0f, 0f, Color.argb(160, 0, 0, 0))
        }
        val x = bmp.width * 0.08f
        val y = bmp.height * 0.5f
        canvas.drawText(text, x, y, paint)
        invalidate()
    }
}
