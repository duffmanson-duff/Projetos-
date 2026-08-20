package com.abridor.app

import android.app.Dialog
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

/**
 * Constrói e exibe o diálogo do equalizador completo: bandas de frequência
 * (sliders verticais), presets prontos, reforço de graves e efeito surround.
 * Usa as cores do tema atual selecionado pelo usuário.
 */
object EqualizerDialog {

    fun show(
        activity: android.app.Activity,
        eq: EqualizerController,
        p: Palette,
        initialPan: Float = 0f,
        onBalanceChange: ((Float, Float) -> Unit)? = null
    ) {
        var currentPan = initialPan
        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar)
        val root = ScrollView(activity).apply {
            setBackgroundColor(p.bg)
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 20), dp(activity, 24), dp(activity, 20), dp(activity, 28))
        }
        root.addView(content)

        content.addView(titleText(activity, "EQUALIZADOR", p))
        content.addView(spacer(activity, 18))

        // ---- presets ----
        content.addView(labelText(activity, "presets", p))
        val presetRow = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
        }
        val presetContainer = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        presetRow.addView(presetContainer)
        eq.presetNames().forEachIndexed { index, name ->
            val chip = TextView(activity).apply {
                text = name
                setTextColor(p.accent)
                textSize = 12.5f
                setPadding(dp(activity, 14), dp(activity, 8), dp(activity, 14), dp(activity, 8))
                background = chipBackground(activity, p)
                setOnClickListener {
                    eq.usePreset(index)
                    dialog.dismiss()
                    show(activity, eq, p, currentPan, onBalanceChange) // reabre já refletindo os novos valores das bandas
                }
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = dp(activity, 8)
            presetContainer.addView(chip, lp)
        }
        content.addView(presetRow)
        content.addView(spacer(activity, 26))

        // ---- bandas ----
        val bandsLabel = if (eq.usingCustomEq) "bandas de frequência (${eq.bandCount} bandas)" else "bandas de frequência"
        content.addView(labelText(activity, bandsLabel, p))
        content.addView(spacer(activity, 8))
        val bandsScroll = HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false }
        val bandsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bandsScroll.addView(bandsRow)
        val sliderHeightDp = 190
        val colWidthDp = 64
        for (b in 0 until eq.bandCount) {
            val band = b
            val col = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(dp(activity, colWidthDp), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val dbLabel = TextView(activity).apply {
                text = "${eq.getBandLevel(band) / 100} dB"
                setTextColor(p.inkDim)
                textSize = 10.5f
                gravity = Gravity.CENTER
            }
            val seek = SeekBar(activity).apply {
                max = (eq.maxLevel - eq.minLevel).toInt()
                progress = (eq.getBandLevel(band) - eq.minLevel).toInt()
            }
            seek.progressDrawable?.setTint(p.accent)
            seek.thumb?.setTint(p.accent)
            seek.rotation = 270f
            val seekLp = LinearLayout.LayoutParams(dp(activity, sliderHeightDp), dp(activity, 34))
            seek.layoutParams = seekLp

            val seekWrap = FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(activity, colWidthDp), dp(activity, sliderHeightDp))
            }
            // centraliza o slider rotacionado dentro do container
            val innerLp = FrameLayout.LayoutParams(dp(activity, sliderHeightDp), dp(activity, 34))
            innerLp.gravity = Gravity.CENTER
            seekWrap.addView(seek, innerLp)

            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val level = (progress + eq.minLevel).toShort()
                        eq.setBandLevel(band, level)
                        dbLabel.text = "${level / 100} dB"
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })

            val freqLabel = TextView(activity).apply {
                val hz = eq.bandFreqHz(band)
                text = if (hz >= 1000) "${hz / 1000}k" else "$hz"
                setTextColor(p.inkDim)
                textSize = 10.5f
                gravity = Gravity.CENTER
            }

            col.addView(dbLabel)
            col.addView(seekWrap)
            col.addView(freqLabel)
            bandsRow.addView(col)
        }
        content.addView(bandsScroll)
        content.addView(spacer(activity, 28))

        // ---- reforço de graves ----
        if (eq.bassBoost != null) {
            content.addView(labelText(activity, "reforço de graves", p))
            val bassSeek = SeekBar(activity).apply {
                max = 1000
                progress = eq.currentBassStrength().toInt()
            }
            bassSeek.progressDrawable?.setTint(p.accent)
            bassSeek.thumb?.setTint(p.accent)
            bassSeek.setOnSeekBarChangeListener(simpleSeekListener { eq.setBassBoost(it.toShort()) })
            content.addView(bassSeek)
            content.addView(spacer(activity, 18))
        }

        // ---- efeito surround ----
        if (eq.virtualizer != null) {
            content.addView(labelText(activity, "efeito surround", p))
            val virtSeek = SeekBar(activity).apply {
                max = 1000
                progress = eq.currentVirtStrength().toInt()
            }
            virtSeek.progressDrawable?.setTint(p.accent)
            virtSeek.thumb?.setTint(p.accent)
            virtSeek.setOnSeekBarChangeListener(simpleSeekListener { eq.setVirtualizer(it.toShort()) })
            content.addView(virtSeek)
            content.addView(spacer(activity, 24))
        }

        // ---- realce de volume (loudness) ----
        if (eq.loudnessEnhancer != null) {
            val initialLoudDb = eq.currentLoudnessDb()
            val loudLabel = labelText(activity, "realce de volume (${"%.1f".format(initialLoudDb)} dB)", p)
            content.addView(loudLabel)
            val loudSeek = SeekBar(activity).apply {
                max = 200 // 0 a 20 dB, passo de 0.1
                progress = (initialLoudDb * 10).toInt()
            }
            loudSeek.progressDrawable?.setTint(p.accent)
            loudSeek.thumb?.setTint(p.accent)
            loudSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val db = progress / 10f
                        eq.setLoudnessGainDb(db)
                        loudLabel.text = "realce de volume (${"%.1f".format(db)} dB)"
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
            content.addView(loudSeek)
            content.addView(spacer(activity, 18))
        }

        // ---- balanço estéreo ----
        if (onBalanceChange != null) {
            val initialLabel = when {
                initialPan < -0.05f -> "balanço (${(-initialPan * 100).toInt()}% esquerda)"
                initialPan > 0.05f -> "balanço (${(initialPan * 100).toInt()}% direita)"
                else -> "balanço (centro)"
            }
            val balanceLabel = labelText(activity, initialLabel, p)
            content.addView(balanceLabel)
            val balanceSeek = SeekBar(activity).apply {
                max = 200 // 0 = tudo esquerda, 100 = centro, 200 = tudo direita
                progress = (initialPan * 100 + 100).toInt().coerceIn(0, 200)
            }
            balanceSeek.progressDrawable?.setTint(p.accent)
            balanceSeek.thumb?.setTint(p.accent)
            balanceSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val pan = (progress - 100) / 100f // -1..1
                        currentPan = pan
                        val left = if (pan > 0) 1f - pan else 1f
                        val right = if (pan < 0) 1f + pan else 1f
                        onBalanceChange(left, right)
                        val label = when {
                            pan < -0.05f -> "balanço (${(-pan * 100).toInt()}% esquerda)"
                            pan > 0.05f -> "balanço (${(pan * 100).toInt()}% direita)"
                            else -> "balanço (centro)"
                        }
                        balanceLabel.text = label
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
            content.addView(balanceSeek)
            content.addView(spacer(activity, 18))
        }

        // ---- limitador anti-distorção ----
        if (eq.limiterSupported) {
            val limiterRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val limiterLabel = labelText(activity, "limitador anti-distorção", p)
            val limiterSwitch = Switch(activity).apply {
                isChecked = eq.isLimiterEnabled()
                setOnCheckedChangeListener { _, isChecked -> eq.setLimiterEnabled(isChecked) }
            }
            limiterRow.addView(limiterLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            limiterRow.addView(limiterSwitch)
            content.addView(limiterRow)
            content.addView(spacer(activity, 20))
        }

        // ---- botões ----
        val btnRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val resetBtn = TextView(activity).apply {
            text = "RESETAR"
            setTextColor(p.inkDim)
            textSize = 12.5f
            gravity = Gravity.CENTER
            setPadding(dp(activity, 10), dp(activity, 12), dp(activity, 10), dp(activity, 12))
            setOnClickListener {
                eq.reset()
                dialog.dismiss()
                onBalanceChange?.invoke(1f, 1f)
                show(activity, eq, p, 0f, onBalanceChange)
            }
        }
        val saveBtn = TextView(activity).apply {
            text = "SALVAR"
            setTextColor(p.accent)
            textSize = 12.5f
            gravity = Gravity.CENTER
            background = ThemeManager.glossyDrawable(p.card, dp(activity, 6).toFloat(), p.accentDim, dp(activity, 1))
            setPadding(dp(activity, 10), dp(activity, 12), dp(activity, 10), dp(activity, 12))
            setOnClickListener {
                eq.saveCurrentSettings(currentPan)
                android.widget.Toast.makeText(activity, "Equalização salva ✓", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        val closeBtn = TextView(activity).apply {
            text = "FECHAR"
            setTextColor(p.onAccent)
            background = ThemeManager.glossyDrawable(p.accent, dp(activity, 6).toFloat())
            textSize = 12.5f
            gravity = Gravity.CENTER
            setPadding(dp(activity, 10), dp(activity, 12), dp(activity, 10), dp(activity, 12))
            setOnClickListener { dialog.dismiss() }
        }
        btnRow.addView(resetBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val saveLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        saveLp.marginStart = dp(activity, 8)
        btnRow.addView(saveBtn, saveLp)
        val closeLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        closeLp.marginStart = dp(activity, 8)
        btnRow.addView(closeBtn, closeLp)
        content.addView(btnRow)
        content.addView(spacer(activity, 10))
        val hint = if (eq.hasSavedSettings())
            "sua equalização salva é aplicada automaticamente nas próximas músicas"
        else
            "toque em SALVAR pra essa equalização valer nas próximas músicas também"
        content.addView(TextView(activity).apply {
            text = hint
            setTextColor(p.inkDim)
            textSize = 10f
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
        })

        dialog.setContentView(root)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setGravity(Gravity.BOTTOM)
        dialog.show()
    }

    private fun simpleSeekListener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) onChange(progress)
        }
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) {}
    }

    private fun titleText(ctx: android.content.Context, text: String, p: Palette) = TextView(ctx).apply {
        this.text = text
        setTextColor(p.paper)
        textSize = 18f
        typeface = android.graphics.Typeface.MONOSPACE
        setPadding(0, 0, 0, dp(ctx, 4))
    }

    private fun labelText(ctx: android.content.Context, text: String, p: Palette) = TextView(ctx).apply {
        this.text = text
        setTextColor(p.inkDim)
        textSize = 11f
        typeface = android.graphics.Typeface.MONOSPACE
        setPadding(0, 0, 0, dp(ctx, 6))
    }

    private fun spacer(ctx: android.content.Context, heightDp: Int) = android.view.View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, heightDp))
    }

    private fun chipBackground(ctx: android.content.Context, p: Palette): android.graphics.drawable.Drawable {
        val shape = android.graphics.drawable.GradientDrawable()
        shape.cornerRadius = dp(ctx, 16).toFloat()
        shape.setColor(p.card)
        shape.setStroke(dp(ctx, 1), p.accentDim)
        return shape
    }

    private fun dp(ctx: android.content.Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density).toInt()
}
