package com.abridor.app

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

object ThemePickerDialog {

    fun show(activity: Activity, onThemeChanged: () -> Unit) {
        val palette = ThemeManager.current(activity)
        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar)

        val root = ScrollView(activity).apply { setBackgroundColor(palette.bg) }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 20), dp(activity, 24), dp(activity, 20), dp(activity, 28))
        }
        root.addView(content)

        content.addView(TextView(activity).apply {
            text = "TEMAS"
            setTextColor(palette.paper)
            textSize = 18f
            typeface = android.graphics.Typeface.MONOSPACE
        })
        content.addView(spacer(activity, 6))
        content.addView(TextView(activity).apply {
            text = "toque para aplicar"
            setTextColor(palette.inkDim)
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
        })
        content.addView(spacer(activity, 20))

        val currentIndex = ThemeManager.currentIndex(activity)

        ThemeManager.palettes.forEachIndexed { index, p ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14))
                background = cardBackground(activity, p, selected = index == currentIndex)
                isClickable = true
                isFocusable = true
            }

            // amostra de cor: quadrado de fundo + faixa de destaque na base
            val swatchWrap = FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(activity, 56), dp(activity, 56))
            }
            val swatchBg = android.view.View(activity).apply {
                setBackgroundColor(p.bg)
            }
            val swatchAccent = android.view.View(activity).apply {
                setBackgroundColor(p.accent)
            }
            swatchWrap.addView(swatchBg, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            val accentLp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 16))
            accentLp.gravity = Gravity.BOTTOM
            swatchWrap.addView(swatchAccent, accentLp)

            val labelCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                lp.marginStart = dp(activity, 16)
                layoutParams = lp
            }
            labelCol.addView(TextView(activity).apply {
                text = p.name
                setTextColor(p.paper)
                textSize = 15f
            })
            labelCol.addView(TextView(activity).apply {
                text = if (index == currentIndex) "em uso agora" else "toque para usar"
                setTextColor(if (index == currentIndex) p.accent else p.inkDim)
                textSize = 11f
                typeface = android.graphics.Typeface.MONOSPACE
            })

            row.addView(swatchWrap)
            row.addView(labelCol)

            row.setOnClickListener {
                ThemeManager.setCurrent(activity, index)
                dialog.dismiss()
                onThemeChanged()
            }

            content.addView(row)
            content.addView(spacer(activity, 10))
        }

        // ---- tema "Personalizado" (RGB escolhido pelo usuário) ----
        val customPalette = ThemeManager.customPalette(activity)
        val isCustomSelected = currentIndex == ThemeManager.customIndex
        val customRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14))
            background = cardBackground(activity, customPalette, selected = isCustomSelected)
            isClickable = true
            isFocusable = true
        }
        val customSwatchWrap = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(activity, 56), dp(activity, 56))
        }
        customSwatchWrap.addView(
            android.view.View(activity).apply { setBackgroundColor(customPalette.bg) },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        val customAccentLp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 16))
        customAccentLp.gravity = Gravity.BOTTOM
        customSwatchWrap.addView(android.view.View(activity).apply { setBackgroundColor(customPalette.accent) }, customAccentLp)

        val customLabelCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = dp(activity, 16)
            layoutParams = lp
        }
        customLabelCol.addView(TextView(activity).apply {
            text = "Personalizado"
            setTextColor(customPalette.paper)
            textSize = 15f
        })
        customLabelCol.addView(TextView(activity).apply {
            text = if (isCustomSelected) "em uso agora · toque para ajustar" else "toque para criar o seu"
            setTextColor(if (isCustomSelected) customPalette.accent else customPalette.inkDim)
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
        })

        customRow.addView(customSwatchWrap)
        customRow.addView(customLabelCol)
        customRow.setOnClickListener {
            dialog.dismiss()
            showCustomColorEditor(activity, palette) { onThemeChanged() }
        }
        content.addView(customRow)
        content.addView(spacer(activity, 10))

        val closeBtn = TextView(activity).apply {
            text = "FECHAR"
            setTextColor(palette.onAccent)
            background = ThemeManager.glossyDrawable(palette.accent, dp(activity, 6).toFloat())
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(activity, 14), 0, dp(activity, 14))
            setOnClickListener { dialog.dismiss() }
        }
        content.addView(spacer(activity, 10))
        content.addView(closeBtn)

        dialog.setContentView(root)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setGravity(Gravity.BOTTOM)
        dialog.show()
    }

    /** Editor com sliders RGB pra montar um tema do zero: escolhe a cor de fundo
     *  e a cor de destaque, e o resto da paleta (painel, cartões, textos) é
     *  derivado automaticamente — tem preview ao vivo enquanto arrasta. */
    private fun showCustomColorEditor(activity: Activity, chrome: Palette, onApplied: () -> Unit) {
        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar)

        var bg = ThemeManager.customBg(activity)
        var accent = ThemeManager.customAccent(activity)

        val root = ScrollView(activity).apply { setBackgroundColor(chrome.bg) }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 20), dp(activity, 24), dp(activity, 20), dp(activity, 28))
        }
        root.addView(content)

        content.addView(TextView(activity).apply {
            text = "PERSONALIZAR CORES"
            setTextColor(chrome.paper)
            textSize = 18f
            typeface = android.graphics.Typeface.MONOSPACE
        })
        content.addView(spacer(activity, 4))
        content.addView(TextView(activity).apply {
            text = "arraste pra ajustar o RGB de cada cor"
            setTextColor(chrome.inkDim)
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
        })
        content.addView(spacer(activity, 18))

        // ---- preview ao vivo ----
        val previewWrap = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 90))
        }
        val previewBg = android.view.View(activity)
        val previewAccent = android.view.View(activity)
        previewWrap.addView(previewBg, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val previewAccentLp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 22))
        previewAccentLp.gravity = Gravity.BOTTOM
        previewWrap.addView(previewAccent, previewAccentLp)
        lateinit var applyBtn: TextView
        fun updatePreview() {
            previewBg.setBackgroundColor(bg)
            previewAccent.setBackgroundColor(accent)
            if (::applyBtn.isInitialized) {
                applyBtn.background = ThemeManager.glossyDrawable(accent, dp(activity, 6).toFloat())
                applyBtn.setTextColor(ThemeManager.buildPalette("tmp", bg, accent).onAccent)
            }
        }
        updatePreview()
        content.addView(previewWrap)
        content.addView(spacer(activity, 22))

        // ---- fundo ----
        val bgLabel = TextView(activity).apply {
            setTextColor(chrome.inkDim)
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
        }
        fun updateBgLabel() { bgLabel.text = "cor de fundo  ·  ${hex(bg)}" }
        updateBgLabel()
        content.addView(bgLabel)
        content.addView(spacer(activity, 6))
        addRgbSliders(activity, content, chrome, bg) { newColor ->
            bg = newColor
            updateBgLabel()
            updatePreview()
        }
        content.addView(spacer(activity, 22))

        // ---- destaque ----
        val accentLabel = TextView(activity).apply {
            setTextColor(chrome.inkDim)
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
        }
        fun updateAccentLabel() { accentLabel.text = "cor de destaque  ·  ${hex(accent)}" }
        updateAccentLabel()
        content.addView(accentLabel)
        content.addView(spacer(activity, 6))
        addRgbSliders(activity, content, chrome, accent) { newColor ->
            accent = newColor
            updateAccentLabel()
            updatePreview()
        }
        content.addView(spacer(activity, 24))

        // ---- botões ----
        val btnRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val cancelBtn = TextView(activity).apply {
            text = "CANCELAR"
            setTextColor(chrome.inkDim)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(activity, 14), 0, dp(activity, 14))
            setOnClickListener { dialog.dismiss() }
        }
        applyBtn = TextView(activity).apply {
            text = "APLICAR"
            setTextColor(ThemeManager.buildPalette("tmp", bg, accent).onAccent)
            background = ThemeManager.glossyDrawable(accent, dp(activity, 6).toFloat())
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(activity, 14), 0, dp(activity, 14))
            setOnClickListener {
                ThemeManager.setCustomColors(activity, bg, accent)
                ThemeManager.setCurrent(activity, ThemeManager.customIndex)
                dialog.dismiss()
                onApplied()
            }
        }
        btnRow.addView(cancelBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val applyLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        applyLp.marginStart = dp(activity, 8)
        btnRow.addView(applyBtn, applyLp)
        content.addView(btnRow)

        dialog.setContentView(root)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setGravity(Gravity.BOTTOM)
        dialog.show()
    }

    private fun addRgbSliders(activity: Activity, container: LinearLayout, chrome: Palette, initialColor: Int, onChange: (Int) -> Unit) {
        var r = Color.red(initialColor)
        var g = Color.green(initialColor)
        var b = Color.blue(initialColor)

        fun row(label: String, initial: Int, trackTint: Int, onSeek: (Int) -> Unit): LinearLayout {
            val rowLayout = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val tag = TextView(activity).apply {
                text = label
                setTextColor(chrome.inkDim)
                textSize = 11f
                typeface = android.graphics.Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(dp(activity, 18), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val seek = SeekBar(activity).apply {
                max = 255
                progress = initial
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            seek.progressDrawable?.setTint(trackTint)
            seek.thumb?.setTint(trackTint)
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) onSeek(progress)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
            rowLayout.addView(tag)
            rowLayout.addView(seek)
            return rowLayout
        }

        container.addView(row("R", r, Color.rgb(220, 70, 70)) { r = it; onChange(Color.rgb(r, g, b)) })
        container.addView(row("G", g, Color.rgb(70, 190, 90)) { g = it; onChange(Color.rgb(r, g, b)) })
        container.addView(row("B", b, Color.rgb(70, 120, 220)) { b = it; onChange(Color.rgb(r, g, b)) })
    }

    private fun hex(color: Int): String = String.format("#%06X", 0xFFFFFF and color)

    private fun cardBackground(ctx: android.content.Context, p: Palette, selected: Boolean): android.graphics.drawable.Drawable {
        val shape = android.graphics.drawable.GradientDrawable()
        shape.cornerRadius = dp(ctx, 10).toFloat()
        shape.setColor(p.card)
        shape.setStroke(dp(ctx, if (selected) 2 else 1), if (selected) p.accent else p.line)
        return shape
    }

    private fun spacer(ctx: android.content.Context, heightDp: Int) = android.view.View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, heightDp))
    }

    private fun dp(ctx: android.content.Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density).toInt()
}
