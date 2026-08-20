package com.abridor.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import java.io.File

object ImageEditUtils {

    private const val MAX_DIMENSION = 1600

    /** Carrega um arquivo de imagem já reduzido para um tamanho seguro em memória. */
    fun loadDownscaled(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > MAX_DIMENSION || bounds.outHeight / sample > MAX_DIMENSION) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, opts)?.copy(Bitmap.Config.ARGB_8888, true)
    }

    fun rotate90(bitmap: Bitmap, clockwise: Boolean): Bitmap {
        val matrix = Matrix().apply { postRotate(if (clockwise) 90f else -90f) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun flip(bitmap: Bitmap, horizontal: Boolean): Bitmap {
        val matrix = Matrix().apply {
            if (horizontal) preScale(-1f, 1f) else preScale(1f, -1f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun crop(bitmap: Bitmap, x: Int, y: Int, w: Int, h: Int): Bitmap {
        val safeX = x.coerceIn(0, bitmap.width - 1)
        val safeY = y.coerceIn(0, bitmap.height - 1)
        val safeW = w.coerceIn(1, bitmap.width - safeX)
        val safeH = h.coerceIn(1, bitmap.height - safeY)
        return Bitmap.createBitmap(bitmap, safeX, safeY, safeW, safeH)
    }

    fun applyColorMatrix(bitmap: Bitmap, matrix: ColorMatrix): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    // ---- ajustes combinados: brilho, contraste, saturação, temperatura ----

    /** brightness/contraste/temperatura: -100..100 (0 = sem alteração). saturação: 0..200 (100 = normal). */
    fun buildAdjustMatrix(brightness: Float, contrast: Float, saturation: Float, warmth: Float): ColorMatrix {
        val cm = ColorMatrix()

        // saturação
        val satMatrix = ColorMatrix().apply { setSaturation((saturation / 100f).coerceIn(0f, 2f)) }
        cm.postConcat(satMatrix)

        // contraste (em torno do ponto médio 128)
        val c = (contrast.coerceIn(-100f, 100f) + 100f) / 100f // 0..2
        val translate = (1f - c) * 128f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, translate,
                0f, c, 0f, 0f, translate,
                0f, 0f, c, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(contrastMatrix)

        // brilho + temperatura (desloca R/B de forma oposta pra esquentar/esfriar)
        val b = brightness.coerceIn(-100f, 100f) * 1.6f
        val w = warmth.coerceIn(-100f, 100f) * 0.9f
        val brightnessMatrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, b + w,
                0f, 1f, 0f, 0f, b,
                0f, 0f, 1f, 0f, b - w,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(brightnessMatrix)

        return cm
    }

    // ---- presets de filtro prontos ----

    fun filterPresets(): List<Pair<String, ColorMatrix>> = listOf(
        "Original" to ColorMatrix(),
        "P&B" to ColorMatrix().apply { setSaturation(0f) },
        "Sépia" to ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        ),
        "Vívido" to combine(
            ColorMatrix().apply { setSaturation(1.5f) },
            buildAdjustMatrix(0f, 20f, 100f, 0f)
        ),
        "Quente" to buildAdjustMatrix(5f, 0f, 110f, 35f),
        "Frio" to buildAdjustMatrix(0f, 0f, 105f, -35f),
        "Negativo" to ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        ),
        "Vintage" to combine(
            ColorMatrix().apply { setSaturation(0.65f) },
            buildAdjustMatrix(-5f, -10f, 100f, 20f)
        )
    )

    private fun combine(a: ColorMatrix, b: ColorMatrix): ColorMatrix {
        val result = ColorMatrix(a)
        result.postConcat(b)
        return result
    }
}
