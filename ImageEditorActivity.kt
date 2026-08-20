package com.abridor.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageEditorActivity : AppCompatActivity() {

    private lateinit var editorView: ImageEditorView
    private lateinit var panelContainer: FrameLayout
    private lateinit var palette: Palette
    private lateinit var sourceFile: File

    // snapshot usado só durante o painel de ajustes, pra permitir preview ao vivo sem empilhar undo a cada tick
    private var adjustBaseBitmap: Bitmap? = null

    private val brushColors: IntArray get() = intArrayOf(
        palette.accent, Color.RED, Color.YELLOW, Color.GREEN,
        Color.CYAN, Color.BLUE, Color.MAGENTA, Color.WHITE, Color.BLACK
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_editor)
        palette = ThemeManager.current(this)

        val path = intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return }
        sourceFile = File(path)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.title = sourceFile.name
        editorView = findViewById(R.id.editorView)
        panelContainer = findViewById(R.id.panelContainer)

        applyTheme(toolbar)

        val bmp = ImageEditUtils.loadDownscaled(sourceFile)
        if (bmp == null) {
            Toast.makeText(this, "Não foi possível abrir esta imagem.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        editorView.setBitmap(bmp, pushToUndo = false)
        editorView.brushColor = palette.accent
        editorView.themeAccent = palette.accent

        setupToolRow()
    }

    private fun applyTheme(toolbar: Toolbar) {
        findViewById<View>(R.id.rootLayout).setBackgroundColor(palette.bg)
        toolbar.setBackgroundColor(palette.bg)
        toolbar.setTitleTextColor(palette.paper)
        (findViewById<View>(R.id.toolRow).parent as View).setBackgroundColor(palette.panel)
        listOf(R.id.toolCrop, R.id.toolRotate, R.id.toolFlip, R.id.toolFilters, R.id.toolAdjust, R.id.toolDraw, R.id.toolText, R.id.toolUndo)
            .forEach { findViewById<TextView>(it).setTextColor(palette.accent) }
    }

    private fun setupToolRow() {
        findViewById<View>(R.id.toolCrop).setOnClickListener { startCrop() }
        findViewById<View>(R.id.toolRotate).setOnClickListener {
            editorView.currentBitmap()?.let { editorView.setBitmap(ImageEditUtils.rotate90(it, true), pushToUndo = true) }
        }
        findViewById<View>(R.id.toolFlip).setOnClickListener {
            editorView.currentBitmap()?.let { editorView.setBitmap(ImageEditUtils.flip(it, true), pushToUndo = true) }
        }
        findViewById<View>(R.id.toolFilters).setOnClickListener { showFiltersPanel() }
        findViewById<View>(R.id.toolAdjust).setOnClickListener { showAdjustPanel() }
        findViewById<View>(R.id.toolDraw).setOnClickListener { showDrawPanel() }
        findViewById<View>(R.id.toolText).setOnClickListener { showTextDialog() }
        findViewById<View>(R.id.toolUndo).setOnClickListener {
            if (!editorView.undo()) Toast.makeText(this, "Nada para desfazer", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1, 0, "SALVAR")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu?.add(0, 2, 1, "COMPARTILHAR")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> saveAsNewFile()
            2 -> shareCurrentImage()
        }
        return true
    }

    // ---------- corte ----------

    private fun startCrop() {
        clearPanel()
        editorView.mode = ImageEditorView.Mode.CROP
        showActionPanel(
            title = "Ajuste as bordas e confirme",
            confirmLabel = "APLICAR CORTE",
            onConfirm = {
                if (!editorView.applyCrop()) Toast.makeText(this, "Área de corte inválida", Toast.LENGTH_SHORT).show()
                clearPanel()
            },
            onCancel = {
                editorView.cancelCrop()
                clearPanel()
            }
        )
    }

    // ---------- filtros ----------

    private fun showFiltersPanel() {
        editorView.mode = ImageEditorView.Mode.NONE
        clearPanel()
        val base = editorView.currentBitmap() ?: return
        val baseSnapshot = base.copy(Bitmap.Config.ARGB_8888, true)

        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        scroll.addView(row)

        ImageEditUtils.filterPresets().forEach { (name, matrix) ->
            val label = TextView(this).apply {
                text = name
                setTextColor(palette.paper)
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = pillBackground()
                setOnClickListener {
                    val filtered = ImageEditUtils.applyColorMatrix(baseSnapshot, matrix)
                    editorView.setBitmap(filtered, pushToUndo = true)
                    clearPanel()
                }
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = dp(8)
            row.addView(label, lp)
        }
        panelContainer.addView(scroll)
        panelContainer.setBackgroundColor(palette.panel)
    }

    // ---------- ajustes (brilho, contraste, saturação, temperatura) ----------

    private fun showAdjustPanel() {
        editorView.mode = ImageEditorView.Mode.NONE
        clearPanel()
        val base = editorView.currentBitmap() ?: return
        adjustBaseBitmap = base.copy(Bitmap.Config.ARGB_8888, true)

        var brightness = 0f
        var contrast = 0f
        var saturation = 100f
        var warmth = 0f

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(6))
        }

        fun livePreview() {
            val snap = adjustBaseBitmap ?: return
            val matrix = ImageEditUtils.buildAdjustMatrix(brightness, contrast, saturation, warmth)
            val preview = ImageEditUtils.applyColorMatrix(snap, matrix)
            editorView.setBitmap(preview, pushToUndo = false)
        }

        fun addSlider(label: String, sliderMin: Int, sliderMax: Int, sliderStart: Int, onChange: (Int) -> Unit) {
            content.addView(sliderLabel(label))
            val seek = SeekBar(this).apply {
                max = sliderMax - sliderMin
                progress = sliderStart - sliderMin
            }
            seek.progressDrawable?.setTint(palette.accent)
            seek.thumb?.setTint(palette.accent)
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) { onChange(progress + sliderMin); livePreview() }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
            content.addView(seek)
        }

        addSlider("brilho", -100, 100, 0) { brightness = it.toFloat() }
        addSlider("contraste", -100, 100, 0) { contrast = it.toFloat() }
        addSlider("saturação", 0, 200, 100) { saturation = it.toFloat() }
        addSlider("temperatura", -100, 100, 0) { warmth = it.toFloat() }

        panelContainer.addView(content)
        panelContainer.setBackgroundColor(palette.panel)

        addConfirmCancelRow(
            onConfirm = {
                val snap = adjustBaseBitmap
                if (snap != null) {
                    val finalBmp = editorView.currentBitmap()
                    editorView.setBitmap(snap, pushToUndo = false) // restaura base sem contar como undo extra
                    if (finalBmp != null) editorView.setBitmap(finalBmp, pushToUndo = true)
                }
                clearPanel()
            },
            onCancel = {
                adjustBaseBitmap?.let { editorView.setBitmap(it, pushToUndo = false) }
                clearPanel()
            }
        )
    }

    // ---------- desenho ----------

    private fun showDrawPanel() {
        clearPanel()
        editorView.mode = ImageEditorView.Mode.DRAW

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(10))
        }
        content.addView(sliderLabel("espessura do traço"))
        val sizeSeek = SeekBar(this).apply { max = 60; progress = 14 }
        sizeSeek.progressDrawable?.setTint(palette.accent)
        sizeSeek.thumb?.setTint(palette.accent)
        sizeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                editorView.brushWidth = (progress + 4).toFloat()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        content.addView(sizeSeek)
        content.addView(spacer(8))

        val colorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        brushColors.forEach { c ->
            val dot = View(this).apply {
                val size = dp(34)
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(10) }
                background = circleDrawable(c)
                setOnClickListener { editorView.brushColor = c }
            }
            colorRow.addView(dot)
        }
        content.addView(colorRow)
        panelContainer.addView(content)
        panelContainer.setBackgroundColor(palette.panel)

        addConfirmCancelRow(
            onConfirm = { editorView.mode = ImageEditorView.Mode.NONE; clearPanel() },
            onCancel = { editorView.mode = ImageEditorView.Mode.NONE; clearPanel() },
            confirmText = "PRONTO",
            hideCancel = true
        )
    }

    // ---------- texto ----------

    private fun showTextDialog() {
        editorView.mode = ImageEditorView.Mode.NONE
        val input = EditText(this).apply { hint = "digite o texto" }
        AlertDialog.Builder(this)
            .setTitle("Adicionar texto")
            .setView(input)
            .setPositiveButton("Adicionar") { _, _ ->
                val text = input.text.toString()
                if (text.isNotBlank()) {
                    editorView.addText(text, palette.accent, 42f)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------- salvar / compartilhar ----------

    private fun saveAsNewFile() {
        val bmp = editorView.currentBitmap() ?: return
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val baseName = sourceFile.nameWithoutExtension
        val outFile = File(sourceFile.parentFile ?: Environment.getExternalStorageDirectory(), "${baseName}_editado_$stamp.jpg")
        try {
            FileOutputStream(outFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            Toast.makeText(this, "Salvo como ${outFile.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível salvar", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareCurrentImage() {
        val bmp = editorView.currentBitmap() ?: return
        try {
            val tmpFile = File(cacheDir, "compartilhar_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tmpFile).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 92, out) }
            FileOpener.share(this, tmpFile)
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível compartilhar", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- helpers de UI ----------

    private fun clearPanel() {
        panelContainer.removeAllViews()
        panelContainer.background = null
    }

    private fun showActionPanel(title: String, confirmLabel: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(10))
        }
        content.addView(TextView(this).apply {
            text = title
            setTextColor(palette.inkDim)
            textSize = 12f
        })
        content.addView(spacer(10))
        panelContainer.addView(content)
        panelContainer.setBackgroundColor(palette.panel)
        addConfirmCancelRow(onConfirm, onCancel, confirmLabel)
    }

    private fun addConfirmCancelRow(onConfirm: () -> Unit, onCancel: () -> Unit, confirmText: String = "CONFIRMAR", hideCancel: Boolean = false) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(18), dp(0), dp(18), dp(14))
        }
        if (!hideCancel) {
            val cancelBtn = TextView(this).apply {
                text = "CANCELAR"
                setTextColor(palette.inkDim)
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(12))
                setOnClickListener { onCancel() }
            }
            row.addView(cancelBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        }
        val confirmBtn = TextView(this).apply {
            text = confirmText
            setTextColor(palette.onAccent)
            background = ThemeManager.glossyDrawable(palette.accent, dp(6).toFloat())
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener { onConfirm() }
        }
        row.addView(confirmBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        (panelContainer.getChildAt(0) as? LinearLayout)?.addView(row)
    }

    private fun sliderLabel(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(palette.inkDim)
        textSize = 11f
        typeface = android.graphics.Typeface.MONOSPACE
        setPadding(0, dp(6), 0, dp(4))
    }

    private fun spacer(heightDp: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp))
    }

    private fun pillBackground(): android.graphics.drawable.Drawable {
        val shape = android.graphics.drawable.GradientDrawable()
        shape.cornerRadius = dp(16).toFloat()
        shape.setColor(palette.card)
        shape.setStroke(dp(1), palette.accentDim)
        return shape
    }

    private fun circleDrawable(color: Int): android.graphics.drawable.Drawable {
        val shape = android.graphics.drawable.GradientDrawable()
        shape.shape = android.graphics.drawable.GradientDrawable.OVAL
        shape.setColor(color)
        shape.setStroke(dp(2), palette.line)
        return shape
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PATH = "extra_path"

        fun open(context: Context, file: File) {
            val intent = Intent(context, ImageEditorActivity::class.java)
            intent.putExtra(EXTRA_PATH, file.absolutePath)
            context.startActivity(intent)
        }
    }
}
