package com.abridor.app

import android.content.Context
import android.graphics.Color

/** Uma paleta de cores completa do app. */
data class Palette(
    val name: String,
    val bg: Int,
    val panel: Int,
    val card: Int,
    val line: Int,
    val accent: Int,
    val accentDim: Int,
    val paper: Int,
    val ink: Int,
    val inkDim: Int,
    val onAccent: Int
)

object ThemeManager {

    private const val PREFS = "abridor_prefs"
    private const val KEY_THEME = "theme_index"
    private const val KEY_CUSTOM_BG = "custom_bg"
    private const val KEY_CUSTOM_ACCENT = "custom_accent"

    private fun c(hex: String) = Color.parseColor(hex)

    val palettes: List<Palette> = listOf(
        Palette(
            name = "Âmbar Clássico",
            bg = c("#12130F"), panel = c("#1B1C16"), card = c("#1E1F18"), line = c("#34362A"),
            accent = c("#E0A640"), accentDim = c("#8A6B2C"),
            paper = c("#E9E4D4"), ink = c("#C9C6B6"), inkDim = c("#7F7D6C"), onAccent = c("#1A1A12")
        ),
        Palette(
            name = "Verde Menta",
            bg = c("#0F1512"), panel = c("#17201B"), card = c("#1A241E"), line = c("#2A3B32"),
            accent = c("#4FD1A5"), accentDim = c("#2E7A5B"),
            paper = c("#E3F2EA"), ink = c("#B8CFC4"), inkDim = c("#6F8A7D"), onAccent = c("#0B1A14")
        ),
        Palette(
            name = "Azul Ártico",
            bg = c("#0D1319"), panel = c("#141C24"), card = c("#17212B"), line = c("#26333F"),
            accent = c("#57A6E0"), accentDim = c("#2F6690"),
            paper = c("#E4EEF7"), ink = c("#AFC4D6"), inkDim = c("#6C8199"), onAccent = c("#0A1A24")
        ),
        Palette(
            name = "Roxo Nebulosa",
            bg = c("#140F19"), panel = c("#1D1524"), card = c("#221828"), line = c("#372A42"),
            accent = c("#B482E0"), accentDim = c("#6E4C90"),
            paper = c("#EBE3F2"), ink = c("#C7B7D6"), inkDim = c("#857399"), onAccent = c("#1C1024")
        ),
        Palette(
            name = "Vermelho Ferrugem",
            bg = c("#170F0D"), panel = c("#211714"), card = c("#251A17"), line = c("#3C2A24"),
            accent = c("#E0714F"), accentDim = c("#904B2F"),
            paper = c("#F2E4DE"), ink = c("#D6B7AC"), inkDim = c("#997266"), onAccent = c("#241009")
        ),
        Palette(
            name = "Grafite Mono",
            bg = c("#121212"), panel = c("#1A1A1A"), card = c("#1E1E1E"), line = c("#333333"),
            accent = c("#C9C9C9"), accentDim = c("#7A7A7A"),
            paper = c("#EDEDED"), ink = c("#C4C4C4"), inkDim = c("#808080"), onAccent = c("#121212")
        )
    )

    /** Índice especial (logo depois do último preset) que representa o tema
     *  "Personalizado", montado a partir das cores RGB escolhidas pelo usuário. */
    val customIndex: Int get() = palettes.size

    fun currentIndex(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val idx = prefs.getInt(KEY_THEME, 0)
        return if (idx in 0..customIndex) idx else 0
    }

    fun current(context: Context): Palette {
        val idx = currentIndex(context)
        return if (idx == customIndex) customPalette(context) else palettes[idx]
    }

    fun setCurrent(context: Context, index: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_THEME, index).apply()
    }

    // ---- tema personalizado (RGB escolhido pelo usuário) ----

    fun customBg(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_CUSTOM_BG, c("#12130F"))

    fun customAccent(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_CUSTOM_ACCENT, c("#E0A640"))

    fun setCustomColors(context: Context, bg: Int, accent: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_CUSTOM_BG, bg)
            .putInt(KEY_CUSTOM_ACCENT, accent)
            .apply()
    }

    /** Monta uma paleta completa a partir de só 2 cores (fundo + destaque) —
     *  o resto (painel, cartão, textos) é derivado automaticamente clareando/
     *  escurecendo essas duas, do mesmo jeito que os temas prontos foram feitos. */
    fun customPalette(context: Context): Palette = buildPalette(
        "Personalizado", customBg(context), customAccent(context)
    )

    fun buildPalette(name: String, bg: Int, accent: Int): Palette {
        val panel = lighten(bg, 0.07f)
        val card = lighten(bg, 0.11f)
        val line = lighten(bg, 0.22f)
        val accentDim = darken(accent, 0.38f)
        val paper = lighten(bg, 0.93f)
        val ink = lighten(bg, 0.80f)
        val inkDim = lighten(bg, 0.58f)
        val onAccent = if (luminance(accent) > 0.5) c("#1A1A12") else c("#F5F2E8")
        return Palette(name, bg, panel, card, line, accent, accentDim, paper, ink, inkDim, onAccent)
    }

    private fun luminance(color: Int): Double {
        val r = Color.red(color) / 255.0
        val g = Color.green(color) / 255.0
        val b = Color.blue(color) / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    // ---- helpers de cor pra dar acabamento 3D/glossy (clareia ou escurece uma cor base) ----

    fun lighten(color: Int, factor: Float): Int {
        val r = Color.red(color) + ((255 - Color.red(color)) * factor).toInt()
        val g = Color.green(color) + ((255 - Color.green(color)) * factor).toInt()
        val b = Color.blue(color) + ((255 - Color.blue(color)) * factor).toInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    fun darken(color: Int, factor: Float): Int {
        val r = Color.red(color) * (1 - factor)
        val g = Color.green(color) * (1 - factor)
        val b = Color.blue(color) * (1 - factor)
        return Color.rgb(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
    }

    /** Fundo "glossy" com gradiente vertical (claro em cima, escuro embaixo) — dá volume 3D a botões e badges. */
    fun glossyDrawable(base: Int, cornerRadiusPx: Float, strokeColor: Int? = null, strokeWidthPx: Int = 0): android.graphics.drawable.GradientDrawable {
        val light = lighten(base, 0.30f)
        val dark = darken(base, 0.22f)
        return android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(light, base, dark)
        ).apply {
            cornerRadius = cornerRadiusPx
            if (strokeColor != null) setStroke(strokeWidthPx, strokeColor)
        }
    }

    fun glossyOval(base: Int): android.graphics.drawable.GradientDrawable {
        val light = lighten(base, 0.30f)
        val dark = darken(base, 0.22f)
        return android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(light, base, dark)
        ).apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
        }
    }
}
