package com.abridor.app

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

object IconDrawer {

    /** Desenha um ícone de pasta com aba, gradiente de luz e sombra suave — visual 3D/moderno. */
    fun folderBitmap(widthPx: Int, heightPx: Int, baseColor: Int): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bodyTop = heightPx * 0.32f
        val bodyBottom = heightPx * 0.92f
        val bodyLeft = widthPx * 0.05f
        val bodyRight = widthPx * 0.95f
        val radius = heightPx * 0.09f

        // sombra suave por baixo (dá a sensação de elevação/3D)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 0, 0, 0)
            maskFilter = BlurMaskFilter(widthPx * 0.06f, BlurMaskFilter.Blur.NORMAL)
        }
        val shadowRect = RectF(bodyLeft + widthPx * 0.03f, bodyTop + heightPx * 0.06f, bodyRight + widthPx * 0.03f, bodyBottom + heightPx * 0.06f)
        canvas.drawRoundRect(shadowRect, radius, radius, shadowPaint)

        // aba (parte de trás, mais escura, atrás do corpo)
        val tabPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ThemeManager.darken(baseColor, 0.12f) }
        val tabPath = Path().apply {
            moveTo(bodyLeft, bodyTop)
            lineTo(bodyLeft + widthPx * 0.10f, heightPx * 0.14f)
            lineTo(bodyLeft + widthPx * 0.40f, heightPx * 0.14f)
            lineTo(bodyLeft + widthPx * 0.48f, bodyTop)
            close()
        }
        canvas.drawPath(tabPath, tabPaint)

        // corpo da pasta com gradiente vertical (claro em cima, escuro embaixo = volume)
        val bodyRect = RectF(bodyLeft, bodyTop, bodyRight, bodyBottom)
        val gradient = LinearGradient(
            0f, bodyTop, 0f, bodyBottom,
            ThemeManager.lighten(baseColor, 0.35f), ThemeManager.darken(baseColor, 0.18f),
            Shader.TileMode.CLAMP
        )
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gradient }
        canvas.drawRoundRect(bodyRect, radius, radius, bodyPaint)

        // realce de luz fino no topo do corpo (efeito "vidro"/verniz)
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 255, 255, 255)
        }
        val highlightRect = RectF(bodyLeft + widthPx * 0.04f, bodyTop + heightPx * 0.02f, bodyRight - widthPx * 0.04f, bodyTop + heightPx * 0.12f)
        canvas.drawRoundRect(highlightRect, radius * 0.6f, radius * 0.6f, highlightPaint)

        return bmp
    }

    /** Ícone de arquivo ZIP com o mesmo tratamento 3D, mas em formato de bloco compactado. */
    fun zipBitmap(widthPx: Int, heightPx: Int, baseColor: Int): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rect = RectF(widthPx * 0.12f, heightPx * 0.10f, widthPx * 0.88f, heightPx * 0.90f)
        val radius = heightPx * 0.08f

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 0, 0, 0)
            maskFilter = BlurMaskFilter(widthPx * 0.05f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(RectF(rect.left + 4, rect.top + 6, rect.right + 4, rect.bottom + 6), radius, radius, shadowPaint)

        val gradient = LinearGradient(
            0f, rect.top, 0f, rect.bottom,
            ThemeManager.lighten(baseColor, 0.35f), ThemeManager.darken(baseColor, 0.2f),
            Shader.TileMode.CLAMP
        )
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gradient }
        canvas.drawRoundRect(rect, radius, radius, bodyPaint)

        // "zíper" central (tracinho pontilhado)
        val zipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160, 0, 0, 0) }
        val cx = (rect.left + rect.right) / 2f
        var y = rect.top + heightPx * 0.1f
        while (y < rect.bottom - heightPx * 0.06f) {
            canvas.drawRect(cx - widthPx * 0.025f, y, cx + widthPx * 0.025f, y + heightPx * 0.05f, zipPaint)
            y += heightPx * 0.11f
        }

        return bmp
    }
}
